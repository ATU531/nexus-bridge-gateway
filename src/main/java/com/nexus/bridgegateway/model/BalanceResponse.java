package com.nexus.bridgegateway.model;

/**
 * 余额响应模型
 * 支持多链查询结果返回
 *
 * @param address 钱包地址
 * @param chain   链标识 (eth, bsc, polygon)
 * @param symbol  原生代币符号 (ETH, BNB, MATIC)
 * @param balance 余额数值
 */
public record BalanceResponse(
        String address,
        String chain,
        String symbol,
        String balance
) {
}
