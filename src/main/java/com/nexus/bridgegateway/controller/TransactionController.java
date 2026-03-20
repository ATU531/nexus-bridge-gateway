package com.nexus.bridgegateway.controller;

import com.nexus.bridgegateway.core.tx.GasStrategyService;
import com.nexus.bridgegateway.core.tx.ReactiveNonceManager;
import com.nexus.bridgegateway.core.tx.ReactiveTransactionService;
import com.nexus.bridgegateway.model.enums.GasStrategyEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * 交易控制器
 * 
 * 提供交易发送接口
 * 
 * 【安全警告】：
 * - 私钥通过 HTTP 传输存在安全风险
 * - 生产环境应使用 HTTPS + 私钥加密存储
 * - 建议使用硬件钱包或 KMS 管理私钥
 * 
 * 【非托管模式】：
 * - 使用 /prepare 接口获取 Nonce 和 Gas 参数
 * - 用户在前端自行签名交易
 * - 使用 /broadcast 接口广播已签名交易
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tx")
@RequiredArgsConstructor
public class TransactionController {

    private final ReactiveTransactionService transactionService;
    private final ReactiveNonceManager nonceManager;
    private final GasStrategyService gasStrategyService;

    /**
     * 发送 ETH 转账交易
     * 
     * 【请求示例】：
     * POST /api/v1/tx/send
     * {
     *   "chain": "eth",
     *   "privateKey": "0x...",
     *   "toAddress": "0x...",
     *   "amount": "0.001"
     * }
     * 
     * @param request 交易请求
     * @return 交易哈希
     */
    @PostMapping("/send")
    public Mono<ResponseEntity<Map<String, Object>>> sendTransaction(
            @Valid @RequestBody TransactionRequest request) {

        log.info("[TransactionController] 收到交易请求, chain={}, to={}, amount={}",
                request.getChain(), request.getToAddress(), request.getAmount());

        return transactionService.sendEther(
                        request.getChain(),
                        request.getPrivateKey(),
                        request.getToAddress(),
                        request.getAmount()
                )
                .map(txHash -> {
                    log.info("[TransactionController] 交易发送成功, txHash={}", txHash);

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "Transaction sent successfully");
                    response.put("data", Map.of(
                            "txHash", txHash,
                            "chain", request.getChain(),
                            "from", "0x...",
                            "to", request.getToAddress(),
                            "amount", request.getAmount().toPlainString()
                    ));

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("[TransactionController] 交易发送失败: {}", error.getMessage());

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 500);
                    response.put("message", "Transaction failed: " + error.getMessage());
                    response.put("data", null);

                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 交易准备接口（非托管模式）
     * 
     * 【功能说明】：
     * 为前端提供组装交易所需的参数，包括 Nonce、Gas Price 和 Chain ID。
     * 前端拿到这些参数后，可以在本地签名交易，然后通过 /broadcast 接口广播。
     * 
     * 【请求示例】：
     * GET /api/v1/tx/prepare?chain=eth&fromAddress=0x...&strategy=STANDARD
     * 
     * @param chain       链标识 (eth, bsc, polygon)
     * @param fromAddress 发送方地址
     * @param strategy    Gas 策略（可选，默认 STANDARD）
     * @return 交易准备数据
     */
    @GetMapping("/prepare")
    public Mono<ResponseEntity<Map<String, Object>>> prepareTransaction(
            @RequestParam @NotBlank(message = "Chain is required")
            @Pattern(regexp = "^(eth|bsc|polygon)$", message = "Chain must be eth, bsc or polygon")
            String chain,
            @RequestParam @NotBlank(message = "From address is required")
            @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid Ethereum address format")
            String fromAddress,
            @RequestParam(required = false, defaultValue = "STANDARD") String strategy) {

        log.info("[TransactionController] [非托管] 收到交易准备请求, chain={}, from={}, strategy={}",
                chain, fromAddress, strategy);

        // 解析 Gas 策略（必须是 final 以便在 lambda 中使用）
        final GasStrategyEnum gasStrategy = parseGasStrategy(strategy);

        // 获取 Nonce
        Mono<BigInteger> nonceMono = nonceManager.allocateNonce(chain, fromAddress);

        // 获取 Gas Price
        Mono<BigInteger> gasPriceMono = Mono.fromCallable(() -> 
                gasStrategyService.calculateGasPrice(chain, gasStrategy));

        // 合并结果
        return Mono.zip(nonceMono, gasPriceMono)
                .map(tuple -> {
                    BigInteger nonce = tuple.getT1();
                    BigInteger gasPrice = tuple.getT2();

                    log.info("[TransactionController] [非托管] 交易准备完成, nonce={}, gasPrice={} wei",
                            nonce, gasPrice);

                    Map<String, Object> data = new HashMap<>();
                    data.put("nonce", nonce.toString());
                    data.put("gasPrice", gasPrice.toString());
                    data.put("gasPriceHex", "0x" + gasPrice.toString(16));
                    data.put("gasLimit", "21000");
                    data.put("chain", chain);
                    data.put("strategy", gasStrategy.name());
                    data.put("fromAddress", fromAddress);

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "Transaction prepared successfully");
                    response.put("data", data);

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("[TransactionController] [非托管] 交易准备失败: {}", error.getMessage());

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 500);
                    response.put("message", "Transaction preparation failed: " + error.getMessage());
                    response.put("data", null);

                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 广播已签名交易（非托管模式）
     * 
     * 【功能说明】：
     * 接收前端签名好的交易，广播到区块链网络。
     * 网关不接触用户私钥，安全性更高。
     * 
     * 【请求示例】：
     * POST /api/v1/tx/broadcast
     * {
     *   "chain": "eth",
     *   "signedTxHex": "0x..."
     * }
     * 
     * @param request 广播请求
     * @return 交易哈希
     */
    @PostMapping("/broadcast")
    public Mono<ResponseEntity<Map<String, Object>>> broadcastTransaction(
            @Valid @RequestBody BroadcastRequest request) {

        log.info("[TransactionController] [非托管] 收到广播请求, chain={}, hexLength={}",
                request.getChain(), request.getSignedTxHex().length());

        return transactionService.broadcastSignedTransaction(
                        request.getChain(),
                        request.getSignedTxHex()
                )
                .map(txHash -> {
                    log.info("[TransactionController] [非托管] 交易广播成功, txHash={}", txHash);

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "Transaction broadcast successfully");
                    response.put("data", Map.of(
                            "txHash", txHash,
                            "chain", request.getChain()
                    ));

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("[TransactionController] [非托管] 交易广播失败: {}", error.getMessage());

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 500);
                    response.put("message", "Transaction broadcast failed: " + error.getMessage());
                    response.put("data", null);

                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 加速交易（Speed Up）
     * 
     * 【功能说明】：
     * 对一笔 PENDING 状态的交易进行加速。使用原交易的 Nonce，提高 Gas Price 重新广播。
     * 这是 EVM 的 Transaction Replacement 机制，新交易会完全替换原交易。
     * 
     * 【请求示例】：
     * POST /api/v1/tx/speed-up
     * {
     *   "originalTxHash": "0x...",
     *   "privateKey": "0x..."
     * }
     * 
     * @param request 加速请求
     * @return 新交易哈希
     */
    @PostMapping("/speed-up")
    public Mono<ResponseEntity<Map<String, Object>>> speedUpTransaction(
            @Valid @RequestBody SpeedUpRequest request) {

        log.info("[TransactionController] [SpeedUp] 收到加速请求, originalTxHash={}", 
                request.getOriginalTxHash());

        return transactionService.speedUpTransaction(
                        request.getOriginalTxHash(),
                        request.getPrivateKey()
                )
                .map(newTxHash -> {
                    log.info("[TransactionController] [SpeedUp] 交易加速成功, newTxHash={}", newTxHash);

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "Transaction sped up successfully");
                    response.put("data", Map.of(
                            "originalTxHash", request.getOriginalTxHash(),
                            "newTxHash", newTxHash
                    ));

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("[TransactionController] [SpeedUp] 交易加速失败: {}", error.getMessage());

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 500);
                    response.put("message", "Transaction speed up failed: " + error.getMessage());
                    response.put("data", null);

                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 重试失败的交易（Retry）
     * 
     * 【功能说明】：
     * 对一笔 FAILED 状态的交易进行重试。申请全新的 Nonce，作为新交易重新广播。
     * 原 FAILED 记录保留作为历史审计。
     * 
     * 【与 SpeedUp 的本质区别】：
     * - SpeedUp：复用原 Nonce，覆盖原记录（PENDING → PENDING）
     * - Retry：申请新 Nonce，生成新记录（FAILED → 新 PENDING）
     * 
     * 【请求示例】：
     * POST /api/v1/tx/retry
     * {
     *   "failedTxHash": "0x...",
     *   "privateKey": "0x..."
     * }
     * 
     * @param request 重试请求
     * @return 新交易哈希
     */
    @PostMapping("/retry")
    public Mono<ResponseEntity<Map<String, Object>>> retryFailedTransaction(
            @Valid @RequestBody RetryRequest request) {

        log.info("[TransactionController] [Retry] 收到重试请求, failedTxHash={}", 
                request.getFailedTxHash());

        return transactionService.retryFailedTransaction(
                        request.getFailedTxHash(),
                        request.getPrivateKey()
                )
                .map(newTxHash -> {
                    log.info("[TransactionController] [Retry] 交易重试成功, newTxHash={}", newTxHash);

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "Transaction retried successfully");
                    response.put("data", Map.of(
                            "failedTxHash", request.getFailedTxHash(),
                            "newTxHash", newTxHash
                    ));

                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("[TransactionController] [Retry] 交易重试失败: {}", error.getMessage());

                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 500);
                    response.put("message", "Transaction retry failed: " + error.getMessage());
                    response.put("data", null);

                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 交易请求体
     */
    @Data
    public static class TransactionRequest {

        /**
         * 链标识 (eth, bsc, polygon)
         */
        @NotBlank(message = "Chain is required")
        @Pattern(regexp = "^(eth|bsc|polygon)$", message = "Chain must be eth, bsc or polygon")
        private String chain;

        /**
         * 发件人私钥
         * 格式：64 位十六进制字符串（可带 0x 前缀）
         */
        @NotBlank(message = "Private key is required")
        @Pattern(regexp = "^(0x)?[a-fA-F0-9]{64}$", message = "Invalid private key format")
        private String privateKey;

        /**
         * 收件人地址
         * 格式：以 0x 开头的 40 位十六进制字符串
         */
        @NotBlank(message = "To address is required")
        @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid Ethereum address format")
        private String toAddress;

        /**
         * 转账金额（ETH 单位）
         * 必须大于 0
         */
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.000000000000000001", message = "Amount must be greater than 0")
        private BigDecimal amount;
    }

    /**
     * 广播请求体（非托管模式）
     */
    @Data
    public static class BroadcastRequest {

        /**
         * 链标识 (eth, bsc, polygon)
         */
        @NotBlank(message = "Chain is required")
        @Pattern(regexp = "^(eth|bsc|polygon)$", message = "Chain must be eth, bsc or polygon")
        private String chain;

        /**
         * 已签名的交易十六进制字符串
         * 格式：以 0x 开头的十六进制字符串
         */
        @NotBlank(message = "Signed transaction hex is required")
        @Pattern(regexp = "^0x[a-fA-F0-9]+$", message = "Invalid signed transaction hex format")
        private String signedTxHex;
    }

    /**
     * 加速请求体
     */
    @Data
    public static class SpeedUpRequest {

        /**
         * 原交易哈希
         * 格式：以 0x 开头的 66 位十六进制字符串
         */
        @NotBlank(message = "Original transaction hash is required")
        @Pattern(regexp = "^0x[a-fA-F0-9]{64}$", message = "Invalid transaction hash format")
        private String originalTxHash;

        /**
         * 发件人私钥
         * 格式：64 位十六进制字符串（可带 0x 前缀）
         */
        @NotBlank(message = "Private key is required")
        @Pattern(regexp = "^(0x)?[a-fA-F0-9]{64}$", message = "Invalid private key format")
        private String privateKey;
    }

    /**
     * 重试请求体
     */
    @Data
    public static class RetryRequest {

        /**
         * 失败交易哈希
         * 格式：以 0x 开头的 66 位十六进制字符串
         */
        @NotBlank(message = "Failed transaction hash is required")
        @Pattern(regexp = "^0x[a-fA-F0-9]{64}$", message = "Invalid transaction hash format")
        private String failedTxHash;

        /**
         * 发件人私钥
         * 格式：64 位十六进制字符串（可带 0x 前缀）
         */
        @NotBlank(message = "Private key is required")
        @Pattern(regexp = "^(0x)?[a-fA-F0-9]{64}$", message = "Invalid private key format")
        private String privateKey;
    }

    /**
     * 解析 Gas 策略字符串
     * 
     * @param strategy 策略名称（可选）
     * @return Gas 策略枚举，无效时返回 STANDARD
     */
    private GasStrategyEnum parseGasStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return GasStrategyEnum.STANDARD;
        }
        try {
            return GasStrategyEnum.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[TransactionController] 无效的 Gas 策略: {}, 使用默认 STANDARD", strategy);
            return GasStrategyEnum.STANDARD;
        }
    }
}
