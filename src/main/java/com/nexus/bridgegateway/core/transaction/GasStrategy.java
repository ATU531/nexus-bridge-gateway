package com.nexus.bridgegateway.core.transaction;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigInteger;

/**
 * Gas 策略
 * 动态 Gas Price 计算，支持 EIP-1559
 */
@Service
public class GasStrategy {

    private final Web3jRegistry web3jRegistry;

    public GasStrategy(Web3jRegistry web3jRegistry) {
        this.web3jRegistry = web3jRegistry;
    }

    /**
     * 获取指定链的 Gas Price
     *
     * @param chain 链标识
     * @param type  策略类型
     * @return Gas Price
     */
    public Mono<BigInteger> getGasPrice(String chain, StrategyType type) {
        return Mono.fromCallable(() -> {
            // 从多链注册中心获取对应链的 Web3j 客户端
            var web3j = web3jRegistry.getClient(chain);
            BigInteger baseGasPrice = web3j.ethGasPrice().send().getGasPrice();

            return switch (type) {
                case CONSERVATIVE -> baseGasPrice;
                case STANDARD -> baseGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
                case AGGRESSIVE -> baseGasPrice.multiply(BigInteger.valueOf(15)).divide(BigInteger.TEN);
            };
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 策略类型
     */
    public enum StrategyType {
        /**
         * 保守型：Gas Price = 当前网络均价 × 1.0
         */
        CONSERVATIVE,

        /**
         * 标准型：Gas Price = 当前网络均价 × 1.2
         */
        STANDARD,

        /**
         * 激进型：Gas Price = 当前网络均价 × 1.5
         */
        AGGRESSIVE
    }
}
