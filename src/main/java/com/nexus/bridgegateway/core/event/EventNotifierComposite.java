package com.nexus.bridgegateway.core.event;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 组合事件通知器
 * 同时支持多种通知方式
 */
@Component
public class EventNotifierComposite implements EventNotifier {

    private final List<EventNotifier> notifiers;

    public EventNotifierComposite(List<EventNotifier> notifiers) {
        this.notifiers = notifiers;
    }

    @Override
    public Mono<Void> notify(TransactionEvent event) {
        return Flux.fromIterable(notifiers)
                .flatMap(notifier -> notifier.notify(event))
                .then();
    }
}
