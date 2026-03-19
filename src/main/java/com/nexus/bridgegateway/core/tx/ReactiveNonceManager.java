package com.nexus.bridgegateway.core.tx;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * 响应式 Nonce 管理器
 * 
 * 【架构说明】：
 * 1. 采用 Redis + Lua 脚本实现原子化 Nonce 分配
 * 2. 使用 WebFlux 响应式编程，保证高并发下的非阻塞特性
 * 3. 阻塞型 RPC 调用（Web3j）使用 Java 21 虚拟线程执行
 * 
 * 【原子性保证】：
 * - Redis Lua 脚本在服务器端原子执行，不会被其他命令打断
 * - 即使多个请求同时到达，也能保证 Nonce 不重复、不遗漏
 * 
 * 【执行链路】：
 * 1. 首次调用 Lua 脚本（ARGV[1] 为空）
 * 2. 如果返回 >= 0，直接返回该 Nonce
 * 3. 如果返回 -1，说明 Redis 无缓存，需要去链上查询基准值
 * 4. 使用虚拟线程执行阻塞的 Web3j RPC 调用
 * 5. 拿到链上 Nonce 后，第二次调用 Lua 脚本（带基准值）
 * 6. 返回分配的 Nonce
 */
@Slf4j
@Component
public class ReactiveNonceManager {

    private static final String NONCE_KEY_PREFIX = "nonce:";
    private static final long NONCE_CACHE_TTL_SECONDS = 3600; // 1 小时

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Web3jRegistry web3jRegistry;

    /**
     * Lua 脚本实例
     * 在类初始化时加载，避免每次调用都读取文件
     */
    private RedisScript<Long> nonceAllocateScript;

    /**
     * Java 21 虚拟线程调度器
     * 用于执行阻塞的 Web3j RPC 调用
     */
    private final Scheduler virtualThreadScheduler;

