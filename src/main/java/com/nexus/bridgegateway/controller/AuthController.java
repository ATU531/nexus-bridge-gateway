package com.nexus.bridgegateway.controller;

import com.nexus.bridgegateway.core.auth.AuthCenter;
import com.nexus.bridgegateway.model.ApiResponse;
import com.nexus.bridgegateway.model.request.SendCodeRequest;
import com.nexus.bridgegateway.model.request.Web2LoginRequest;
import com.nexus.bridgegateway.model.request.Web3LoginRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * 统一认证控制器
 * 
 * 【架构定位】：
 * 提供 Web2/Web3 统一认证入口，支持 SIWE (Sign-In with Ethereum) 登录。
 * 
 * 【核心功能】：
 * 1. 生成 SIWE Nonce（Web3 登录前置）
 * 2. 发送验证码（Web2 登录前置）
 * 3. Web2 登录（手机号/邮箱 + 验证码）
 * 4. Web3 登录（钱包签名）
 * 
 * 【API 设计】：
 * - GET  /api/v1/auth/nonce       - 获取 SIWE Nonce
 * - POST /api/v1/auth/send-code   - 发送验证码
 * - POST /api/v1/auth/login/web2  - Web2 登录
 * - POST /api/v1/auth/login/web3  - Web3 登录
 * 
 * 【托管模式说明】：
 * Web2 用户登录后，系统自动为其创建托管钱包，
 * 用户无需管理私钥即可使用 Web3 功能。
 * 这是 Web2 用户向 Web3 过渡的核心托管方案。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthCenter authCenter;

    /**
     * 获取 SIWE Nonce
     * 
     * 【功能说明】：
     * 生成一个随机 Nonce，用于 SIWE 登录流程的防重放攻击。
     * 
     * 【流程】：
     * 1. 生成随机 UUID 作为 Nonce
     * 2. 存入 Redis，设置 5 分钟过期
     * 3. 返回给前端用于构造 SIWE 消息
     * 
     * @return 包含 nonce 的响应
     */
    @GetMapping("/nonce")
    public Mono<ApiResponse<Map<String, String>>> getNonce() {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        log.info("[AuthController] 生成 SIWE Nonce: {}", nonce);
        
        return authCenter.storeNonce(nonce)
                .map(stored -> {
                    if (stored) {
                        return ApiResponse.success(Map.of("nonce", nonce));
                    } else {
                        return ApiResponse.<Map<String, String>>error(500, "存储 Nonce 失败");
                    }
                });
    }

    /**
     * 发送验证码
     * 
     * 【功能说明】：
     * 生成 6 位数字验证码并存入 Redis，用于 Web2 登录验证。
     * 
     * 【流程】：
     * 1. 生成 6 位随机数字验证码
     * 2. 存入 Redis，设置 5 分钟过期
     * 3. 生产环境应调用短信/邮件服务发送验证码
     * 
     * 【模拟模式】：
     * 当前为模拟模式，验证码直接返回给前端。
     * 生产环境应通过短信/邮件发送，不返回验证码。
     * 
     * @param request 发送验证码请求
     * @return 包含验证码的响应（模拟模式）
     */
    @PostMapping("/send-code")
    public Mono<ApiResponse<Map<String, String>>> sendCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("[AuthController] 收到发送验证码请求, phoneOrEmail={}", request.getPhoneOrEmail());
        
        return authCenter.generateAndStoreVerificationCode(request.getPhoneOrEmail())
                .map(code -> {
                    log.info("[AuthController] 验证码发送成功, phoneOrEmail={}", request.getPhoneOrEmail());
                    // 【模拟模式】返回验证码，生产环境应移除
                    return ApiResponse.success(Map.of(
                            "message", "验证码发送成功",
                            "code", code  // 生产环境应移除此行
                    ));
                })
                .onErrorResume(e -> {
                    log.error("[AuthController] 验证码发送失败: {}", e.getMessage());
                    return Mono.just(ApiResponse.<Map<String, String>>error(500, "验证码发送失败"));
                });
    }

    /**
     * Web2 登录（手机号/邮箱）
     * 
     * 【功能说明】：
     * 验证验证码并完成登录，返回 JWT Token。
     * 
     * 【验证流程】：
     * 1. 校验验证码是否有效
     * 2. 查询或创建用户身份
     * 3. 新用户自动创建托管钱包
     * 4. 签发 JWT Token
     * 
     * 【托管钱包】：
     * Web2 用户登录后，系统自动为其创建托管钱包。
     * 用户无需管理私钥即可使用 Web3 功能。
     * selfCustody = false 表示托管模式。
     * 
     * @param request Web2 登录请求
     * @return 包含 JWT Token 的响应
     */
    @PostMapping("/login/web2")
    public Mono<ApiResponse<Map<String, String>>> web2Login(@Valid @RequestBody Web2LoginRequest request) {
        log.info("[AuthController] 收到 Web2 登录请求, phoneOrEmail={}", request.getPhoneOrEmail());
        
        return authCenter.web2Login(request.getPhoneOrEmail(), request.getVerificationCode())
                .map(token -> {
                    log.info("[AuthController] Web2 登录成功, phoneOrEmail={}", request.getPhoneOrEmail());
                    return ApiResponse.success(Map.of("token", token));
                })
                .onErrorResume(e -> {
                    log.error("[AuthController] Web2 登录失败: {}", e.getMessage());
                    return Mono.just(ApiResponse.<Map<String, String>>error(401, e.getMessage()));
                });
    }

    /**
     * Web3 登录 (SIWE)
     * 
     * 【功能说明】：
     * 验证以太坊签名并完成登录，返回 JWT Token。
     * 
     * 【验证流程】：
     * 1. 校验 Nonce 是否有效（防重放）
     * 2. 验证以太坊签名（EIP-4361）
     * 3. 查询或创建用户身份
     * 4. 签发 JWT Token
     * 
     * 【自托管模式】：
     * Web3 用户自己管理私钥，系统不存储私钥。
     * selfCustody = true 表示自托管模式。
     * 
     * @param request Web3 登录请求
     * @return 包含 JWT Token 的响应
     */
    @PostMapping("/login/web3")
    public Mono<ApiResponse<Map<String, String>>> web3Login(@Valid @RequestBody Web3LoginRequest request) {
        log.info("[AuthController] 收到 Web3 登录请求, walletAddress={}", request.getWalletAddress());
        
        return authCenter.web3Login(
                        request.getWalletAddress(),
                        request.getMessage(),
                        request.getSignature())
                .map(token -> {
                    log.info("[AuthController] Web3 登录成功, walletAddress={}", request.getWalletAddress());
                    return ApiResponse.success(Map.of("token", token));
                })
                .onErrorResume(e -> {
                    log.error("[AuthController] Web3 登录失败: {}", e.getMessage());
                    return Mono.just(ApiResponse.<Map<String, String>>error(401, e.getMessage()));
                });
    }
}
