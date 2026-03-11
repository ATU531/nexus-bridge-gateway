package com.nexus.bridgegateway.core.query;

import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
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

    private final Web3j web3j;

    public QueryEngine(Web3j web3j) {
        this.web3j = web3j;
    }

    /**
     * 查询地址余额
     */
    public Mono<BigInteger> getBalance(String address) {
        return Mono.fromCallable(() ->
                web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                        .send()
                        .getBalance()
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询最新区块号
     */
    public Mono<BigInteger> getLatestBlockNumber() {
        return Mono.fromCallable(() ->
                web3j.ethBlockNumber()
                        .send()
                        .getBlockNumber()
        ).subscribeOn(Schedulers.boundedElastic());
    }
}
