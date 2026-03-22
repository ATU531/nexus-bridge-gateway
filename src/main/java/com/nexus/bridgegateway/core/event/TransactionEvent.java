package com.nexus.bridgegateway.core.event;

import java.math.BigInteger;
import java.util.Map;

/**
 * 链上交易事件实体
 * 
 * 【架构定位】：
 * 作为 Web3 链上事件与 Web2 业务系统之间的数据载体，
 * 封装了从智能合约 Event 中提取的核心信息。
 * 
 * 【字段说明】：
 * - chain: 链标识（eth, bsc, polygon），用于多链场景区分
 * - contractAddress: 触发事件的合约地址
 * - transactionHash: 触发事件的交易哈希，用于交易追踪
 * - eventName: 事件名称（如 Transfer, Approval, Swap 等）
 * - eventData: 事件参数字典，键值对形式存储解析后的事件数据
 * - blockNumber: 区块号，用于确认事件所在区块
 */
public class TransactionEvent {

    /**
     * 链标识 (eth, bsc, polygon)
     */
    private String chain;

    /**
     * 触发事件的合约地址
     */
    private String contractAddress;

    /**
     * 触发事件的交易哈希
     */
    private String transactionHash;

    /**
     * 事件名称 (如 Transfer, Approval, Swap 等)
     */
    private String eventName;

    /**
     * 事件参数字典
     * 键值对形式存储解析后的事件数据
     * 例如：{"from": "0x...", "to": "0x...", "value": "1000000000000000000"}
     */
    private Map<String, Object> eventData;

    /**
     * 区块号
     */
    private BigInteger blockNumber;

    public TransactionEvent() {
    }

    public TransactionEvent(String chain, String contractAddress, String transactionHash,
                           String eventName, Map<String, Object> eventData, BigInteger blockNumber) {
        this.chain = chain;
        this.contractAddress = contractAddress;
        this.transactionHash = transactionHash;
        this.eventName = eventName;
        this.eventData = eventData;
        this.blockNumber = blockNumber;
    }

    public String getChain() {
        return chain;
    }

    public void setChain(String chain) {
        this.chain = chain;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public Map<String, Object> getEventData() {
        return eventData;
    }

    public void setEventData(Map<String, Object> eventData) {
        this.eventData = eventData;
    }

    public BigInteger getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(BigInteger blockNumber) {
        this.blockNumber = blockNumber;
    }

    @Override
    public String toString() {
        return "TransactionEvent{" +
                "chain='" + chain + '\'' +
                ", contractAddress='" + contractAddress + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", eventName='" + eventName + '\'' +
                ", eventData=" + eventData +
                ", blockNumber=" + blockNumber +
                '}';
    }
}
