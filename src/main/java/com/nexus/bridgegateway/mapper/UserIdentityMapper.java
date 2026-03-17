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
}
