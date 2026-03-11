package com.nexus.bridgegateway.core.transaction;

import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigInteger;

/**
 * 交易编排中心
 * 处理私钥托管、Nonce 管理、Gas 优化
 */
@Service
public class TransactionOrchestrator {

    private final Web3j web3j;
    private final NonceManager nonceManager;
    private final GasStrategy gasStrategy;

    public TransactionOrchestrator(Web3j web3j, NonceManager nonceManager, GasStrategy gasStrategy) {
        this.web3j = web3j;
        this.nonceManager = nonceManager;
        this.gasStrategy = gasStrategy;
    }

    /**
     * 构建并发送交易（托管模式）
     */
    public Mono<String> sendTransactionWithCustody(String from, String to, BigInteger value, String data) {
        // 获取 Nonce
        return nonceManager.getNextNonce(from)
                .flatMap(nonce ->
                        // 获取 Gas 价格
                        gasStrategy.getGasPrice(GasStrategy.StrategyType.STANDARD)
                                .map(gasPrice -> {
                                    // TODO: 使用托管私钥签名交易
                                    // TODO: 发送交易到链上
                                    return "0x...txHash";
                                })
                );
    }

    /**
     * 构建交易（非托管模式，返回待签名交易）
     */
    public Mono<String> buildUnsignedTransaction(String from, String to, BigInteger value) {
        return nonceManager.getNextNonce(from)
                .flatMap(nonce ->
                        gasStrategy.getGasPrice(GasStrategy.StrategyType.STANDARD)
                                .map(gasPrice -> {
                                    // TODO: 构建未签名交易
                                    return "unsignedTxData";
                                })
                );
    }

    /**
     * 提交已签名交易
     */
    public Mono<String> submitSignedTransaction(String signedTx) {
        return Mono.fromCallable(() -> {
            // TODO: 发送已签名交易到链上
            return "0x...txHash";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询交易状态
     */
    public Mono<TransactionStatus> getTransactionStatus(String txHash) {
        return Mono.fromCallable(() -> {
            // TODO: 查询交易收据
            return TransactionStatus.PENDING;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
