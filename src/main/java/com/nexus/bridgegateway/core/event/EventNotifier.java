package com.nexus.bridgegateway.core.event;

/**
 * 事件通知接口
 * 
 * 【架构定位】：
 * 定义链上事件通知的标准接口，支持多种通知策略的实现。
 * 
 * 【设计模式】：
 * 采用策略模式，不同的通知实现（Webhook、MQ、WebSocket）
 * 通过实现此接口，以统一的方式处理链上事件。
 * 
 * 【实现类】：
 * - WebhookNotifier: HTTP POST 回调通知业务方
 * - MQNotifier: 发送到消息队列（RabbitMQ/Kafka）
 * - WebSocketNotifier: 实时推送到前端 WebSocket 连接
 */
public interface EventNotifier {

    /**
     * 通知事件
     * 
     * 【职责】：
     * 将链上事件转发给对应的下游系统或客户端。
     * 
     * 【实现要求】：
     * - 实现类应保证通知的可靠性
     * - 异常不应影响其他通知器的执行
     * - 建议使用异步方式处理，避免阻塞事件监听主流程
     * 
     * @param event 链上交易事件
     */
    void notify(TransactionEvent event);
}
