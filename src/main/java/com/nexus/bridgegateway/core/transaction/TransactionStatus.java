package com.nexus.bridgegateway.core.transaction;

/**
 * 交易状态枚举
 */
public enum TransactionStatus {
    /**
     * 待处理
     */
    PENDING,

    /**
     * 已提交到内存池
     */
    SUBMITTED,

    /**
     * 已确认
     */
    CONFIRMED,

    /**
     * 已完成（已上链）
     */
    SUCCESS,

    /**
     * 失败
     */
    FAILED,

    /**
     * 已回滚
     */
    REVERTED
}
