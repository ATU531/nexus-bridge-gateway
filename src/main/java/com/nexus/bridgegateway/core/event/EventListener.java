package com.nexus.bridgegateway.core.event;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Log;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 事件监听中心
 * 监听链上合约 Event，转发给 Web2 业务方
 */
@Service
public class EventListener {

    private final Web3jRegistry web3jRegistry;
    private final EventNotifierComposite eventNotifier;

    public EventListener(Web3jRegistry web3jRegistry, EventNotifierComposite eventNotifier) {
        this.web3jRegistry = web3jRegistry;
        this.eventNotifier = eventNotifier;
    }

    /**
     * 订阅合约事件
     *
     * @param chain            链标识
     * @param contractAddress  合约地址
     * @param eventSignature   事件签名
     * @return Mono<Void>
     */
    public Mono<Void> subscribeEvent(String chain, String contractAddress, String eventSignature) {
        // 从多链注册中心获取对应链的 Web3j 客户端
        var web3j = web3jRegistry.getClient(chain);
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
