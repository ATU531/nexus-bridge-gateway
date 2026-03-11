package com.nexus.bridgegateway.core.event;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 消息队列通知器
 */
@Component
public class MQNotifier implements EventNotifier {

    private final RabbitTemplate rabbitTemplate;

    public MQNotifier(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public Mono<Void> notify(TransactionEvent event) {
        return Mono.fromRunnable(() -> {
            // TODO: 发送到 RabbitMQ 队列
            // rabbitTemplate.convertAndSend("blockchain.events", event);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
