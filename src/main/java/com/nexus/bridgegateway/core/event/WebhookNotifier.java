package com.nexus.bridgegateway.core.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Webhook 通知器
 * 
 * 【架构定位】：
 * 将链上事件转换为 HTTP POST 回调，通知业务方系统。
 * 
 * 【适用场景】：
 * - 业务方需要实时接收链上事件
 * - 业务方有 HTTP 回调接收接口
 * - 需要将链上事件同步到传统 Web2 系统
 * 
 * 【实现说明】：
 * 当前为占位实现，使用日志模拟 HTTP POST。
 * 后续可扩展：
 * - 从配置中心获取业务方回调地址
 * - 使用 WebClient 发送异步 HTTP 请求
 * - 实现重试机制和失败处理
 */
@Slf4j
@Component
public class WebhookNotifier implements EventNotifier {

    @Override
    public void notify(TransactionEvent event) {
        log.info("[WebhookNotifier] 模拟 HTTP POST 到业务方回调地址: {}", event);
        
        // TODO: 实现真实的 HTTP POST 通知
        // 1. 从配置中心获取业务方回调 URL
        // 2. 使用 WebClient 发送异步 POST 请求
        // 3. 处理响应和异常
        // 4. 实现重试机制
    }
}
