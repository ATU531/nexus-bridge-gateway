package com.nexus.bridgegateway.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Web2 登录请求
 * 
 * 【功能说明】：
 * 用于手机号/邮箱登录验证。
 * 
 * 【流程说明】：
 * 1. 前端先调用 /send-code 获取验证码
 * 2. 用户输入验证码后通过此接口提交验证
 * 3. 验证通过后系统为用户创建托管钱包（新用户）或返回已有钱包
 * 
 * 【托管模式】：
 * Web2 用户登录后，系统自动为其生成托管钱包，
 * 用户无需管理私钥即可使用 Web3 功能。
 */
@Data
public class Web2LoginRequest {

    /**
     * 手机号或邮箱
     * 支持手机号（11位数字）或邮箱格式
     */
    @NotBlank(message = "手机号或邮箱不能为空")
    private String phoneOrEmail;

    /**
     * 验证码
     * 6 位数字验证码
     */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字")
    private String verificationCode;
}
