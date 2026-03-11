package com.nexus.bridgegateway.core.event;

import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 事件监听中心
 * 监听链上合约 Event，转发给 Web2 业务方
 */
@Service
public class EventListener {

    private final Web3j web3j;
    private final EventNotifierComposite eventNotifier;

    public EventListener(Web3j web3j, EventNotifierComposite eventNotifier) {
        this.web3j = web3j;
        this.eventNotifier = eventNotifier;
    }

    /**
     * 订阅合约事件
     */
    public Mono<Void> subscribeEvent(String contractAddress, String eventSignature) {
        // TODO: 使用 Web3j 订阅合约事件
        // TODO: 解析事件参数
        // TODO: 调用 EventNotifier 通知业务方
        return Mono.empty();
    }

    /**
     * 处理链上日志
     */
    public Mono<Void> processLogs(List<Log> logs) {
        return Flux.fromIterable(logs)
                .map(this::parseLog)
                .flatMap(eventNotifier::notify)
                .then();
    }

    /**
     * 解析日志为事件对象
     */
    private TransactionEvent parseLog(Log log) {
        TransactionEvent event = new TransactionEvent();
        event.setTransactionHash(log.getTransactionHash());
        event.setBlockNumber(log.getBlockNumber());
        event.setContractAddress(log.getAddress());
        event.setTopics(log.getTopics());
        event.setData(log.getData());
        return event;
    }
}
