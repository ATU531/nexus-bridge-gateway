package com.nexus.bridgegateway.core.event;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 事件监听中心
 * 
 * 【架构定位】：
 * 监听链上合约 Event，转发给 Web2 业务方。
 * 作为事件监听中心的入口服务，提供事件订阅和处理能力。
 * 
 * 【核心职责】：
 * 1. 提供事件订阅入口
 * 2. 解析链上日志为标准化事件对象
 * 3. 协调事件分发流程
 * 
 * 【与 ContractEventListenerService 的关系】：
 * - EventListener: 高层门面，提供简化的 API
 * - ContractEventListenerService: 底层实现，处理具体的事件订阅逻辑
 */
@Slf4j
@Service
public class EventListener {

    private final Web3jRegistry web3jRegistry;
    private final EventNotifierComposite eventNotifier;
    private final ContractEventListenerService contractEventListenerService;

    public EventListener(
            Web3jRegistry web3jRegistry,
            EventNotifierComposite eventNotifier,
            ContractEventListenerService contractEventListenerService) {
        this.web3jRegistry = web3jRegistry;
        this.eventNotifier = eventNotifier;
        this.contractEventListenerService = contractEventListenerService;
    }

    /**
     * 订阅合约事件
     * 
     * 【功能说明】：
     * 订阅指定链上合约的所有事件，并将事件分发给所有通知器。
     * 
     * @param chain            链标识
     * @param contractAddress  合约地址
     */
    public void subscribeToContractEvent(String chain, String contractAddress) {
        log.info("[EventListener] 订阅合约事件, chain={}, contract={}", chain, contractAddress);
        contractEventListenerService.subscribeToContractEvent(chain, contractAddress)
                .subscribe(
                        event -> log.debug("[EventListener] 收到事件: {}", event),
                        error -> log.error("[EventListener] 订阅异常: {}", error.getMessage()),
                        () -> log.info("[EventListener] 订阅完成")
                );
    }

    /**
     * 处理链上日志列表
     * 
     * 【适用场景】：
     * 批量处理历史日志或手动触发的事件处理。
     * 
     * @param logs 链上日志列表
     */
    public void processLogs(List<Log> logs) {
        log.info("[EventListener] 处理 {} 条日志", logs.size());
        for (Log ethLog : logs) {
            try {
                TransactionEvent event = parseLog(ethLog);
                eventNotifier.notify(event);
            } catch (Exception e) {
                log.error("[EventListener] 处理日志失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 解析日志为事件对象
     * 
     * @param ethLog 链上日志
     * @return 标准化的事件对象
     */
    private TransactionEvent parseLog(Log ethLog) {
        TransactionEvent event = new TransactionEvent();
        event.setContractAddress(ethLog.getAddress());
        event.setTransactionHash(ethLog.getTransactionHash());
        event.setBlockNumber(ethLog.getBlockNumber());
        
        // 从 Topic[0] 提取事件签名
        List<String> topics = ethLog.getTopics();
        if (!topics.isEmpty()) {
            String eventSignature = topics.get(0);
            event.setEventName(parseEventName(eventSignature));
        }
        
        // 存储原始数据
        event.setEventData(Map.of(
                "topics", topics,
                "rawData", ethLog.getData()
        ));
        
        return event;
    }

    /**
     * 解析事件名称
     */
    private String parseEventName(String eventSignature) {
        Map<String, String> knownSignatures = Map.of(
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef", "Transfer",
                "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925", "Approval"
        );
        return knownSignatures.getOrDefault(eventSignature, "Event_" + eventSignature.substring(2, 10));
    }
}
