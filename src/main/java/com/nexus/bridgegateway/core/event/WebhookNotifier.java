package com.nexus.bridgegateway.core.event;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Webhook 通知器
 */
@Component
public class WebhookNotifier implements EventNotifier {

    private final WebClient webClient;

    public WebhookNotifier() {
        this.webClient = WebClient.builder().build();
    }

    @Override
    public Mono<Void> notify(TransactionEvent event) {
        // TODO: 从配置中获取业务方回调地址
        String webhookUrl = "https://example.com/webhook";

        // 使用 WebClient 发送异步 HTTP POST 请求
        return webClient.post()
                .uri(webhookUrl)
                .bodyValue(event)
                .retrieve()
                .toBodilessEntity()
                .then();
    }
}
