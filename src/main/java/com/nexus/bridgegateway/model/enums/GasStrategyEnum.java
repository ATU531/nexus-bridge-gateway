package com.nexus.bridgegateway.model.enums;

import java.math.BigDecimal;

/**
 * Gas 策略枚举
 * 
 * 【策略说明】：
 * 根据网络拥堵情况和用户需求，提供四种 Gas 价格计算策略。
 * 乘数越高，交易被打包的优先级越高，但成本也越高。
 * 
 * 【乘数原理】：
 * - 乘数基于网络建议的基础 Gas Price 进行调整
 * - 乘数越大，矿工优先打包的概率越高
 * - DYNAMIC 策略会根据实时网络状况智能调整
 */
public enum GasStrategyEnum {

    /**
     * 保守型策略
     * 
     * 【特点】：
     * - 使用网络建议的基础 Gas Price，不做任何加价
     * - 适合不紧急的交易，成本最低
     * - 在网络拥堵时可能需要较长时间才能被打包
     */
    CONSERVATIVE("保守型", new BigDecimal("1.0")),

    /**
     * 标准型策略（默认推荐）
     * 
     * 【特点】：
     * - 在基础 Gas Price 上增加 20%
     * - 平衡了成本和速度，适合大多数场景
     * - 通常能在合理时间内被打包
     */
    STANDARD("标准型", new BigDecimal("1.2")),

    /**
     * 激进型策略
     * 
     * 【特点】：
     * - 在基础 Gas Price 上增加 50%
     * - 适合紧急交易，优先级较高
     * - 成本较高，但能快速被打包
     */
    AGGRESSIVE("激进型", new BigDecimal("1.5")),

    /**
     * 动态调整策略
     * 
     * 【特点】：
     * - 根据实时网络拥堵情况智能计算 Gas Price
     * - 使用 EIP-1559 的 BaseFee 和 PriorityFee 机制
     * - 计算公式：BaseFee * 2 + PriorityFee
     * - 如果节点不支持 EIP-1559，降级为基础 Gas Price * 1.3
     * - 适合对打包时间有要求的交易
     */
    DYNAMIC("动态调整", null);

    private final String description;
    private final BigDecimal multiplier;

    GasStrategyEnum(String description, BigDecimal multiplier) {
        this.description = description;
        this.multiplier = multiplier;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    /**
     * 是否为动态策略
     * 
     * @return true 表示动态策略，需要特殊计算
     */
    public boolean isDynamic() {
        return this == DYNAMIC;
    }
}
