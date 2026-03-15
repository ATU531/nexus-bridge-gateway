package com.nexus.bridgegateway.service;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import com.nexus.bridgegateway.exception.GatewayRpcException;
import com.nexus.bridgegateway.model.BalanceResponse;
import com.nexus.bridgegateway.model.BlockResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.exceptions.MessageDecodingException;
import org.web3j.utils.Convert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.web3j.protocol.core.Response;

@Service
public class Web3QueryService {
    private static final Logger logger = LoggerFactory.getLogger(Web3QueryService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final Web3jRegistry web3jRegistry;
    // 使用 Java 21 虚拟线程池
    private final Scheduler virtualThreadScheduler;

    public Web3QueryService(Web3jRegistry web3jRegistry) {
        this.web3jRegistry = web3jRegistry;
        this.virtualThreadScheduler = Schedulers.fromExecutor(
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    /**
     * 获取指定链上地址的原生代币余额
     *
     * @param chain   链标识 (eth, bsc, polygon)
     * @param address 钱包地址
     * @return Mono<BalanceResponse> 余额响应
     */
    public Mono<BalanceResponse> getNativeBalance(String chain, String address) {
        logger.info("[多链查询] 查询 {} 链上地址 {} 的余额", chain, address);

        // 从注册中心获取对应链的 Web3j 客户端和代币符号
        Web3j web3j = web3jRegistry.getClient(chain);
        String symbol = web3jRegistry.getSymbol(chain);

        return Mono.fromCallable(() -> {
                    logger.debug("[多链查询] 发送 RPC 请求到 {} 链查询余额", chain);
                    // 使用虚拟线程执行阻塞的 RPC 调用
                    EthGetBalance ethGetBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();

                    if (ethGetBalance.hasError()) {
                        Response.Error error = ethGetBalance.getError();
                        logger.error("[多链查询] {} 链 RPC 错误 {}: {}", chain, error.getCode(), error.getMessage());
                        throw new GatewayRpcException("上游 RPC 节点返回错误: " + error.getMessage(), error.getCode());
                    }

                    BigInteger balanceWei = ethGetBalance.getBalance();
                    logger.debug("[多链查询] {} 链收到余额 (Wei): {}", chain, balanceWei);

                    // 转换为可读的代币单位
                    BigDecimal balance = Convert.fromWei(new BigDecimal(balanceWei), Convert.Unit.ETHER);
                    logger.debug("[多链查询] {} 链余额转换后: {} {}", chain, balance, symbol);

                    return balance.toPlainString();
                })
                // 关键：在虚拟线程上执行阻塞操作，绝不阻塞 WebFlux 主线程
                .subscribeOn(virtualThreadScheduler)
                .map(balance -> {
                    logger.info("[多链查询] 成功获取 {} 链地址 {} 余额: {} {}", chain, address, balance, symbol);
                    return new BalanceResponse(address, chain, symbol, balance);
                })
                .retryWhen(reactor.util.retry.Retry.backoff(MAX_RETRIES, Duration.ofMillis(RETRY_DELAY_MS))
                        .filter(throwable -> {
                            if (throwable.getCause() instanceof MessageDecodingException) {
                                logger.warn("[多链查询] {} 链 RPC 节点返回内部错误，重试中...", chain);
                                return true;
                            }
                            return true;
                        })
                        .doBeforeRetry(retrySignal ->
                                logger.warn("[多链查询] {} 链重试 {}/{}",
                                        chain, retrySignal.totalRetries() + 1, MAX_RETRIES)
                        ))
                .onErrorMap(throwable -> {
                    logger.error("[多链查询] 查询 {} 链地址 {} 余额失败: {}",
                            chain, address, throwable.getMessage(), throwable);
                    return new RuntimeException("查询 " + chain + " 链余额失败: " + throwable.getMessage(), throwable);
                });
    }

    /**
     * 获取指定链的最新区块号
     *
     * @param chain 链标识 (eth, bsc, polygon)
     * @return Mono<BlockResponse> 区块响应
     */
    public Mono<BlockResponse> getLatestBlockNumber(String chain) {
        logger.info("[多链查询] 查询 {} 链最新区块号", chain);

        // 从注册中心获取对应链的 Web3j 客户端
        Web3j web3j = web3jRegistry.getClient(chain);

        return Mono.fromCallable(() -> {
                    logger.debug("[多链查询] 发送 RPC 请求到 {} 链查询最新区块", chain);
                    // 使用虚拟线程执行阻塞的 RPC 调用
                    EthBlockNumber ethBlockNumber = web3j.ethBlockNumber().send();
                    return ethBlockNumber.getBlockNumber().longValue();
                })
                // 关键：在虚拟线程上执行阻塞操作，绝不阻塞 WebFlux 主线程
                .subscribeOn(virtualThreadScheduler)
                .map(blockNumber -> {
                    logger.info("[多链查询] 成功获取 {} 链最新区块号: {}", chain, blockNumber);
                    return new BlockResponse(blockNumber);
                })
                .retryWhen(reactor.util.retry.Retry.backoff(MAX_RETRIES, Duration.ofMillis(RETRY_DELAY_MS))
                        .filter(throwable -> {
                            if (throwable.getCause() instanceof MessageDecodingException) {
                                logger.warn("[多链查询] {} 链 RPC 节点返回内部错误，重试中...", chain);
                                return true;
                            }
                            return true;
                        })
                        .doBeforeRetry(retrySignal ->
                                logger.warn("[多链查询] {} 链重试 {}/{}",
                                        chain, retrySignal.totalRetries() + 1, MAX_RETRIES)
                        ))
                .onErrorMap(throwable -> {
                    logger.error("[多链查询] 查询 {} 链最新区块号失败: {}",
                            chain, throwable.getMessage(), throwable);
                    return new RuntimeException("查询 " + chain + " 链区块号失败: " + throwable.getMessage(), throwable);
                });
    }
}
