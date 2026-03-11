package com.nexus.bridgegateway.core.event;

import reactor.core.publisher.Mono;

/**
 * 事件通知接口
 */
public interface EventNotifier {

    /**
     * 通知事件
     */
    Mono<Void> notify(TransactionEvent event);
}
