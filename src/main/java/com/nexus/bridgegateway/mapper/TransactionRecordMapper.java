package com.nexus.bridgegateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.bridgegateway.model.entity.TransactionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 链上交易流转记录数据访问层
 */
@Mapper
public interface TransactionRecordMapper extends BaseMapper<TransactionRecord> {

    /**
     * 根据交易哈希查询记录
     *
     * @param txHash 交易哈希
     * @return 交易记录
     */
    @Select("SELECT * FROM transaction_record WHERE tx_hash = #{txHash}")
    TransactionRecord selectByTxHash(@Param("txHash") String txHash);

    /**
     * 根据发送方地址查询交易记录
     *
     * @param fromAddress 发送方地址
     * @return 交易记录列表
     */
    @Select("SELECT * FROM transaction_record WHERE from_address = #{fromAddress} ORDER BY create_time DESC")
    List<TransactionRecord> selectByFromAddress(@Param("fromAddress") String fromAddress);

    /**
     * 根据状态查询交易记录
     *
     * @param status 交易状态
     * @return 交易记录列表
     */
    @Select("SELECT * FROM transaction_record WHERE status = #{status} ORDER BY create_time DESC")
    List<TransactionRecord> selectByStatus(@Param("status") String status);

    /**
     * 更新交易状态
     *
     * @param txHash  交易哈希
     * @param status  新状态
     * @param gasUsed 实际消耗的 Gas
     * @return 影响行数
     */
    @Update("UPDATE transaction_record SET status = #{status}, gas_used = #{gasUsed} WHERE tx_hash = #{txHash}")
    int updateStatusByTxHash(
            @Param("txHash") String txHash,
            @Param("status") String status,
            @Param("gasUsed") Long gasUsed);
}
