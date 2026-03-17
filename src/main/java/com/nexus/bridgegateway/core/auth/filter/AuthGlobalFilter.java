package com.nexus.bridgegateway.core.auth.filter;

import com.nexus.bridgegateway.core.auth.JwtAuthenticationFilter;
import com.nexus.bridgegateway.service.IdentityMapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * 全局认证网关过滤器（重构版）
 *
 * 【架构说明】：
 * 1. 优先级：-99（在 JWT 鉴权过滤器 -100 之后执行）
 * 2. 信任链：JWT Filter -> X-Trusted-User-ID -> AuthGlobalFilter -> X-Nexus-Wallet-Address
 * 3. 安全性：只信任 JWT Filter 注入的 X-Trusted-User-ID，不直接信任客户端传入的 X-User-ID
 *
 * 拦截逻辑：
 * 1. 从请求头中提取 X-Trusted-User-ID（由 JWT Filter 注入，绝对可信）
 * 2. 如果不存在，尝试从 X-User-ID 提取（兼容模式，但不信任）
 * 3. 查询对应的钱包地址
 * 4. 将钱包地址添加到请求头 X-Nexus-Wallet-Address 中传递给下游服务
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 信任的用户 ID 请求头（由 JWT Filter 注入，绝对可信）
     */
    public static final String TRUSTED_USER_ID_HEADER = "X-Trusted-User-ID";

    /**
     * 请求头：Web2 用户ID（客户端传入，不信任，仅用于兼容）
     */
    public static final String HEADER_USER_ID = "X-User-ID";

    /**
     * 请求头：Web3 钱包地址（由本过滤器设置）
     */
    public static final String HEADER_WALLET_ADDRESS = "X-Nexus-Wallet-Address";

    private final IdentityMapperService identityMapperService;

    public AuthGlobalFilter(IdentityMapperService identityMapperService) {
        this.identityMapperService = identityMapperService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 优先使用 JWT Filter 注入的 X-Trusted-User-ID（绝对可信）
        String userId = request.getHeaders().getFirst(TRUSTED_USER_ID_HEADER);

        // 如果 JWT Filter 没有注入，尝试从 X-User-ID 提取（兼容模式，但不信任）
        if (userId == null || userId.isBlank()) {
            userId = request.getHeaders().getFirst(HEADER_USER_ID);
        }

        // 如果没有用户 ID，直接放行
        if (userId == null || userId.isBlank()) {
            log.debug("No trusted or user ID header found, passing through");
            return chain.filter(exchange);
        }

        // 将 userId 声明为 final 变量以便在 lambda 中使用
        final String finalUserId = userId;
        
        log.debug("Found user ID: {}, querying wallet address", finalUserId);

        // 调用 IdentityMapperService 获取钱包地址（返回 CompletableFuture）
        CompletableFuture<String> walletAddressFuture = identityMapperService.getWalletAddressByUserId(finalUserId);

        // 将 CompletableFuture 转换为 Mono 响应式流
        return Mono.fromFuture(walletAddressFuture)
                .flatMap(walletAddress -> {
                    if (walletAddress.isBlank()) {
                        log.warn("User {} has no bound wallet address", finalUserId);
                        // 钱包地址为空，仍然放行，但不下传钱包地址头
                        return chain.filter(exchange);
                    }

                    // 重写请求头，添加钱包地址
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header(HEADER_WALLET_ADDRESS, walletAddress)
                            .build();

                    log.debug("Added {} header: {} for user: {}",
                            HEADER_WALLET_ADDRESS, walletAddress, finalUserId);

                    // 将重写后的请求传递给下游
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to process authentication for user: {}", finalUserId, throwable);
                    // 发生错误时，仍然放行，避免阻塞请求
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        // 设置为 -99，在 JWT 鉴权过滤器（-100）之后执行
        return -99;
    }
}
