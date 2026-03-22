package com.nexus.bridgegateway.core.event;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * 合约事件监听服务
 * 
 * 【架构定位】：
 * 作为 Web3j 事件订阅到 Web2 通知推送的桥梁，
 * 负责监听链上合约事件并分发给所有注册的通知器。
 * 
 * 【核心职责】：
 * 1. 订阅指定链上合约的事件日志
 * 2. 解析原始日志为标准化的事件对象
 * 3. 将事件分发给所有通知器（Webhook、MQ、WebSocket）
 * 
 * 【技术实现】：
 * - 使用 Web3j 的 ethLogFlowable 订阅链上日志
 * - 将 RxJava Flowable 转换为 Project Reactor Flux
 * - 使用 Java 21 虚拟线程执行阻塞操作
 * - 支持多链、多合约的事件订阅
 * 
 * 【线程模型】：
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    事件监听线程模型                          │
 * ├─────────────────────────────────────────────────────────────┤
 * │  Web3j RxJava Flowable (IO 线程)                            │
 * │         │                                                   │
 * │         ▼                                                   │
 * │  Flux.create (转换为 Reactor)                               │
 * │         │                                                   │
 * │         ▼                                                   │
 * │  虚拟线程执行通知逻辑 (非阻塞)                               │
 * │         │                                                   │
 * │         ▼                                                   │
 * │  并行分发到所有 Notifier                                     │
 * └─────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
public class ContractEventListenerService {

    private final Web3jRegistry web3jRegistry;
    private final List<EventNotifier> notifiers;

    /**
     * 活跃的订阅记录
     * Key: 订阅标识 (chain:contractAddress)
     * Value: 订阅句柄
     */
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();

    /**
     * 构造函数 - 动态注入所有 EventNotifier 实现
     * 
     * Spring 会自动注入所有实现了 EventNotifier 接口的 Bean，
     * 实现发布-订阅模式的自动注册。
     * 
     * @param web3jRegistry 多链 Web3j 客户端注册中心
     * @param notifiers     所有事件通知器实现（自动注入）
     */
    public ContractEventListenerService(
            Web3jRegistry web3jRegistry,
            List<EventNotifier> notifiers) {
        this.web3jRegistry = web3jRegistry;
        this.notifiers = notifiers;
        log.info("[EventListener] 初始化完成，注册了 {} 个通知器", notifiers.size());
        notifiers.forEach(notifier -> 
                log.info("[EventListener] - {}", notifier.getClass().getSimpleName()));
    }

