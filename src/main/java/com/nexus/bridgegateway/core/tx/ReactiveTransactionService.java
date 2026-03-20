package com.nexus.bridgegateway.core.tx;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import com.nexus.bridgegateway.mapper.TransactionRecordMapper;
import com.nexus.bridgegateway.model.entity.TransactionRecord;
import com.nexus.bridgegateway.model.enums.GasStrategyEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;

/**
 * 响应式交易服务
 * 
 * 【架构说明】：
 * 1. 使用 WebFlux 响应式编程，保证高并发下的非阻塞特性
 * 2. 所有 Web3j 阻塞调用必须在 Java 21 虚拟线程中执行
 * 3. Nonce 由 ReactiveNonceManager 统一管理，保证原子性
 * 4. Gas 策略由 GasStrategyService 智能计算
 * 5. 交易记录自动落库，状态由 TransactionTrackerService 定时更新
 * 
 * 【交易流程】：
 * 1. 从私钥提取发件人地址
 * 2. 从 NonceManager 获取安全的 Nonce
 * 3. 在虚拟线程中执行：计算 Gas Price、组装交易、签名、广播
 * 4. 广播成功后立即落库，状态为 PENDING
 * 5. 失败时重置 Nonce
 */
@Slf4j
@Service
public class ReactiveTransactionService {

    /**
     * ETH 转账默认 Gas Limit
     * 普通 ETH 转账固定为 21000
     */
    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(21_000L);

    /**
     * 默认 Gas 策略
     */
    private static final GasStrategyEnum DEFAULT_GAS_STRATEGY = GasStrategyEnum.STANDARD;

    private final ReactiveNonceManager nonceManager;
    private final Web3jRegistry web3jRegistry;
    private final TransactionRecordMapper transactionRecordMapper;
    private final GasStrategyService gasStrategyService;

    /**
     * Java 21 虚拟线程调度器
     * 用于执行阻塞的 Web3j RPC 调用和数据库操作
     */
    private final Scheduler virtualThreadScheduler;

