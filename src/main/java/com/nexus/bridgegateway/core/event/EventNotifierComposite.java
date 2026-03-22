package com.nexus.bridgegateway.core.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组合事件通知器
 * 
 * 【架构定位】：
 * 作为所有通知器的聚合入口，统一管理事件分发逻辑。
 * 
 * 【设计模式】：
 * 采用组合模式，将多个 EventNotifier 实现组合为一个统一入口，
 * 简化事件分发调用。
 * 
 * 【使用方式】：
 * 当需要向所有通知渠道发送事件时，直接调用此类的 notify 方法，
 * 它会自动遍历所有注册的通知器进行分发。
 */
@Slf4j
@Component
public class EventNotifierComposite implements EventNotifier {

    private final List<EventNotifier> notifiers;

    /**
     * 构造函数 - 自动注入所有 EventNotifier 实现
     * 
     * @param notifiers 所有事件通知器实现（Spring 自动注入）
     */
    public EventNotifierComposite(List<EventNotifier> notifiers) {
        this.notifiers = notifiers;
        log.info("[EventNotifierComposite] 初始化完成，聚合了 {} 个通知器", notifiers.size());
    }

    /**
     * 通知所有注册的通知器
     * 
     * 【分发策略】：
     * - 遍历所有通知器，依次调用 notify 方法
     * - 单个通知器失败不影响其他通知器
     * - 记录失败日志便于排查
     * 
     * @param event 链上交易事件
     */
    @Override
    public void notify(TransactionEvent event) {
        log.debug("[EventNotifierComposite] 分发事件到 {} 个通知器", notifiers.size());
        
        for (EventNotifier notifier : notifiers) {
            try {
                notifier.notify(event);
            } catch (Exception e) {
                log.error("[EventNotifierComposite] 通知器 {} 处理失败: {}", 
                        notifier.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 获取注册的通知器数量
     */
    public int getNotifierCount() {
        return notifiers.size();
    }
}
