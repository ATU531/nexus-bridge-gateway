package com.nexus.bridgegateway.core.tx;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import com.nexus.bridgegateway.mapper.TransactionRecordMapper;
import com.nexus.bridgegateway.model.entity.TransactionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * 交易状态追踪服务
 * 
 * 【架构说明】：
 * 1. 使用 @Scheduled 定时轮询 PENDING 状态的交易
 * 2. 所有 Web3j 阻塞调用在 Java 21 虚拟线程中执行
 * 3. 单条记录查询失败不影响其他记录的处理
 * 
 * 【轮询流程】：
 * 1. 查询数据库中所有 status = 'PENDING' 的记录
 * 2. 遍历每条记录，查询链上交易凭证
 * 3. 根据凭证状态更新数据库记录
 * 
 * 【超时重发机制】（TODO 待实现）：
 * 当交易 PENDING 超过指定时间（如 3 分钟），且策略为 DYNAMIC 时，
 * 使用原 Nonce 并提高 20% Gas Price 重新签名广播。
 * 
 * 实现思路：
 * 1. 在 transaction_record 表中增加 gas_strategy 和 retry_count 字段
 * 2. 在查询 PENDING 记录时，同时检查 create_time 是否超时
 * 3. 对于超时的 DYNAMIC 策略交易：
 *    - 调用 GasStrategyService.calculateGasPrice(chain, DYNAMIC) 获取新 Gas Price
 *    - 将新 Gas Price 提高 20%
 *    - 使用原 Nonce 重新签名广播
 *    - 更新 retry_count++
 *    - 设置最大重试次数（如 3 次）避免无限重试
 * 4. 重发成功后，将原记录标记为 REPLACED，新建记录存储新 txHash
 */
@Slf4j
@Service
public class TransactionTrackerService {

    private final TransactionRecordMapper transactionRecordMapper;
    private final Web3jRegistry web3jRegistry;

    public TransactionTrackerService(
            TransactionRecordMapper transactionRecordMapper,
            Web3jRegistry web3jRegistry) {
        this.transactionRecordMapper = transactionRecordMapper;
        this.web3jRegistry = web3jRegistry;
    }

    /**
     * 定时追踪 PENDING 状态的交易
     * 
     * 【执行频率】：每 10 秒执行一次
     * 【执行方式】：在虚拟线程中执行，避免阻塞调度线程
     */
    @Scheduled(fixedDelay = 10000)
    public void trackPendingTransactions() {
        // 使用虚拟线程执行轮询任务
        Thread.ofVirtual().start(() -> {
            try {
                doTrackPendingTransactions();
            } catch (Exception e) {
                log.error("[TransactionTracker] 轮询任务执行异常: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 执行交易状态追踪的核心逻辑
     * 
     * 【核心流程】：
     * 1. 查询所有 PENDING 状态的记录
     * 2. 遍历并查询链上状态
     * 3. 更新数据库记录
     */
    private void doTrackPendingTransactions() {
        log.debug("[TransactionTracker] 开始轮询 PENDING 交易...");

        // 1. 查询所有 PENDING 状态的记录
        List<TransactionRecord> pendingRecords = transactionRecordMapper.selectByStatus("PENDING");

        if (pendingRecords == null || pendingRecords.isEmpty()) {
            log.debug("[TransactionTracker] 没有 PENDING 状态的交易");
            return;
        }

        log.info("[TransactionTracker] 发现 {} 条 PENDING 交易待追踪", pendingRecords.size());

        // 2. 遍历每条记录，查询链上状态
        for (TransactionRecord record : pendingRecords) {
            // 【容错】：单条记录查询失败不影响其他记录
            try {
                trackSingleTransaction(record);
            } catch (Exception e) {
                log.error("[TransactionTracker] 追踪交易失败, txHash={}, error={}", 
                        record.getTxHash(), e.getMessage());
                // 继续处理下一条记录
            }
        }

        log.debug("[TransactionTracker] 本轮轮询完成");
    }

    /**
     * 追踪单笔交易的状态
     * 
     * @param record 交易记录
     */
    private void trackSingleTransaction(TransactionRecord record) {
        String txHash = record.getTxHash();
        String chain = record.getChain();

        log.debug("[TransactionTracker] 追踪交易: txHash={}, chain={}", txHash, chain);

        // 获取对应链的 Web3j 客户端
        Web3j web3j = web3jRegistry.getClient(chain);

        // 查询链上交易凭证（阻塞调用，但在虚拟线程中执行）
        try {
            Optional<TransactionReceipt> receiptOptional = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();

            // 检查凭证是否存在
            if (!receiptOptional.isPresent()) {
                // 交易尚未被打包，保持 PENDING 状态
                log.debug("[TransactionTracker] 交易尚未被打包, txHash={}", txHash);
                return;
            }

            TransactionReceipt receipt = receiptOptional.get();

            // 判断交易状态
            String statusHex = receipt.getStatus();
            String newStatus;
            if ("0x1".equals(statusHex)) {
                newStatus = "SUCCESS";
                log.info("[TransactionTracker] 交易成功, txHash={}", txHash);
            } else {
                newStatus = "FAILED";
                log.warn("[TransactionTracker] 交易失败, txHash={}, status={}", txHash, statusHex);
            }

            // 提取 Gas Used
            BigInteger gasUsed = receipt.getGasUsed();
            log.debug("[TransactionTracker] Gas Used: {}", gasUsed);

            // 更新数据库记录
            record.setStatus(newStatus);
            record.setGasUsed(gasUsed.longValue());
            record.setUpdateTime(java.time.LocalDateTime.now());

            int updated = transactionRecordMapper.updateById(record);
            if (updated > 0) {
                log.info("[TransactionTracker] 交易状态已更新, txHash={}, status={}, gasUsed={}", 
                        txHash, newStatus, gasUsed);
            } else {
                log.warn("[TransactionTracker] 交易状态更新失败, txHash={}", txHash);
            }

        } catch (Exception e) {
            log.error("[TransactionTracker] 查询交易凭证异常, txHash={}, error={}", 
                    txHash, e.getMessage());
            throw new RuntimeException("Failed to track transaction: " + txHash, e);
        }
    }
}
