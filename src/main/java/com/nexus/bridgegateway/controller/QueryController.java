package com.nexus.bridgegateway.controller;

import com.nexus.bridgegateway.core.auth.filter.AuthGlobalFilter;
import com.nexus.bridgegateway.model.ApiResponse;
import com.nexus.bridgegateway.model.BalanceResponse;
import com.nexus.bridgegateway.model.BlockResponse;
import com.nexus.bridgegateway.service.Web3QueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * 多链查询控制器（内部服务）
 * 支持 ETH、BSC、Polygon 等多链数据查询
 *
 * 【架构规范】：此 Controller 为内部服务，仅通过网关路由转发访问
 * 外部请求路径：/api/v1/user/**  ->  网关路由  ->  内部路径：/internal/api/v1/user/**
 * 这样可以确保请求经过 AuthGlobalFilter 进行身份认证和钱包地址注入
 */
@RestController
@RequestMapping("/internal/api/v1/user")
public class QueryController {

    private final Web3QueryService web3QueryService;

    public QueryController(Web3QueryService web3QueryService) {
        this.web3QueryService = web3QueryService;
    }

    /**
     * 获取指定链上地址的原生代币余额（通过 X-User-ID 查询）
     * 只需要在 Header 中提供 X-User-ID，网关会自动查询绑定的钱包地址
     *
     * 【注意】：X-Nexus-Wallet-Address 请求头由 AuthGlobalFilter 根据 X-User-ID 自动注入
     *
     * @param chain                   链标识 (eth, bsc, polygon)
     * @param headerWalletAddress     请求头中的钱包地址（由 AuthGlobalFilter 根据 X-User-ID 注入）
     * @return 余额响应
     */
    @GetMapping("/{chain}/balance")
    public Mono<ApiResponse<BalanceResponse>> getNativeBalanceByUserId(
            @PathVariable String chain,
            @RequestHeader(value = AuthGlobalFilter.HEADER_WALLET_ADDRESS, required = false) String headerWalletAddress) {
        // 必须从请求头中获取钱包地址（由 AuthGlobalFilter 根据 X-User-ID 查询并设置）
        if (headerWalletAddress == null || headerWalletAddress.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing X-User-ID header or user has no bound wallet address"));
        }
        return web3QueryService.getNativeBalance(chain, headerWalletAddress)
                .map(ApiResponse::success);
    }

    /**
     * 获取指定链上地址的原生代币余额
     * 优先从请求头 X-Nexus-Wallet-Address 中读取钱包地址（由 AuthGlobalFilter 设置）
     *
     * 【注意】：如果提供了 X-User-ID，AuthGlobalFilter 会查询并注入绑定的钱包地址，
     * 优先级高于 URL 路径中的 address 参数
     *
     * @param chain                   链标识 (eth, bsc, polygon)
     * @param address                 URL路径中的钱包地址
     * @param headerWalletAddress     请求头中的钱包地址（可选，由认证过滤器注入）
     * @return 余额响应
     */
    @GetMapping("/{chain}/balance/{address}")
    public Mono<ApiResponse<BalanceResponse>> getNativeBalance(
            @PathVariable String chain,
            @PathVariable String address,
            @RequestHeader(value = AuthGlobalFilter.HEADER_WALLET_ADDRESS, required = false) String headerWalletAddress) {
        // 优先使用请求头中的钱包地址（由 AuthGlobalFilter 从 user_identity 表查询并设置）
        String walletAddress = (headerWalletAddress != null && !headerWalletAddress.isBlank())
                ? headerWalletAddress
                : address;
        return web3QueryService.getNativeBalance(chain, walletAddress)
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
