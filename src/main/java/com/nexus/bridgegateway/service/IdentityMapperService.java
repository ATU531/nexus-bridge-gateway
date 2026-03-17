package com.nexus.bridgegateway.service;

import com.nexus.bridgegateway.mapper.UserIdentityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * 身份映射服务
 * 负责 Web2 用户ID 与 Web3 钱包地址的映射查询
 *
 * 核心原则：所有涉及阻塞型 I/O (MyBatis-Plus) 的操作，
 * 必须使用虚拟线程执行，避免阻塞 Netty EventLoop
 */
@Slf4j
@Service
public class IdentityMapperService {

    private final UserIdentityMapper userIdentityMapper;

    public IdentityMapperService(UserIdentityMapper userIdentityMapper) {
        this.userIdentityMapper = userIdentityMapper;
    }

    /**
     * 根据 Web2 用户ID 获取绑定的 Web3 钱包地址
     *
     * 【核心底线】：使用虚拟线程执行阻塞型数据库查询
     *
     * @param userId Web2系统用户ID
     * @return CompletableFuture<String> 钱包地址，查不到返回空字符串
     */
    public CompletableFuture<String> getWalletAddressByUserId(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Querying wallet address for userId: {}", userId);
                String walletAddress = userIdentityMapper.selectWalletAddressByUserId(userId);

                if (walletAddress == null || walletAddress.isBlank()) {
                    log.warn("No wallet address found for userId: {}", userId);
                    return "";
                }

                log.debug("Found wallet address: {} for userId: {}", walletAddress, userId);
                return walletAddress;
            } catch (Exception e) {
                log.error("Failed to query wallet address for userId: {}", userId, e);
                throw new RuntimeException("Failed to query wallet address", e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
}