    /**
     * 订阅合约事件
     * 
     * 【功能说明】：
     * 订阅指定链上合约的所有事件，并将事件分发给所有通知器。
     * 
     * 【执行流程】：
     * 1. 获取对应链的 Web3j 客户端
     * 2. 构建事件过滤器（指定合约地址）
     * 3. 使用 ethLogFlowable 订阅日志
     * 4. 将 RxJava Flowable 转换为 Reactor Flux
     * 5. 解析日志并分发给通知器
     * 
     * 【架构提示】：
     * Web3j 的 ethLogFlowable 返回 RxJava 的 Flowable，
     * 通过 Flux.create 将其转换为 Project Reactor 的 Flux，
     * 以完美融入 WebFlux 响应式体系。
     * 
     * @param chain           链标识 (eth, bsc, polygon)
     * @param contractAddress 合约地址
     * @return Flux<TransactionEvent> 事件流
     */
    public Flux<TransactionEvent> subscribeToContractEvent(String chain, String contractAddress) {
        String subscriptionKey = chain + ":" + contractAddress;
        log.info("[EventListener] 开始订阅合约事件, chain={}, contract={}", chain, contractAddress);

        // 检查是否已存在订阅
        if (activeSubscriptions.containsKey(subscriptionKey)) {
            log.warn("[EventListener] 订阅已存在, key={}", subscriptionKey);
            return Flux.error(new IllegalStateException("Subscription already exists: " + subscriptionKey));
        }

        // 获取对应链的 Web3j 客户端
        Web3j web3j = web3jRegistry.getClient(chain);

        // 构建事件过滤器
        EthFilter filter = new EthFilter(
                DefaultBlockParameterName.LATEST,
                DefaultBlockParameterName.LATEST,
                contractAddress
        );

        return Flux.<TransactionEvent>create(emitter -> {
                    log.debug("[EventListener] 创建事件订阅 Flux, chain={}, contract={}", chain, contractAddress);

                    // 【关键】订阅链上日志
                    // Web3j 返回 RxJava 的 Flowable，需要转换为 Reactor Flux
                    Disposable subscription = web3j.ethLogFlowable(filter)
                            .subscribe(
                                    ethLog -> {
                                        try {
                                            TransactionEvent event = parseLogToEvent(chain, ethLog);
                                            
                                            emitter.next(event);
                                            
                                            dispatchToNotifiers(event);
                                            
                                        } catch (Exception e) {
                                            log.error("[EventListener] 处理日志异常: {}", e.getMessage(), e);
                                        }
                                    },
                                    error -> {
                                        log.error("[EventListener] 订阅异常: {}", error.getMessage(), error);
                                        emitter.error(error);
                                        activeSubscriptions.remove(subscriptionKey);
                                    },
                                    () -> {
                                        log.info("[EventListener] 订阅完成, key={}", subscriptionKey);
                                        emitter.complete();
                                        activeSubscriptions.remove(subscriptionKey);
                                    }
                            );

                    // 记录活跃订阅
                    activeSubscriptions.put(subscriptionKey, subscription);

                    // 注册取消回调
                    emitter.onDispose(() -> {
                        log.info("[EventListener] 取消订阅, key={}", subscriptionKey);
                        if (!subscription.isDisposed()) {
                            subscription.dispose();
                        }
                        activeSubscriptions.remove(subscriptionKey);
                    });

                }, FluxSink.OverflowStrategy.BUFFER)
                .subscribeOn(Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor()))
                .doOnSubscribe(s -> log.info("[EventListener] Flux 订阅开始, chain={}, contract={}", chain, contractAddress))
                .doOnCancel(() -> log.info("[EventListener] Flux 取消订阅, chain={}, contract={}", chain, contractAddress))
                .doOnError(e -> log.error("[EventListener] Flux 异常, chain={}, contract={}, error={}", 
                        chain, contractAddress, e.getMessage()));
    }

    /**
     * 解析链上日志为事件对象
     * 
     * 【解析逻辑】：
     * 1. 提取交易哈希、区块号、合约地址
     * 2. 从 Topic[0] 提取事件签名（事件名称）
     * 3. 解析事件参数（简化实现，实际需根据 ABI 解码）
     * 
     * @param chain 链标识
     * @param ethLog   链上日志
     * @return 标准化的事件对象
     */
    private TransactionEvent parseLogToEvent(String chain, Log ethLog) {
        TransactionEvent event = new TransactionEvent();
        event.setChain(chain);
        event.setContractAddress(ethLog.getAddress());
        event.setTransactionHash(ethLog.getTransactionHash());
        event.setBlockNumber(ethLog.getBlockNumber());

        // 从 Topic[0] 提取事件签名
        // Topic[0] 是事件签名的 Keccak-256 哈希
        List<String> topics = ethLog.getTopics();
        if (!topics.isEmpty()) {
            String eventSignature = topics.get(0);
            event.setEventName(parseEventName(eventSignature));
        }

        // 解析事件参数（简化实现）
        // 实际生产环境需要根据合约 ABI 进行解码
        Map<String, Object> eventData = parseEventData(topics, ethLog.getData());
        event.setEventData(eventData);

        log.debug("[EventListener] 解析事件: chain={}, txHash={}, eventName={}, blockNumber={}",
                chain, event.getTransactionHash(), event.getEventName(), event.getBlockNumber());

        return event;
    }

    /**
     * 解析事件名称
     * 
     * 【说明】：
     * 实际生产环境需要维护事件签名到事件名称的映射表，
     * 或通过合约 ABI 动态解析。
     * 
     * @param eventSignature 事件签名哈希
     * @return 事件名称
     */
    private String parseEventName(String eventSignature) {
        // 简化实现：返回签名哈希
        // 实际应通过映射表或 ABI 解析
        if (eventSignature == null || eventSignature.isEmpty()) {
            return "Unknown";
        }
        
        // 常见事件签名映射（示例）
        // Transfer(address,address,uint256) = 0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef
        // Approval(address,address,uint256) = 0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925
        Map<String, String> knownSignatures = Map.of(
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef", "Transfer",
                "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925", "Approval",
                "0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67", "Swap"
        );
        
        return knownSignatures.getOrDefault(eventSignature, "Event_" + eventSignature.substring(2, 10));
    }

    /**
     * 解析事件数据
     * 
     * 【说明】：
     * 简化实现，将 Topics 和 Data 存入 Map。
     * 实际生产环境需要根据合约 ABI 进行解码。
     * 
     * @param topics 事件主题列表
     * @param data   事件数据
     * @return 事件参数字典
     */
    private Map<String, Object> parseEventData(List<String> topics, String data) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        
        // 存储原始 Topics（Topic[0] 是事件签名，Topic[1..n] 是索引参数）
        eventData.put("topics", topics);
        
        // 存储原始 Data（非索引参数）
        eventData.put("rawData", data);
        
        return eventData;
    }

    /**
     * 分发事件到所有通知器
     * 
     * 【发布-订阅模式】：
     * 遍历所有注册的通知器，将事件分发给它们处理。
     * 每个通知器独立处理，互不影响。
     * 
     * @param event 事件对象
     */
    private void dispatchToNotifiers(TransactionEvent event) {
        log.debug("[EventListener] 分发事件到 {} 个通知器", notifiers.size());
        
        for (EventNotifier notifier : notifiers) {
            try {
                notifier.notify(event);
            } catch (Exception e) {
                // 单个通知器失败不影响其他通知器
                log.error("[EventListener] 通知器 {} 处理失败: {}", 
                        notifier.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 取消订阅
     * 
     * @param chain           链标识
     * @param contractAddress 合约地址
     */
    public void unsubscribe(String chain, String contractAddress) {
        String subscriptionKey = chain + ":" + contractAddress;
        Disposable subscription = activeSubscriptions.remove(subscriptionKey);
        
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("[EventListener] 已取消订阅, key={}", subscriptionKey);
        } else {
            log.warn("[EventListener] 订阅不存在, key={}", subscriptionKey);
        }
    }

    /**
     * 获取活跃订阅数量
     */
    public int getActiveSubscriptionCount() {
        return activeSubscriptions.size();
    }

    /**
     * 关闭所有订阅
     */
    public void shutdown() {
        log.info("[EventListener] 关闭所有订阅, count={}", activeSubscriptions.size());
        activeSubscriptions.forEach((key, subscription) -> {
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        activeSubscriptions.clear();
    }
}
