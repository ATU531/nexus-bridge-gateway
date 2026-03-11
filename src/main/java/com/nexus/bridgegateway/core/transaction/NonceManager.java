package com.nexus.bridgegateway.core.transaction;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
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

    private final Web3j web3j;
    private final ReactiveRedisTemplate<String, Long> redisTemplate;

    public NonceManager(Web3j web3j, ReactiveRedisTemplate<String, Long> redisTemplate) {
        this.web3j = web3j;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取下一个可用 Nonce
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
     */
    public Mono<Boolean> resetNonce(String address) {
        String key = "nonce:" + address.toLowerCase();
        return redisTemplate.delete(key).map(deleted -> deleted > 0);
    }
}
