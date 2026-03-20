package com.nexus.bridgegateway.core.tx;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import com.nexus.bridgegateway.model.enums.GasStrategyEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGasPrice;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;

/**
 * Gas 策略服务
 * 
 * 【架构说明】：
 * 1. 提供四种 Gas 价格计算策略：CONSERVATIVE、STANDARD、AGGRESSIVE、DYNAMIC
 * 2. 所有 Web3j 阻塞调用在 Java 21 虚拟线程中执行
 * 3. DYNAMIC 策略支持 EIP-1559 的 BaseFee + PriorityFee 机制
 * 
 * 【策略对比】：
 * | 策略        | 乘数  | 适用场景           | 打包速度 | 成本 |
 * |------------|-------|-------------------|---------|------|
 * | CONSERVATIVE | 1.0  | 不紧急交易         | 慢      | 低   |
 * | STANDARD     | 1.2  | 大多数场景（默认）  | 中等    | 中等 |
 * | AGGRESSIVE   | 1.5  | 紧急交易           | 快      | 高   |
 * | DYNAMIC      | 智能  | 对时间有要求       | 自适应  | 自适应|
 */
@Slf4j
@Service
public class GasStrategyService {

    private final Web3jRegistry web3jRegistry;

    /**
     * DYNAMIC 策略降级时的默认乘数
     * 当节点不支持 EIP-1559 时使用
     */
    private static final BigDecimal DYNAMIC_FALLBACK_MULTIPLIER = new BigDecimal("1.3");

    public GasStrategyService(Web3jRegistry web3jRegistry) {
        this.web3jRegistry = web3jRegistry;
    }

    /**
     * 计算指定策略下的 Gas Price
     * 
     * 【执行方式】：在虚拟线程中执行，避免阻塞调用线程
     * 
     * @param chain   链标识 (eth, bsc, polygon)
     * @param strategy Gas 策略
     * @return 计算后的 Gas Price (Wei)
     */
    public BigInteger calculateGasPrice(String chain, GasStrategyEnum strategy) {
        log.debug("[GasStrategy] 开始计算 Gas Price, chain={}, strategy={}", chain, strategy);

        // 在虚拟线程中执行阻塞操作（使用 Callable 获取返回值）
        try {
            Future<BigInteger> future = Executors.newVirtualThreadPerTaskExecutor()
                    .submit(() -> doCalculateGasPrice(chain, strategy));
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gas price calculation interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("[GasStrategy] 计算 Gas Price 失败: {}", cause.getMessage());
            throw new RuntimeException("Failed to calculate gas price", cause);
        }
    }

    /**
     * 执行 Gas Price 计算的核心逻辑
     * 
     * @param chain   链标识
     * @param strategy Gas 策略
     * @return 计算后的 Gas Price
     */
    private BigInteger doCalculateGasPrice(String chain, GasStrategyEnum strategy) throws Exception {
        Web3j web3j = web3jRegistry.getClient(chain);

        // 1. 获取网络建议的基础 Gas Price
        EthGasPrice ethGasPrice = web3j.ethGasPrice().send();
        if (ethGasPrice.hasError()) {
            throw new RuntimeException("Failed to get gas price: " + ethGasPrice.getError().getMessage());
        }
        BigInteger baseGasPrice = ethGasPrice.getGasPrice();
        log.debug("[GasStrategy] 基础 Gas Price: {} wei", baseGasPrice);

        // 2. 根据策略计算最终 Gas Price
        BigInteger finalGasPrice;

        if (strategy.isDynamic()) {
            // DYNAMIC 策略：使用 EIP-1559 机制智能计算
            finalGasPrice = calculateDynamicGasPrice(web3j, baseGasPrice);
        } else {
            // 固定乘数策略：基础价格 * 乘数
            finalGasPrice = applyMultiplier(baseGasPrice, strategy.getMultiplier());
        }

        log.info("[GasStrategy] 计算完成, strategy={}, baseGasPrice={} wei, finalGasPrice={} wei",
                strategy, baseGasPrice, finalGasPrice);

        return finalGasPrice;
    }

