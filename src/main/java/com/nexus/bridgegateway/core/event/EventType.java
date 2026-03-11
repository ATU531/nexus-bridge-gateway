package com.nexus.bridgegateway.core.event;

/**
 * 事件类型枚举
 */
public enum EventType {
    /**
     * 转账事件
     */
    TRANSFER,

    /**
     * 铸造事件
     */
    MINT,

    /**
     * 销毁事件
     */
    BURN,

    /**
     * 兑换事件
     */
    SWAP,

    /**
     * 自定义事件
     */
    CUSTOM
}
