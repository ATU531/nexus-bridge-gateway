package com.nexus.bridgegateway.controller;

import com.nexus.bridgegateway.core.event.ContractEventListenerService;
import com.nexus.bridgegateway.model.ApiResponse;
import com.nexus.bridgegateway.model.request.SubscribeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 事件监听控制器
 * 
 * 【架构定位】：
 * 提供事件订阅的 REST API 入口，作为 Web3 事件监听能力的外部暴露点。
 * 
 * 【核心功能】：
 * 1. 订阅合约事件
 * 2. 取消订阅
 * 3. 查询订阅状态
 * 
 * 【API 设计】：
 * - POST /api/v1/events/subscribe - 订阅合约事件
 * - DELETE /api/v1/events/subscribe - 取消订阅
 * - GET /api/v1/events/subscriptions - 查询活跃订阅数
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final ContractEventListenerService contractEventListenerService;

    /**
     * 订阅合约事件
     * 
     * 【功能说明】：
     * 订阅指定链上合约的所有事件，事件触发后将通过配置的通知器推送。
     * 
     * 【防重机制】：
     * 如果该合约已订阅，将跳过重复订阅，不会产生额外资源消耗。
     * 
     * @param request 订阅请求
     * @return 订阅结果
     */
    @PostMapping("/subscribe")
    public Mono<ApiResponse<String>> subscribe(@Valid @RequestBody SubscribeRequest request) {
        log.info("[EventController] 收到订阅请求, chain={}, contract={}", 
                request.getChain(), request.getContractAddress());

        // 检查是否已订阅
        if (contractEventListenerService.isSubscribed(request.getChain(), request.getContractAddress())) {
            log.info("[EventController] 合约已订阅, chain={}, contract={}", 
                    request.getChain(), request.getContractAddress());
            return Mono.just(ApiResponse.success(
                    String.format("合约已订阅，无需重复订阅。chain=%s, contract=%s", 
                            request.getChain(), request.getContractAddress())));
        }

        // 执行订阅
        contractEventListenerService.subscribeToContractEvent(
                        request.getChain(), 
                        request.getContractAddress())
                .subscribe(
                        event -> log.debug("[EventController] 收到事件: {}", event),
                        error -> log.error("[EventController] 订阅异常: {}", error.getMessage()),
                        () -> log.info("[EventController] 订阅完成")
                );

        return Mono.just(ApiResponse.success(
                String.format("订阅指令已接收并处理。chain=%s, contract=%s", 
                        request.getChain(), request.getContractAddress())));
    }

    /**
     * 取消订阅
     * 
     * @param chain           链标识
     * @param contractAddress 合约地址
     * @return 取消结果
     */
    @DeleteMapping("/subscribe")
    public Mono<ApiResponse<String>> unsubscribe(
            @RequestParam String chain,
            @RequestParam String contractAddress) {
        log.info("[EventController] 收到取消订阅请求, chain={}, contract={}", chain, contractAddress);

        contractEventListenerService.unsubscribe(chain, contractAddress);

        return Mono.just(ApiResponse.success(
                String.format("取消订阅指令已处理。chain=%s, contract=%s", chain, contractAddress)));
    }

    /**
     * 查询活跃订阅数量
     * 
     * @return 当前活跃的订阅数量
     */
    @GetMapping("/subscriptions/count")
    public Mono<ApiResponse<Integer>> getActiveSubscriptionCount() {
        int count = contractEventListenerService.getActiveSubscriptionCount();
        log.debug("[EventController] 查询活跃订阅数: {}", count);
        return Mono.just(ApiResponse.success(count));
    }

    /**
     * 检查订阅状态
     * 
     * @param chain           链标识
     * @param contractAddress 合约地址
     * @return 是否已订阅
     */
    @GetMapping("/subscriptions/status")
    public Mono<ApiResponse<Boolean>> checkSubscriptionStatus(
            @RequestParam String chain,
            @RequestParam String contractAddress) {
        boolean isSubscribed = contractEventListenerService.isSubscribed(chain, contractAddress);
        return Mono.just(ApiResponse.success(isSubscribed));
    }
}
