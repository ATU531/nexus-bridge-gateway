package com.nexus.bridgegateway.core.transaction;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import com.nexus.bridgegateway.core.tx.ReactiveNonceManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigInteger;

/**
 * 交易编排中心
 * 处理私钥托管、Nonce 管理、Gas 优化
 */
@Service
public class TransactionOrchestrator {

    private final Web3jRegistry web3jRegistry;
    private final ReactiveNonceManager nonceManager;
    private final GasStrategy gasStrategy;

    public TransactionOrchestrator(Web3jRegistry web3jRegistry, ReactiveNonceManager nonceManager, GasStrategy gasStrategy) {
        this.web3jRegistry = web3jRegistry;
        this.nonceManager = nonceManager;
        this.gasStrategy = gasStrategy;
    }

    /**
     * 构建并发送交易（托管模式）
     *
     * @param chain  链标识
     * @param from   发送地址
     * @param to     接收地址
     * @param value  转账金额
     * @param data   交易数据
     * @return 交易哈希
     */
    public Mono<String> sendTransactionWithCustody(String chain, String from, String to, BigInteger value, String data) {
        // 获取 Nonce
        return nonceManager.allocateNonce(chain, from)
                .flatMap(nonce ->
                        // 获取 Gas 价格
                        gasStrategy.getGasPrice(chain, GasStrategy.StrategyType.STANDARD)
                                .map(gasPrice -> {
                                    // TODO: 使用托管私钥签名交易
                                    // TODO: 发送交易到链上
                                    return "0x...txHash";
                                })
                );
    }

    /**
     * 构建交易（非托管模式，返回待签名交易）
     *
     * @param chain 链标识
     * @param from  发送地址
     * @param to    接收地址
     * @param value 转账金额
     * @return 未签名交易数据
     */
    public Mono<String> buildUnsignedTransaction(String chain, String from, String to, BigInteger value) {
        return nonceManager.allocateNonce(chain, from)
                .flatMap(nonce ->
                        gasStrategy.getGasPrice(chain, GasStrategy.StrategyType.STANDARD)
                                .map(gasPrice -> {
                                    // TODO: 构建未签名交易
                                    return "unsignedTxData";
                                })
                );
    }

    /**
     * 提交已签名交易
     *
     * @param chain         链标识
     * @param signedTxData  已签名交易数据
     * @return 交易哈希
     */
    public Mono<String> submitSignedTransaction(String chain, String signedTxData) {
        return Mono.fromCallable(() -> {
            // 从多链注册中心获取对应链的 Web3j 客户端
            var web3j = web3jRegistry.getClient(chain);
            // TODO: 发送已签名交易
            return "0x...txHash";
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}
