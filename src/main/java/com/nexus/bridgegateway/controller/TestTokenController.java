package com.nexus.bridgegateway.controller;

import com.nexus.bridgegateway.core.auth.JwtUtils;
import com.nexus.bridgegateway.core.tx.ReactiveNonceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 临时 Token 生成控制器（仅用于测试）
 * 生产环境应移除或禁用此控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
public class TestTokenController {

    private final JwtUtils jwtUtils;
    private final ReactiveNonceManager nonceManager;

    /**
     * 生成测试用的 JWT Token
     *
     * @param userId 用户ID
     * @return 包含 Token 的响应
     */
    @GetMapping("/generate-token/{userId}")
    public ResponseEntity<Map<String, Object>> generateToken(@PathVariable String userId) {
        log.info("Generating test token for user: {}", userId);
        
        String token = jwtUtils.generateToken(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "Token generated successfully");
        response.put("data", Map.of(
                "token", token,
                "userId", userId,
                "type", "Bearer"
        ));
        
        return ResponseEntity.ok(response);
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token JWT Token
     * @return 验证结果
     */
    @GetMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        log.info("Validating token");
        
        boolean isValid = jwtUtils.validateToken(token);
        String userId = jwtUtils.getUserIdFromToken(token);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "Token validation completed");
        response.put("data", Map.of(
                "valid", isValid,
                "userId", userId
        ));
        
        return ResponseEntity.ok(response);
    }

    /**
     * Nonce 并发测试接口
     * 
     * 使用 Flux.range(1, 10).flatMap() 模拟 10 个并发请求同时抢 Nonce
     * 验证分配的 Nonce 是否连续（如 10, 11, 12, 13...19）
     *
     * @param chain   链标识 (eth, bsc, polygon)
     * @param address 钱包地址
     * @param count   并发请求数量（默认 10）
     * @return 测试结果
     */
    @GetMapping("/nonce/concurrent")
    public Mono<ResponseEntity<Map<String, Object>>> testConcurrentNonce(
            @RequestParam(defaultValue = "eth") String chain,
            @RequestParam(defaultValue = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045") String address,
            @RequestParam(defaultValue = "10") int count) {

        log.info("========== Nonce 并发测试开始 ==========");
        log.info("链: {}, 地址: {}, 并发数: {}", chain, address, count);

        long startTime = System.currentTimeMillis();

        // 使用 Flux.range 模拟并发请求
        // flatMap 会并行执行，模拟真实的高并发场景
        return Flux.range(1, count)
                .flatMap(i -> {
                    log.info("发起第 {} 个 Nonce 请求", i);
                    return nonceManager.allocateNonce(chain, address)
                            .doOnNext(nonce -> log.info("第 {} 个请求分配到 Nonce: {}", i, nonce));
                })
                .collectList()
                .map(nonces -> {
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;

                    // 排序 Nonce 列表
                    List<BigInteger> sortedNonces = nonces.stream()
                            .sorted()
                            .collect(Collectors.toList());

                    // 检查是否连续
                    boolean isConsecutive = checkConsecutive(sortedNonces);

                    // 打印结果
                    log.info("========== Nonce 并发测试结果 ==========");
                    log.info("总请求数: {}", count);
                    log.info("总耗时: {} ms", duration);
                    log.info("分配的 Nonce (排序后): {}", sortedNonces);
                    log.info("是否连续: {}", isConsecutive ? "✅ 是" : "❌ 否");

                    if (!isConsecutive) {
                        log.warn("Nonce 不连续！可能存在并发问题！");
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "Nonce concurrent test completed");
                    response.put("data", Map.of(
                            "totalRequests", count,
                            "durationMs", duration,
                            "nonces", sortedNonces,
                            "isConsecutive", isConsecutive,
                            "minNonce", sortedNonces.get(0),
                            "maxNonce", sortedNonces.get(sortedNonces.size() - 1)
                    ));

                    return ResponseEntity.ok(response);
                })
                .doOnError(error -> log.error("Nonce 并发测试失败: {}", error.getMessage(), error));
    }

    /**
     * 重置 Nonce 缓存
     * 用于测试前清理缓存
     *
     * @param chain   链标识
     * @param address 钱包地址
     * @return 重置结果
     */
    @GetMapping("/nonce/reset")
    public Mono<ResponseEntity<Map<String, Object>>> resetNonce(
            @RequestParam(defaultValue = "eth") String chain,
            @RequestParam(defaultValue = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045") String address) {

        log.info("重置 Nonce 缓存: chain={}, address={}", chain, address);

        return nonceManager.resetNonce(chain, address)
                .map(success -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", success ? "Nonce cache reset successfully" : "No cache to reset");
                    response.put("data", Map.of("reset", success));
                    return ResponseEntity.ok(response);
                });
    }

    /**
     * 检查 Nonce 列表是否连续
     */
    private boolean checkConsecutive(List<BigInteger> nonces) {
        if (nonces.isEmpty()) {
            return true;
        }
        
        for (int i = 1; i < nonces.size(); i++) {
            // 检查相邻两个 Nonce 差值是否为 1
            if (!nonces.get(i).subtract(nonces.get(i - 1)).equals(BigInteger.ONE)) {
                return false;
            }
        }
        return true;
    }
}
