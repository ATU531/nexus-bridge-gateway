package com.nexus.bridgegateway.core.transaction;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.DefaultBlockParameterName;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigInteger;

/**
 * Nonce 管理器
 * 高并发场景下的 Nonce 分配与冲突解决
 */
@Service
public class NonceManager {

    private final Web3jRegistry web3jRegistry;
    private final ReactiveRedisTemplate<String, Long> redisTemplate;

    public NonceManager(Web3jRegistry web3jRegistry, ReactiveRedisTemplate<String, Long> redisTemplate) {
        this.web3jRegistry = web3jRegistry;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取下一个可用 Nonce
     *
     * @param address 钱包地址
     * @return Nonce
     */
    public Mono<BigInteger> getNextNonce(String address) {
        String key = "nonce:" + address.toLowerCase();

        return redisTemplate.opsForValue().get(key)
                .flatMap(cachedNonce -> {
                    // 有缓存，直接递增
                    return redisTemplate.opsForValue().increment(key)
                            .map(nextNonce -> BigInteger.valueOf(nextNonce - 1));
                })
                .switchIfEmpty(
                        // 无缓存，从链上获取
                        Mono.fromCallable(() -> {
                            // 默认使用 eth 链获取 nonce，实际应根据业务传入 chain 参数
                            var web3j = web3jRegistry.getClient("eth");
                            BigInteger onChainNonce = web3j.ethGetTransactionCount(
                                    address, DefaultBlockParameterName.LATEST
                            ).send().getTransactionCount();
                            return onChainNonce;
                        })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(onChainNonce ->
                                        redisTemplate.opsForValue().set(key, onChainNonce.longValue())
                                                .thenReturn(onChainNonce)
                                )
                );
    }

    /**
     * 获取指定链的下一个可用 Nonce
     *
     * @param chain   链标识
     * @param address 钱包地址
     * @return Nonce
     */
    public Mono<BigInteger> getNextNonce(String chain, String address) {
        String key = "nonce:" + chain + ":" + address.toLowerCase();

        return redisTemplate.opsForValue().get(key)
                .flatMap(cachedNonce -> {
                    return redisTemplate.opsForValue().increment(key)
                            .map(nextNonce -> BigInteger.valueOf(nextNonce - 1));
                })
                .switchIfEmpty(
                        Mono.fromCallable(() -> {
                            var web3j = web3jRegistry.getClient(chain);
                            BigInteger onChainNonce = web3j.ethGetTransactionCount(
                                    address, DefaultBlockParameterName.LATEST
                            ).send().getTransactionCount();
                            return onChainNonce;
                        })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(onChainNonce ->
                                        redisTemplate.opsForValue().set(key, onChainNonce.longValue())
                                                .thenReturn(onChainNonce)
                                )
                );
    }

    /**
     * 重置 Nonce（在交易失败时使用）
     *
     * @param address 钱包地址
     * @return 是否成功
     */
    public Mono<Boolean> resetNonce(String address) {
        String key = "nonce:" + address.toLowerCase();
        return redisTemplate.delete(key).map(deleted -> deleted > 0);
    }

    /**
     * 重置指定链的 Nonce
     *
     * @param chain   链标识
     * @param address 钱包地址
     * @return 是否成功
     */
    public Mono<Boolean> resetNonce(String chain, String address) {
        String key = "nonce:" + chain + ":" + address.toLowerCase();
        return redisTemplate.delete(key).map(deleted -> deleted > 0);
    }
}