    public ReactiveTransactionService(
            ReactiveNonceManager nonceManager,
            Web3jRegistry web3jRegistry,
            TransactionRecordMapper transactionRecordMapper,
            GasStrategyService gasStrategyService) {
        this.nonceManager = nonceManager;
        this.web3jRegistry = web3jRegistry;
        this.transactionRecordMapper = transactionRecordMapper;
        this.gasStrategyService = gasStrategyService;
        this.virtualThreadScheduler = Schedulers.fromExecutor(
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    /**
     * 发送 ETH 转账交易（使用默认 Gas 策略：STANDARD）
     * 
     * @param chain       链标识 (eth, bsc, polygon)
     * @param privateKey  发件人私钥（明文，生产环境应使用加密存储）
     * @param toAddress   收件人地址
     * @param amountEth   转账金额（ETH 单位）
     * @return Mono<String> 交易哈希
     */
    public Mono<String> sendEther(
            String chain,
            String privateKey,
            String toAddress,
            BigDecimal amountEth) {
        return sendEther(chain, privateKey, toAddress, amountEth, DEFAULT_GAS_STRATEGY);
    }

    /**
     * 发送 ETH 转账交易（指定 Gas 策略）
     * 
     * 【完整流程】：
     * 1. 从私钥提取发件人地址
     * 2. 获取原子化 Nonce
     * 3. 在虚拟线程中执行所有阻塞操作：
     *    - 根据 Gas 策略计算 Gas Price
     *    - 金额单位转换
     *    - 构建原始交易
     *    - 本地签名
     *    - 广播到链上
     *    - 落库记录
     * 4. 失败时重置 Nonce
     * 
     * @param chain       链标识 (eth, bsc, polygon)
     * @param privateKey  发件人私钥（明文，生产环境应使用加密存储）
     * @param toAddress   收件人地址
     * @param amountEth   转账金额（ETH 单位）
     * @param gasStrategy Gas 策略（CONSERVATIVE, STANDARD, AGGRESSIVE, DYNAMIC）
     * @return Mono<String> 交易哈希
     */
    public Mono<String> sendEther(
            String chain,
            String privateKey,
            String toAddress,
            BigDecimal amountEth,
            GasStrategyEnum gasStrategy) {

        log.info("[TransactionService] 开始发送交易, chain={}, to={}, amount={} ETH, strategy={}", 
                chain, toAddress, amountEth, gasStrategy);

        // 步骤 A：从私钥提取发件人地址
        Credentials credentials;
        try {
            credentials = Credentials.create(privateKey);
        } catch (Exception e) {
            log.error("[TransactionService] 私钥格式无效");
            return Mono.error(new IllegalArgumentException("Invalid private key"));
        }
        String fromAddress = credentials.getAddress();
        log.info("[TransactionService] 发件人地址: {}", fromAddress);

        // 步骤 B：获取原子化 Nonce
        return nonceManager.allocateNonce(chain, fromAddress)
                .flatMap(nonce -> {
                    log.info("[TransactionService] 分配到 Nonce: {}", nonce);

                    // 步骤 C：在虚拟线程中执行所有阻塞操作
                    return executeTransactionInVirtualThread(chain, credentials, toAddress, amountEth, nonce, gasStrategy)
                            // 失败时重置 Nonce
                            .onErrorResume(error -> {
                                log.error("[TransactionService] 交易失败，重置 Nonce: {}", nonce);
                                return nonceManager.resetNonce(chain, fromAddress)
                                        .then(Mono.error(error));
                            });
                })
                .doOnSuccess(txHash -> log.info("[TransactionService] 交易广播成功, txHash={}", txHash))
                .doOnError(error -> log.error("[TransactionService] 交易失败: {}", error.getMessage()));
    }

    /**
     * 在虚拟线程中执行交易签名与广播
     * 
     * 【关键】：所有 Web3j 阻塞调用和数据库操作必须在此方法内执行
     * 因为当前代码已经在虚拟线程中，MyBatis-Plus 的 insert 可以直接安全调用
     * 
     * @param chain       链标识
     * @param credentials 凭证（包含私钥和地址）
     * @param toAddress   收件人地址
     * @param amountEth   转账金额（ETH）
     * @param nonce       交易 Nonce
     * @param gasStrategy Gas 策略
     * @return Mono<String> 交易哈希
     */
    private Mono<String> executeTransactionInVirtualThread(
            String chain,
            Credentials credentials,
            String toAddress,
            BigDecimal amountEth,
            BigInteger nonce,
            GasStrategyEnum gasStrategy) {

        return Mono.fromCallable(() -> {
            Web3j web3j = web3jRegistry.getClient(chain);
            String fromAddress = credentials.getAddress();

            log.debug("[TransactionService] [虚拟线程] 开始执行交易签名与广播");

            // 1. 【升级】根据 Gas 策略计算 Gas Price
            // 使用 GasStrategyService 替换原来写死的 ethGasPrice 查询
            log.debug("[TransactionService] [虚拟线程] 根据 {} 策略计算 Gas Price...", gasStrategy);
            BigInteger gasPrice = gasStrategyService.calculateGasPrice(chain, gasStrategy);
            log.debug("[TransactionService] [虚拟线程] 计算得到 Gas Price: {} wei", gasPrice);

            // 2. 将金额从 ETH 转换为 Wei
            BigInteger valueWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger();
            log.debug("[TransactionService] [虚拟线程] 转账金额: {} wei", valueWei);

            // 3. 构建原始交易对象 (兼容 EIP-155)
            log.debug("[TransactionService] [虚拟线程] 构建原始交易...");
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                    nonce,
                    gasPrice,
                    DEFAULT_GAS_LIMIT,
                    toAddress,
                    valueWei
            );

            // 4. 使用私钥对交易进行本地签名 (带上 Chain ID)
            log.debug("[TransactionService] [虚拟线程] 签名交易...");
            BigInteger chainId = web3j.ethChainId().send().getChainId();
            log.debug("[TransactionService] [虚拟线程] 当前网络 Chain ID: {}", chainId);
            
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId.longValue(), credentials);
            String hexValue = Numeric.toHexString(signedMessage);
            log.debug("[TransactionService] [虚拟线程] 签名完成，hex 长度: {} 字符", hexValue.length());

            // 5. 将签名后的交易广播到链上
            log.debug("[TransactionService] [虚拟线程] 广播交易到链上...");
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

            // 6. 检查广播结果
            if (ethSendTransaction.hasError()) {
                String errorMsg = ethSendTransaction.getError().getMessage();
                int errorCode = ethSendTransaction.getError().getCode();
                log.error("[TransactionService] [虚拟线程] 交易广播失败, code={}, message={}", errorCode, errorMsg);
                throw new TransactionBroadcastException(errorCode, errorMsg);
            }

            String txHash = ethSendTransaction.getTransactionHash();
            log.info("[TransactionService] [虚拟线程] 交易广播成功, txHash={}", txHash);

            // 7. 交易落库：将交易记录存入数据库
            // 因为当前代码已在虚拟线程中执行，MyBatis-Plus 的 insert 可以直接调用
            log.debug("[TransactionService] [虚拟线程] 开始交易落库...");
            TransactionRecord record = new TransactionRecord();
            record.setTxHash(txHash);
            record.setChain(chain);
            record.setFromAddress(fromAddress);
            record.setToAddress(toAddress);
            record.setAmount(amountEth);
            record.setTxNonce(nonce.longValue());
            record.setStatus("PENDING");
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            
            transactionRecordMapper.insert(record);
            log.info("[TransactionService] [虚拟线程] 交易记录已落库, txHash={}, nonce={}, status=PENDING, gasStrategy={}", 
                    txHash, nonce, gasStrategy);

            return txHash;

        })
        // 【关键】：在虚拟线程上执行所有阻塞操作
        .subscribeOn(virtualThreadScheduler);
    }

