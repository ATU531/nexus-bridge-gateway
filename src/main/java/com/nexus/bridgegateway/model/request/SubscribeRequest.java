package com.nexus.bridgegateway.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 事件订阅请求
 * 
 * 【功能说明】：
 * 用于订阅指定链上合约的事件监听。
 * 
 * 【字段说明】：
 * - chain: 链标识，如 eth, bsc, polygon
 * - contractAddress: 要监听的合约地址
 */
@Data
public class SubscribeRequest {

    /**
     * 链标识
     * 支持的链：eth, bsc, polygon, arb, op 等
     */
    @NotBlank(message = "链标识不能为空")
    private String chain;

    /**
     * 合约地址
     * 必须是有效的以太坊地址格式（0x 开头，40 位十六进制）
     */
    @NotBlank(message = "合约地址不能为空")
    @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "合约地址格式无效")
    private String contractAddress;
}