    /**
     * 应用乘数计算 Gas Price
     * 
     * @param baseGasPrice 基础 Gas Price
     * @param multiplier   乘数
     * @return 计算后的 Gas Price
     */
    private BigInteger applyMultiplier(BigInteger baseGasPrice, BigDecimal multiplier) {
        BigDecimal basePriceDecimal = new BigDecimal(baseGasPrice);
        BigDecimal result = basePriceDecimal.multiply(multiplier);
        // 向上取整，确保 Gas 足够
        return result.setScale(0, RoundingMode.CEILING).toBigInteger();
    }

    /**
     * 计算 DYNAMIC 策略的 Gas Price
     * 
     * 【计算公式】：BaseFee * 2 + PriorityFee
     * 【降级机制】：如果节点不支持 EIP-1559，使用 baseGasPrice * 1.3
     * 
     * @param web3j        Web3j 客户端
     * @param baseGasPrice 基础 Gas Price（降级时使用）
     * @return 计算后的 Gas Price
     */
    private BigInteger calculateDynamicGasPrice(Web3j web3j, BigInteger baseGasPrice) {
        try {
            log.debug("[GasStrategy] [DYNAMIC] 尝试获取 EIP-1559 参数...");

            // 1. 获取最新区块的 BaseFeePerGas
            EthBlock.Block latestBlock = web3j.ethGetBlockByNumber(
                    DefaultBlockParameterName.LATEST, false).send().getBlock();

            if (latestBlock == null) {
                log.warn("[GasStrategy] [DYNAMIC] 无法获取最新区块，使用降级策略");
                return applyMultiplier(baseGasPrice, DYNAMIC_FALLBACK_MULTIPLIER);
            }

            // 2. 尝试获取 BaseFeePerGas（EIP-1559 字段）
            BigInteger baseFeePerGas = latestBlock.getBaseFeePerGas();
            if (baseFeePerGas == null || baseFeePerGas.equals(BigInteger.ZERO)) {
                log.warn("[GasStrategy] [DYNAMIC] 节点不支持 EIP-1559 (无 BaseFeePerGas)，使用降级策略");
                return applyMultiplier(baseGasPrice, DYNAMIC_FALLBACK_MULTIPLIER);
            }

            log.debug("[GasStrategy] [DYNAMIC] BaseFeePerGas: {} wei", baseFeePerGas);

            // 3. 尝试获取建议的 PriorityFee（MaxPriorityFeePerGas）
            BigInteger priorityFee;
            try {
                priorityFee = web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas();
                if (priorityFee == null) {
                    priorityFee = BigInteger.ZERO;
                }
            } catch (Exception e) {
                // 节点不支持 eth_maxPriorityFeePerGas，使用经验值
                // 经验值：BaseFee 的 10% 作为 PriorityFee
                priorityFee = baseFeePerGas.divide(BigInteger.TEN);
                log.debug("[GasStrategy] [DYNAMIC] 节点不支持 MaxPriorityFeePerGas，使用经验值: {} wei", priorityFee);
            }

            log.debug("[GasStrategy] [DYNAMIC] PriorityFee: {} wei", priorityFee);

            // 4. 计算最终 Gas Price：BaseFee * 2 + PriorityFee
            // 乘以 2 是为了应对 BaseFee 在下一个区块可能上涨 12.5% 的情况
            // 这样可以确保交易在连续两个区块内都能被打包
            BigInteger dynamicGasPrice = baseFeePerGas.multiply(BigInteger.valueOf(2)).add(priorityFee);

            log.info("[GasStrategy] [DYNAMIC] 计算完成: BaseFee={} wei, PriorityFee={} wei, Final={} wei",
                    baseFeePerGas, priorityFee, dynamicGasPrice);

            return dynamicGasPrice;

        } catch (Exception e) {
            log.warn("[GasStrategy] [DYNAMIC] EIP-1559 查询失败: {}，使用降级策略", e.getMessage());
            return applyMultiplier(baseGasPrice, DYNAMIC_FALLBACK_MULTIPLIER);
        }
    }
}
