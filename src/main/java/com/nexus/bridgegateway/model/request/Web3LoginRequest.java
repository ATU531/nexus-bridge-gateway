package com.nexus.bridgegateway.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Web3 登录请求 (SIWE)
 * 
 * 【功能说明】：
 * 用于 EIP-4361 (Sign-In with Ethereum) 登录验证。
 * 
 * 【流程说明】：
 * 1. 前端先调用 /nonce 获取随机 Nonce
 * 2. 前端构造 SIWE 消息并让用户签名
 * 3. 前端将签名结果通过此接口提交验证
 */
@Data
public class Web3LoginRequest {

    /**
     * 钱包地址
     * 必须是有效的以太坊地址格式（0x 开头，40 位十六进制）
     */
    @NotBlank(message = "钱包地址不能为空")
    @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "钱包地址格式无效")
    private String walletAddress;

    /**
     * SIWE 消息原文
     * 包含 Nonce、域名、时间戳等信息的 EIP-4361 格式消息
     */
    @NotBlank(message = "消息不能为空")
    private String message;

    /**
     * 签名结果
     * 用户使用私钥对消息签名后的十六进制字符串
     */
    @NotBlank(message = "签名不能为空")
    private String signature;
}
