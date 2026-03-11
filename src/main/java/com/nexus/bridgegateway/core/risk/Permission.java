package com.nexus.bridgegateway.core.risk;

/**
 * 权限枚举
 */
public enum Permission {
    /**
     * 查询余额
     */
    QUERY_BALANCE,

    /**
     * 查看交易记录
     */
    VIEW_TRANSACTIONS,

    /**
     * 发起转账
     */
    TRANSFER,

    /**
     * 批量发奖
     */
    BATCH_REWARD,

    /**
     * 合约部署
     */
    DEPLOY_CONTRACT,

    /**
     * 风控配置
     */
    RISK_CONFIG,

    /**
     * 系统管理
     */
    SYSTEM_ADMIN
}
