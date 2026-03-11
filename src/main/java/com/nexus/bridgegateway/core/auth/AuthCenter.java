package com.nexus.bridgegateway.core.auth;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 统一认证中心
 * JWT 与钱包地址的映射及 SIWE 校验
 */
@Service
public class AuthCenter {

    /**
     * Web2 登录（手机号/邮箱）
     */
    public Mono<String> web2Login(String phoneOrEmail, String verificationCode) {
        // TODO: 实现验证码校验
        // TODO: 生成或获取托管钱包地址
        // TODO: 签发 JWT Token
        return Mono.just(generateJwtToken(phoneOrEmail));
    }

    /**
     * Web3 登录（SIWE）
     */
    public Mono<String> web3Login(String walletAddress, String signature, String message) {
        // TODO: 验证以太坊签名 (EIP-4361)
        // TODO: 签发 JWT Token
        return Mono.just(generateJwtToken(walletAddress));
    }

    /**
     * 为用户创建托管钱包
     */
    public Mono<String> createCustodyWallet(String userId) {
        // TODO: 生成钱包地址和私钥
        // TODO: 加密存储私钥
        return Mono.just("0x...");
    }

    /**
     * 验证 JWT Token
     */
    public Mono<Boolean> validateToken(String token) {
        // TODO: 验证 JWT 有效性
        return Mono.just(true);
    }

    /**
     * 从 Token 中获取钱包地址
     */
    public Mono<String> getWalletAddressFromToken(String token) {
        // TODO: 解析 JWT 获取钱包地址
        return Mono.just("0x...");
    }

    private String generateJwtToken(String subject) {
        // TODO: 使用 jjwt 生成 Token
        return "jwt.token.here";
    }
}
