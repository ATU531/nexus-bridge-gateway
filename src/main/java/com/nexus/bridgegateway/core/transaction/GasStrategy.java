package com.nexus.bridgegateway.core.transaction;

import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigInteger;

/**
 * Gas 策略
 * 动态 Gas Price 计算，支持 EIP-1559
 */
@Service
public class GasStrategy {

    private final Web3j web3j;

    public GasStrategy(Web3j web3j) {
        this.web3j = web3j;
    }

    /**
     * 获取 Gas Price
     */
    public Mono<BigInteger> getGasPrice(StrategyType type) {
        return Mono.fromCallable(() -> {
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
