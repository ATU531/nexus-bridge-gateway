package com.nexus.bridgegateway.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Web2与Web3用户身份映射实体
 * 对应数据库表: user_identity
 */
@Data
@TableName("user_identity")
public class UserIdentity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Web2系统用户ID (如内部工号、UUID等)
     */
    @TableField("user_id")
    private String userId;

    /**
     * 手机号
     */
    @TableField("phone_number")
    private String phoneNumber;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 绑定的Web3钱包地址 (以太坊标准长度42位)
     */
    @TableField("wallet_address")
    private String walletAddress;

    /**
     * 加密存储的私钥(仅托管钱包有)
     */
    @TableField("private_key_cipher")
    private String privateKeyCipher;

    /**
     * 是否自托管(1:是, 0:否)
     */
    @TableField("self_custody")
    private Boolean selfCustody;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
