package com.nexus.bridgegateway.core.query;

import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多链 Provider 管理
 * 支持 Ethereum、BSC、Polygon 等多链
 */
@Component
public class MultiChainProvider {

    private final Map<String, Web3j> providers = new ConcurrentHashMap<>();

    public void registerProvider(String chainId, Web3j web3j) {
        providers.put(chainId, web3j);
    }

    public Web3j getProvider(String chainId) {
        return providers.get(chainId);
    }

    public boolean hasProvider(String chainId) {
        return providers.containsKey(chainId);
    }
}
