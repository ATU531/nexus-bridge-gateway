package com.nexus.bridgegateway.core.query;

import org.springframework.stereotype.Component;
import org.web3j.protocol.core.DefaultBlockParameterName;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigInteger;

/**
 * 核心查询引擎
 * 聚合多链 RPC，提供标准化的只读接口
 */
@Component
public class QueryEngine {

    private final Web3jRegistry web3jRegistry;

    public QueryEngine(Web3jRegistry web3jRegistry) {
        this.web3jRegistry = web3jRegistry;
    }

    /**
     * 查询指定链上地址的余额
     *
     * @param chain   链标识
     * @param address 钱包地址
     * @return 余额
     */
    public Mono<BigInteger> getBalance(String chain, String address) {
        return Mono.fromCallable(() -> {
            // 从多链注册中心获取对应链的 Web3j 客户端
            var web3j = web3jRegistry.getClient(chain);
            return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询指定链的最新区块号
     *
     * @param chain 链标识
     * @return 区块号
     */
    public Mono<BigInteger> getLatestBlockNumber(String chain) {
        return Mono.fromCallable(() -> {
            // 从多链注册中心获取对应链的 Web3j 客户端
            var web3j = web3jRegistry.getClient(chain);
            return web3j.ethBlockNumber()
                    .send()
                    .getBlockNumber();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
