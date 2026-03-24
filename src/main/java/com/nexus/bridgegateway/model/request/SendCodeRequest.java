package com.nexus.bridgegateway.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码请求
 * 
 * 【功能说明】：
 * 用于 Web2 登录前获取验证码。
 * 
 * 【流程说明】：
 * 1. 前端调用此接口发送验证码
 * 2. 系统生成 6 位数字验证码并存入 Redis
 * 3. 生产环境应调用短信/邮件服务发送验证码
 * 
 * 【验证码有效期】：
 * 5 分钟，过期后需重新获取。
 */
@Data
public class SendCodeRequest {

    /**
     * 手机号或邮箱
     * 支持手机号或邮箱格式
     */
    @NotBlank(message = "手机号或邮箱不能为空")
    private String phoneOrEmail;
}
