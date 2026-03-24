package com.nexus.bridgegateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.bridgegateway.model.entity.UserIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户身份映射数据访问层
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {

    /**
     * 根据Web2用户ID查询钱包地址
     *
     * @param userId Web2系统用户ID
     * @return 钱包地址，未找到返回null
     */
    @Select("SELECT wallet_address FROM user_identity WHERE user_id = #{userId}")
    String selectWalletAddressByUserId(@Param("userId") String userId);

    /**
     * 根据钱包地址查询用户身份
     *
     * @param walletAddress 钱包地址
     * @return 用户身份实体，未找到返回null
     */
    @Select("SELECT * FROM user_identity WHERE wallet_address = #{walletAddress}")
    UserIdentity selectByWalletAddress(@Param("walletAddress") String walletAddress);

    /**
     * 根据手机号查询用户身份
     *
     * @param phoneNumber 手机号
     * @return 用户身份实体，未找到返回null
     */
    @Select("SELECT * FROM user_identity WHERE phone_number = #{phoneNumber}")
    UserIdentity selectByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * 根据邮箱查询用户身份
     *
     * @param email 邮箱
     * @return 用户身份实体，未找到返回null
     */
    @Select("SELECT * FROM user_identity WHERE email = #{email}")
    UserIdentity selectByEmail(@Param("email") String email);

    /**
     * 根据用户ID查询用户身份
     *
     * @param userId Web2系统用户ID
     * @return 用户身份实体，未找到返回null
     */
    @Select("SELECT * FROM user_identity WHERE user_id = #{userId}")
    UserIdentity selectByUserId(@Param("userId") String userId);

    /**
     * 根据手机号或邮箱查询用户身份
     * 
     * 【使用场景】：
     * Web2 登录时，用户可能使用手机号或邮箱登录，
     * 此方法统一查询两种情况。
     *
     * @param phoneOrEmail 手机号或邮箱
     * @return 用户身份实体，未找到返回null
     */
    @Select("SELECT * FROM user_identity WHERE phone_number = #{phoneOrEmail} OR email = #{phoneOrEmail}")
    UserIdentity selectByPhoneOrEmail(@Param("phoneOrEmail") String phoneOrEmail);
}
