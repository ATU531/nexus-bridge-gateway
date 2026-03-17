package com.nexus.bridgegateway.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
     * Web2系统用户ID
     */
    private String userId;

    /**
     * 绑定的Web3钱包地址
     */
    private String walletAddress;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