    public ReactiveNonceManager(
            ReactiveStringRedisTemplate redisTemplate,
            Web3jRegistry web3jRegistry) {
        this.redisTemplate = redisTemplate;
        this.web3jRegistry = web3jRegistry;
        this.virtualThreadScheduler = Schedulers.fromExecutor(
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    /**
     * 初始化：加载 Lua 脚本
     */
    @PostConstruct
    public void init() {
        try {
            ClassPathResource scriptResource = new ClassPathResource("lua/nonce_allocate.lua");
            String scriptContent = new String(
                    scriptResource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            this.nonceAllocateScript = RedisScript.of(scriptContent, Long.class);
            log.info("[NonceManager] Lua 脚本加载成功");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script", e);
        }
    }

    /**
     * 分配 Nonce
     * 
     * 【核心逻辑】：
     * 1. 构造 Redis Key: nonce:{chain}:{address}
     * 2. 首次调用 Lua 脚本，ARGV[1] 为空
     * 3. 根据返回值决定是否需要去链上查询基准值
     * 4. 如果需要，使用虚拟线程执行阻塞 RPC 调用
     * 5. 第二次调用 Lua 脚本，ARGV[1] 为链上查询的基准值
     * 
     * @param chain   链标识 (eth, bsc, polygon)
     * @param address 钱包地址
     * @return Mono<BigInteger> 分配的 Nonce
     */
    public Mono<BigInteger> allocateNonce(String chain, String address) {
        String redisKey = buildNonceKey(chain, address);
        log.debug("[NonceManager] 开始分配 Nonce, chain={}, address={}", chain, address);

        // 第一次调用 Lua 脚本，ARGV[1] 为空
        return executeLuaScript(redisKey, "")
                .flatMap(result -> {
                    if (result >= 0) {
                        // Lua 脚本返回有效 Nonce，直接返回
                        log.info("[NonceManager] 从 Redis 缓存分配 Nonce: {}", result);
                        return Mono.just(BigInteger.valueOf(result));
                    }

                    // 返回 -1，说明 Redis 无缓存，需要去链上查询基准值
                    log.info("[NonceManager] Redis 无缓存，开始从链上查询 Nonce 基准值");
                    return fetchNonceFromChain(chain, address)
                            .flatMap(chainNonce -> {
                                log.info("[NonceManager] 链上查询到 Nonce 基准值: {}", chainNonce);
                                // 第二次调用 Lua 脚本，ARGV[1] 为链上查询的基准值
                                return executeLuaScript(redisKey, chainNonce.toString());
                            })
                            .map(result2 -> {
                                if (result2 >= 0) {
                                    log.info("[NonceManager] 基于链上基准值分配 Nonce: {}", result2);
                                    return BigInteger.valueOf(result2);
                                }
                                // 理论上不会发生，因为提供了基准值
                                throw new RuntimeException("Lua script returned -1 even with fallback nonce");
                            });
                })
                .doOnError(error -> log.error("[NonceManager] 为地址 {} 分配 Nonce 彻底失败. 异常类型: {}, 原因: {}", 
                address, error.getClass().getSimpleName(), error.getMessage()));
    }

    /**
     * 执行 Lua 脚本
     * 
     * @param key     Redis Key
     * @param fallback Fallback Nonce 基准值（空字符串表示不使用）
     * @return Mono<Long> Lua 脚本返回值
     */
    private Mono<Long> executeLuaScript(String key, String fallback) {
        List<String> keys = Collections.singletonList(key);
        return redisTemplate.execute(nonceAllocateScript, keys, fallback)
                .next();
    }

    /**
     * 从链上查询 Nonce 基准值
     * 
     * 【关键】：使用 Java 21 虚拟线程执行阻塞的 Web3j RPC 调用
     * 
     * 为什么必须使用虚拟线程？
     * 1. Web3j.ethGetTransactionCount() 是阻塞调用
     * 2. 如果直接在 WebFlux 线程执行，会阻塞整个事件循环
     * 3. 虚拟线程轻量级，可以创建数百万个，不会耗尽系统资源
     * 4. 阻塞操作在虚拟线程中执行，完成后自动切换回 WebFlux 线程
     * 
     * @param chain   链标识
     * @param address 钱包地址
     * @return Mono<BigInteger> 链上的 Nonce 值
     */
    private Mono<BigInteger> fetchNonceFromChain(String chain, String address) {
        Web3j web3j = web3jRegistry.getClient(chain);

        return Mono.fromCallable(() -> {
                    // 【关键】：此代码块在虚拟线程中执行
                    // 查询 PENDING 状态的 Nonce，包含已提交但未确认的交易
                    log.debug("[NonceManager] 在虚拟线程中执行 Web3j RPC 调用, chain={}, address={}", chain, address);
                    
                    EthGetTransactionCount ethGetTransactionCount = web3j
                            .ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
                            .send();

                    if (ethGetTransactionCount.hasError()) {
                        throw new RuntimeException("RPC Error: " + ethGetTransactionCount.getError().getMessage());
                    }

                    BigInteger nonce = ethGetTransactionCount.getTransactionCount();
                    log.debug("[NonceManager] 链上查询完成, Nonce={}", nonce);
                    return nonce;
                })
                // 【关键】：在虚拟线程上执行阻塞操作，绝不阻塞 WebFlux 主线程
                .subscribeOn(virtualThreadScheduler)
                .doOnError(error -> log.error("[NonceManager] 链上查询 Nonce 失败: {}", error.getMessage()));
    }

    /**
     * 构造 Nonce Redis Key
     * 格式: nonce:{chain}:{address}
     * 
     * @param chain   链标识
     * @param address 钱包地址
     * @return Redis Key
     */
    private String buildNonceKey(String chain, String address) {
        return NONCE_KEY_PREFIX + chain.toLowerCase() + ":" + address.toLowerCase();
    }

    /**
     * 重置指定地址的 Nonce 缓存
     * 用于交易失败后重置 Nonce
     * 
     * @param chain   链标识
     * @param address 钱包地址
     * @return Mono<Boolean> 是否删除成功
     */
    public Mono<Boolean> resetNonce(String chain, String address) {
        String redisKey = buildNonceKey(chain, address);
        log.info("[NonceManager] 重置 Nonce 缓存, key={}", redisKey);
        return redisTemplate.delete(redisKey)
                .map(count -> count > 0);
    }

    /**
     * 获取当前缓存的 Nonce 值（不递增）
     * 用于调试和监控
     * 
     * @param chain   链标识
     * @param address 钱包地址
     * @return Mono<BigInteger> 当前缓存的 Nonce 值
     */
    public Mono<BigInteger> getCurrentNonce(String chain, String address) {
        String redisKey = buildNonceKey(chain, address);
        return redisTemplate.opsForValue()
                .get(redisKey)
                .map(nonceStr -> new BigInteger(nonceStr).subtract(BigInteger.ONE));
    }
}
