package com.nexus.bridgegateway.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 链上交易流转记录实体
 * 对应数据库表: transaction_record
 */
@Data
@TableName("transaction_record")
public class TransactionRecord {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 交易哈希 (64位hex+0x前缀)
     */
    private String txHash;

    /**
     * 所属链标识 (如 eth, bsc, polygon)
     */
    private String chain;

    /**
     * 发送方钱包地址 (40位hex+0x前缀)
     */
    private String fromAddress;

    /**
     * 接收方钱包地址 (40位hex+0x前缀)
     */
    private String toAddress;

    /**
     * 交易金额 (保留18位小数, 兼容Wei/ETH)
     */
    private BigDecimal amount;

    /**
     * 交易Nonce (用于加速重发)
     */
    private Long txNonce;

    /**
     * 交易状态: PENDING, SUCCESS, FAILED
     */
    private String status;

    /**
     * 链上实际消耗的 Gas (成功或失败后回填)
     */
    private Long gasUsed;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;

    /**
     * 记录更新时间
     */
    private LocalDateTime updateTime;
}
