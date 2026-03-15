package com.nexus.bridgegateway.controller;

import com.nexus.bridgegateway.model.ApiResponse;
import com.nexus.bridgegateway.model.BalanceResponse;
import com.nexus.bridgegateway.model.BlockResponse;
import com.nexus.bridgegateway.service.Web3QueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 多链查询控制器
 * 支持 ETH、BSC、Polygon 等多链数据查询
 */
@RestController
@RequestMapping("/v1/query")
public class QueryController {

    private final Web3QueryService web3QueryService;

    public QueryController(Web3QueryService web3QueryService) {
        this.web3QueryService = web3QueryService;
    }

    /**
     * 获取指定链上地址的原生代币余额
     *
     * @param chain   链标识 (eth, bsc, polygon)
     * @param address 钱包地址
     * @return 余额响应
     */
    @GetMapping("/{chain}/balance/{address}")
    public Mono<ApiResponse<BalanceResponse>> getNativeBalance(
            @PathVariable String chain,
            @PathVariable String address) {
        return web3QueryService.getNativeBalance(chain, address)
                .map(ApiResponse::success);
    }

    /**
     * 获取指定链的最新区块号
     *
     * @param chain 链标识 (eth, bsc, polygon)
     * @return 区块响应
     */
    @GetMapping("/{chain}/block/latest")
    public Mono<ApiResponse<BlockResponse>> getLatestBlockNumber(
            @PathVariable String chain) {
        return web3QueryService.getLatestBlockNumber(chain)
                .map(ApiResponse::success);
    }
}