    /**
     * 广播已签名的交易（非托管模式）
     * 
     * 【适用场景】：
     * - 用户在前端自行签名交易，网关只负责广播
     * - 网关不接触用户私钥，安全性更高
     * 
     * 【核心流程】：
     * 1. 解码签名交易，提取关键信息
     * 2. 广播到链上
     * 3. 落库追踪
     * 
     * @param chain       链标识 (eth, bsc, polygon)
     * @param signedTxHex 已签名的交易十六进制字符串
     * @return Mono<String> 交易哈希
     */
    public Mono<String> broadcastSignedTransaction(String chain, String signedTxHex) {
        log.info("[TransactionService] [非托管] 开始广播已签名交易, chain={}, hexLength={}", 
                chain, signedTxHex.length());

        return Mono.fromCallable(() -> {
            Web3j web3j = web3jRegistry.getClient(chain);

            // 1. 解码签名交易
            log.debug("[TransactionService] [非托管] [虚拟线程] 解码签名交易...");
            org.web3j.crypto.SignedRawTransaction signedTx;
            try {
                signedTx = (org.web3j.crypto.SignedRawTransaction) TransactionDecoder.decode(signedTxHex);
            } catch (Exception e) {
                log.error("[TransactionService] [非托管] 交易解码失败: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid signed transaction hex: " + e.getMessage());
            }

            // 2. 提取交易信息
            String fromAddress = signedTx.getFrom();
            String toAddress = signedTx.getTo();
            BigInteger valueWei = signedTx.getValue();
            BigInteger nonce = signedTx.getNonce();
            BigInteger gasPrice = signedTx.getGasPrice();
            BigInteger gasLimit = signedTx.getGasLimit();

            // 将 Wei 转换为 ETH
            BigDecimal amountEth = Convert.fromWei(new BigDecimal(valueWei), Convert.Unit.ETHER);

            log.info("[TransactionService] [非托管] 交易信息: from={}, to={}, nonce={}, value={} ETH",
                    fromAddress, toAddress, nonce, amountEth);

            // 3. 计算交易哈希（对签名交易数据进行 Keccak-256 哈希）
            byte[] txBytes = Numeric.hexStringToByteArray(signedTxHex);
            String txHash = Numeric.toHexString(Hash.sha3(txBytes));
            log.debug("[TransactionService] [非托管] 计算得到 txHash: {}", txHash);

            // 4. 广播到链上
            log.debug("[TransactionService] [非托管] [虚拟线程] 广播交易到链上...");
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(signedTxHex).send();

            // 5. 检查广播结果
            if (ethSendTransaction.hasError()) {
                String errorMsg = ethSendTransaction.getError().getMessage();
                int errorCode = ethSendTransaction.getError().getCode();
                log.error("[TransactionService] [非托管] 交易广播失败, code={}, message={}", errorCode, errorMsg);
                throw new TransactionBroadcastException(errorCode, errorMsg);
            }

            // 验证返回的哈希与计算的一致
            String returnedHash = ethSendTransaction.getTransactionHash();
            if (!txHash.equals(returnedHash)) {
                log.warn("[TransactionService] [非托管] 哈希不一致: 计算值={}, 返回值={}", txHash, returnedHash);
                txHash = returnedHash;
            }

            log.info("[TransactionService] [非托管] 交易广播成功, txHash={}", txHash);

            // 6. 交易落库
            log.debug("[TransactionService] [非托管] [虚拟线程] 开始交易落库...");
            TransactionRecord record = new TransactionRecord();
            record.setTxHash(txHash);
            record.setChain(chain);
            record.setFromAddress(fromAddress);
            record.setToAddress(toAddress);
            record.setAmount(amountEth);
            record.setTxNonce(nonce.longValue());
            record.setStatus("PENDING");
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());

            transactionRecordMapper.insert(record);
            log.info("[TransactionService] [非托管] 交易记录已落库, txHash={}, nonce={}, status=PENDING", txHash, nonce);

            return txHash;

        })
        .subscribeOn(virtualThreadScheduler);
    }

    /**
     * 加速交易（Speed Up / Transaction Replacement）
     * 
     * 【EVM Transaction Replacement 机制】：
     * 在 EVM 架构中，加速一笔 PENDING 的交易，必须满足以下条件：
     * 1. 使用原交易的 Nonce（这是关键！）
     * 2. 新交易的 Gas Price 必须比原交易高至少 10%（有些节点要求 12.5%）
     * 3. 新交易会完全替换原交易（原交易永远不会被打包）
     * 
     * 【适用场景】：
     * - 原交易 Gas Price 过低，长时间未被矿工打包
     * - 网络拥堵加剧，需要提高 Gas Price 加速交易
     * 
     * @param originalTxHash 原交易哈希
     * @param privateKey     发件人私钥（用于重新签名）
     * @return Mono<String> 新交易哈希
     */
    public Mono<String> speedUpTransaction(String originalTxHash, String privateKey) {
        log.info("[TransactionService] [SpeedUp] 开始加速交易, originalTxHash={}", originalTxHash);

        return Mono.fromCallable(() -> {
            // 1. 查询原交易记录
            log.debug("[TransactionService] [SpeedUp] [虚拟线程] 查询原交易记录...");
            TransactionRecord originalRecord = transactionRecordMapper.selectByTxHash(originalTxHash);
            if (originalRecord == null) {
                throw new IllegalArgumentException("Transaction not found: " + originalTxHash);
            }

            // 2. 验证交易状态必须是 PENDING
            if (!"PENDING".equals(originalRecord.getStatus())) {
                throw new IllegalArgumentException(
                        "Only PENDING transactions can be sped up, current status: " + originalRecord.getStatus());
            }

            // 3. 验证 txNonce 是否存在
            Long txNonce = originalRecord.getTxNonce();
            if (txNonce == null) {
                throw new IllegalArgumentException("Original transaction has no nonce recorded, cannot speed up");
            }

            String chain = originalRecord.getChain();
            String fromAddress = originalRecord.getFromAddress();
            String toAddress = originalRecord.getToAddress();
            BigDecimal amountEth = originalRecord.getAmount();

            log.info("[TransactionService] [SpeedUp] 原交易信息: chain={}, from={}, to={}, nonce={}, amount={} ETH",
                    chain, fromAddress, toAddress, txNonce, amountEth);

            // 4. 验证私钥对应的地址是否与原交易发送方一致
            Credentials credentials;
            try {
                credentials = Credentials.create(privateKey);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid private key");
            }
            String signerAddress = credentials.getAddress();
            if (!signerAddress.equalsIgnoreCase(fromAddress)) {
                throw new IllegalArgumentException(
                        "Private key does not match original sender. Expected: " + fromAddress + ", Got: " + signerAddress);
            }

            // 5. 获取 Web3j 客户端
            Web3j web3j = web3jRegistry.getClient(chain);

            // 6. 【关键】使用 AGGRESSIVE 策略获取更高的 Gas Price
            // AGGRESSIVE 策略会在基础 Gas Price 上增加 50%，确保比原交易高
            log.debug("[TransactionService] [SpeedUp] [虚拟线程] 获取激进档位 Gas Price...");
            BigInteger newGasPrice = gasStrategyService.calculateGasPrice(chain, GasStrategyEnum.AGGRESSIVE);
            log.info("[TransactionService] [SpeedUp] 新 Gas Price: {} wei", newGasPrice);

            // 7. 将金额从 ETH 转换为 Wei
            BigInteger valueWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger();

            // 8. 【EVM Transaction Replacement】使用原 Nonce 重新构建交易
            // 关键点：Nonce 必须与原交易完全相同，否则会被视为新交易而非替换
            log.debug("[TransactionService] [SpeedUp] [虚拟线程] 构建替换交易 (使用原 Nonce: {})...", txNonce);
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                    BigInteger.valueOf(txNonce),  // 【关键】使用原交易的 Nonce
                    newGasPrice,                   // 新的更高 Gas Price
                    DEFAULT_GAS_LIMIT,
                    toAddress,
                    valueWei
            );

            // 9. 使用私钥对交易进行签名 (带上 Chain ID)
            log.debug("[TransactionService] [SpeedUp] [虚拟线程] 签名替换交易...");
            BigInteger chainId = web3j.ethChainId().send().getChainId();
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId.longValue(), credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            // 10. 广播新交易到链上
            log.debug("[TransactionService] [SpeedUp] [虚拟线程] 广播替换交易...");
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

            // 11. 检查广播结果
            if (ethSendTransaction.hasError()) {
                String errorMsg = ethSendTransaction.getError().getMessage();
                int errorCode = ethSendTransaction.getError().getCode();
                log.error("[TransactionService] [SpeedUp] 替换交易广播失败, code={}, message={}", errorCode, errorMsg);
                throw new TransactionBroadcastException(errorCode, errorMsg);
            }

            String newTxHash = ethSendTransaction.getTransactionHash();
            log.info("[TransactionService] [SpeedUp] 替换交易广播成功, newTxHash={}", newTxHash);

            // 12. 更新数据库记录：将原记录的 txHash 更新为新哈希
            // 注意：状态保持 PENDING，由 TransactionTrackerService 继续追踪
            log.debug("[TransactionService] [SpeedUp] [虚拟线程] 更新数据库记录...");
            originalRecord.setTxHash(newTxHash);
            originalRecord.setUpdateTime(LocalDateTime.now());
            
            int updated = transactionRecordMapper.updateById(originalRecord);
            if (updated > 0) {
                log.info("[TransactionService] [SpeedUp] 数据库记录已更新, oldTxHash={}, newTxHash={}, nonce={}, status=PENDING",
                        originalTxHash, newTxHash, txNonce);
            } else {
                log.warn("[TransactionService] [SpeedUp] 数据库记录更新失败, newTxHash={}", newTxHash);
            }

            return newTxHash;

        })
        .subscribeOn(virtualThreadScheduler);
    }

    /**
     * 重试失败的交易（Retry Failed Transaction）
     * 
     * 【EVM 失败交易重试机制】：
     * 在 EVM 中，FAILED 状态的交易 Nonce 已被消耗，重试必须：
     * 1. 申请一个全新的 Nonce（不能复用原 Nonce！）
     * 2. 作为一笔全新的交易重新广播
     * 3. 生成新的数据库记录（旧 FAILED 记录作为历史审计保留）
     * 
     * 【与 SpeedUp 的本质区别】：
     * ┌─────────────┬────────────────────┬────────────────────┐
     * │    特性     │      SpeedUp       │       Retry        │
     * ├─────────────┼────────────────────┼────────────────────┤
     * │ 原交易状态  │      PENDING       │       FAILED       │
     * │ Nonce 处理  │    复用原 Nonce    │   申请新 Nonce     │
     * │ 数据库操作  │   覆盖原记录哈希   │  生成新 PENDING 记录│
     * │ 历史记录    │      被替换        │    保留作为审计    │
     * └─────────────┴────────────────────┴────────────────────┘
     * 
     * 【适用场景】：
     * - 交易因 Gas 不足、合约执行失败等原因 FAILED
     * - 需要重新发起相同参数的交易
     * 
     * @param failedTxHash 失败交易哈希
     * @param privateKey   发件人私钥（用于重新签名）
     * @return Mono<String> 新交易哈希
     */
    public Mono<String> retryFailedTransaction(String failedTxHash, String privateKey) {
        log.info("[TransactionService] [Retry] 开始重试失败交易, failedTxHash={}", failedTxHash);

        // 步骤 1：查询原交易记录并验证状态
        return Mono.fromCallable(() -> {
                    log.debug("[TransactionService] [Retry] [虚拟线程] 查询原交易记录...");
                    TransactionRecord failedRecord = transactionRecordMapper.selectByTxHash(failedTxHash);
                    if (failedRecord == null) {
                        throw new IllegalArgumentException("Transaction not found: " + failedTxHash);
                    }

                    // 验证交易状态必须是 FAILED
                    if (!"FAILED".equals(failedRecord.getStatus())) {
                        throw new IllegalArgumentException(
                                "Only FAILED transactions can be retried, current status: " + failedRecord.getStatus());
                    }

                    return failedRecord;
                })
                .subscribeOn(virtualThreadScheduler)
                // 步骤 2：验证私钥并提取发件人地址
                .flatMap(failedRecord -> {
                    Credentials credentials;
                    try {
                        credentials = Credentials.create(privateKey);
                    } catch (Exception e) {
                        return Mono.error(new IllegalArgumentException("Invalid private key"));
                    }
                    String signerAddress = credentials.getAddress();
                    if (!signerAddress.equalsIgnoreCase(failedRecord.getFromAddress())) {
                        return Mono.error(new IllegalArgumentException(
                                "Private key does not match original sender. Expected: " + failedRecord.getFromAddress() 
                                + ", Got: " + signerAddress));
                    }

                    log.info("[TransactionService] [Retry] 原交易信息: chain={}, from={}, to={}, amount={} ETH",
                            failedRecord.getChain(), failedRecord.getFromAddress(), 
                            failedRecord.getToAddress(), failedRecord.getAmount());

                    // 步骤 3：【关键】申请新的 Nonce（不能复用原 Nonce！）
                    // 因为 FAILED 交易的 Nonce 已被链上消耗
                    return nonceManager.allocateNonce(failedRecord.getChain(), failedRecord.getFromAddress())
                            .flatMap(newNonce -> {
                                log.info("[TransactionService] [Retry] 分配到新 Nonce: {} (原 Nonce: {})", 
                                        newNonce, failedRecord.getTxNonce());

                                // 步骤 4：在虚拟线程中执行交易构建、签名、广播
                                return executeRetryTransaction(failedRecord, credentials, newNonce, privateKey);
                            });
                });
    }

    /**
     * 执行重试交易的核心逻辑（在虚拟线程中运行）
     */
    private Mono<String> executeRetryTransaction(
            TransactionRecord failedRecord,
            Credentials credentials,
            BigInteger newNonce,
            String privateKey) {

        return Mono.fromCallable(() -> {
            String chain = failedRecord.getChain();
            String toAddress = failedRecord.getToAddress();
            BigDecimal amountEth = failedRecord.getAmount();

            // 获取 Web3j 客户端
            Web3j web3j = web3jRegistry.getClient(chain);

            // 使用 STANDARD 策略计算 Gas Price
            log.debug("[TransactionService] [Retry] [虚拟线程] 计算 Gas Price...");
            BigInteger gasPrice = gasStrategyService.calculateGasPrice(chain, GasStrategyEnum.STANDARD);
            log.info("[TransactionService] [Retry] Gas Price: {} wei", gasPrice);

            // 将金额从 ETH 转换为 Wei
            BigInteger valueWei = Convert.toWei(amountEth, Convert.Unit.ETHER).toBigInteger();

            // 构建新交易（使用新分配的 Nonce）
            log.debug("[TransactionService] [Retry] [虚拟线程] 构建新交易 (新 Nonce: {})...", newNonce);
            RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                    newNonce,           // 【关键】使用新分配的 Nonce
                    gasPrice,
                    DEFAULT_GAS_LIMIT,
                    toAddress,
                    valueWei
            );

            // 签名交易
            log.debug("[TransactionService] [Retry] [虚拟线程] 签名新交易...");
            BigInteger chainId = web3j.ethChainId().send().getChainId();
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId.longValue(), credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            // 广播新交易
            log.debug("[TransactionService] [Retry] [虚拟线程] 广播新交易...");
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

            // 检查广播结果
            if (ethSendTransaction.hasError()) {
                String errorMsg = ethSendTransaction.getError().getMessage();
                int errorCode = ethSendTransaction.getError().getCode();
                log.error("[TransactionService] [Retry] 新交易广播失败, code={}, message={}", errorCode, errorMsg);
                throw new TransactionBroadcastException(errorCode, errorMsg);
            }

            String newTxHash = ethSendTransaction.getTransactionHash();
            log.info("[TransactionService] [Retry] 新交易广播成功, newTxHash={}", newTxHash);

            // 【关键】创建新的数据库记录（不覆盖原 FAILED 记录）
            // 原 FAILED 记录作为历史审计保留
            log.debug("[TransactionService] [Retry] [虚拟线程] 创建新数据库记录...");
            TransactionRecord newRecord = new TransactionRecord();
            newRecord.setTxHash(newTxHash);
            newRecord.setChain(chain);
            newRecord.setFromAddress(failedRecord.getFromAddress());
            newRecord.setToAddress(toAddress);
            newRecord.setAmount(amountEth);
            newRecord.setTxNonce(newNonce.longValue());
            newRecord.setStatus("PENDING");
            newRecord.setCreateTime(LocalDateTime.now());
            newRecord.setUpdateTime(LocalDateTime.now());

            transactionRecordMapper.insert(newRecord);
            log.info("[TransactionService] [Retry] 新交易记录已落库, newTxHash={}, newNonce={}, status=PENDING, " +
                            "原失败交易: failedTxHash={}, oldNonce={}",
                    newTxHash, newNonce, failedRecord.getTxHash(), failedRecord.getTxNonce());

            return newTxHash;

        })
        .subscribeOn(virtualThreadScheduler)
        // 失败时重置新分配的 Nonce
        .onErrorResume(error -> {
            log.error("[TransactionService] [Retry] 重试失败，重置新 Nonce: {}", newNonce);
            return nonceManager.resetNonce(failedRecord.getChain(), failedRecord.getFromAddress())
                    .then(Mono.error(error));
        });
    }

    /**
     * 交易广播异常
     * 用于封装链上返回的错误信息
     */
    public static class TransactionBroadcastException extends RuntimeException {
        private final int code;

        public TransactionBroadcastException(int code, String message) {
            super("Transaction broadcast failed [" + code + "]: " + message);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
