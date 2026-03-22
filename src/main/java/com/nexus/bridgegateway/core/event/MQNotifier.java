package com.nexus.bridgegateway.core.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息队列通知器
 * 
 * 【架构定位】：
 * 将链上事件发送到消息队列（RabbitMQ/Kafka），
 * 实现事件的可靠投递和异步处理。
 * 
 * 【适用场景】：
 * - 需要事件持久化和可靠投递
 * - 多个消费者需要处理同一事件
 * - 需要解耦事件生产者和消费者
 * - 高吞吐量场景下的削峰填谷
 * 
 * 【实现说明】：
 * 当前为占位实现，使用日志模拟消息发送。
 * 后续可扩展：
 * - 集成 RabbitTemplate 或 KafkaTemplate
 * - 实现消息序列化和路由
 * - 配置死信队列和重试策略
 */
@Slf4j
@Component
public class MQNotifier implements EventNotifier {

    @Override
    public void notify(TransactionEvent event) {
        log.info("[MQNotifier] 模拟发送到 RabbitMQ/Kafka: {}", event);
        
        // TODO: 实现真实的 MQ 发送
        // 1. 注入 RabbitTemplate 或 KafkaTemplate
        // 2. 序列化事件对象
        // 3. 发送到指定交换机/主题
        // 4. 处理发送失败的情况
    }
}
