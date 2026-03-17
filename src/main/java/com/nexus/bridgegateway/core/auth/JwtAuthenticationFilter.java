package com.nexus.bridgegateway.core.auth;

import com.nexus.bridgegateway.core.auth.filter.AuthGlobalFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 鉴权过滤器
 *
 * 优先级：-100（必须高于身份映射 Filter 的 -99）
 *
 * 拦截逻辑：
 * 1. 检查白名单路径（/internal/** 和登录接口），直接放行
 * 2. 从 Authorization 请求头提取 Bearer Token
 * 3. 验证 Token 有效性
 * 4. 如果无效，返回 401 Unauthorized
 * 5. 如果有效，将 userId 注入到 X-Trusted-User-ID 请求头传递给下游
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /**
     * JWT 请求头
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * 信任的用户 ID 请求头（由 JWT Filter 注入，绝对可信）
     */
    public static final String TRUSTED_USER_ID_HEADER = "X-Trusted-User-ID";

    private final JwtUtils jwtUtils;

    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();

        // 白名单路径直接放行（内部服务路径和测试接口）
        if (path.startsWith("/internal/") || path.startsWith("/api/v1/test/")) {
            log.debug("Whitelist path {}, passing through", path);
            return chain.filter(exchange);
        }

        // 尝试从 Authorization 请求头提取 Bearer Token
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No Authorization header found, returning 401");
            return renderUnauthorized(exchange);
        }

        String token = authHeader.substring(7);

        // 验证 Token
        if (!jwtUtils.validateToken(token)) {
            log.warn("Invalid or expired JWT token");
            return renderUnauthorized(exchange);
        }

        // 解析 userId
        String userId = jwtUtils.getUserIdFromToken(token);
        if (userId == null) {
            log.warn("Failed to parse userId from JWT token");
            return renderUnauthorized(exchange);
        }

        log.debug("JWT token validated successfully, userId: {}", userId);

        // 将解析出的 userId 注入到请求头，传递给下游
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(TRUSTED_USER_ID_HEADER, userId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 返回 401 Unauthorized 响应
     */
    private Mono<Void> renderUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        String body = "{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        // 设置为 -100，优先级高于 AuthGlobalFilter 的 -99
        return -100;
    }
}
