package com.nexus.bridgegateway.core.query;

import com.nexus.bridgegateway.config.Web3Properties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web3j 多链路由注册中心
 * 负责管理多个区块链网络的 Web3j 客户端实例
 */
@Component
public class Web3jRegistry {

    private final Web3Properties web3Properties;

    /**
     * 链标识到 Web3j 实例的映射表
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private final Map<String, Web3j> web3jClients = new ConcurrentHashMap<>();

    /**
     * 链标识到原生代币符号的映射表
     */
    private final Map<String, String> chainSymbols = new ConcurrentHashMap<>();

    public Web3jRegistry(Web3Properties web3Properties) {
        this.web3Properties = web3Properties;
    }

    /**
     * Spring 初始化时执行
     * 遍历配置中的所有链，为每个链构建 Web3j 实例并缓存
     */
    @PostConstruct
    public void initialize() {
        Map<String, Web3Properties.ChainConfig> chains = web3Properties.getChains();
        if (chains != null) {
            chains.forEach((chainId, config) -> {
                // 构建 Web3j 实例
                HttpService httpService = new HttpService(config.getRpcUrl());
                Web3j web3j = Web3j.build(httpService);

                // 缓存 Web3j 实例和代币符号
                web3jClients.put(chainId.toLowerCase(), web3j);
                chainSymbols.put(chainId.toLowerCase(), config.getSymbol());

                System.out.println("[Web3jRegistry] 已注册链: " + chainId + " -> " + config.getRpcUrl());
            });
        }
    }

    /**
     * 获取指定链的 Web3j 客户端实例
     *
     * @param chain 链标识 (eth, bsc, polygon)
     * @return Web3j 实例
     * @throws UnsupportedChainException 如果该链未被支持
     */
    public Web3j getClient(String chain) {
        String chainKey = chain.toLowerCase();
        Web3j client = web3jClients.get(chainKey);

        if (client == null) {
            throw new UnsupportedChainException("不支持的链: " + chain + "。支持的链: " + web3jClients.keySet());
        }

        return client;
    }

    /**
     * 获取指定链的原生代币符号
     *
     * @param chain 链标识 (eth, bsc, polygon)
     * @return 代币符号 (ETH, BNB, MATIC)
     * @throws UnsupportedChainException 如果该链未被支持
     */
    public String getSymbol(String chain) {
        String chainKey = chain.toLowerCase();
        String symbol = chainSymbols.get(chainKey);

        if (symbol == null) {
            throw new UnsupportedChainException("不支持的链: " + chain + "。支持的链: " + chainSymbols.keySet());
        }

        return symbol;
    }

    /**
     * 检查是否支持指定的链
     *
     * @param chain 链标识
     * @return true 如果支持该链
     */
    public boolean isSupported(String chain) {
        return web3jClients.containsKey(chain.toLowerCase());
    }

    /**
     * 获取所有支持的链标识列表
     *
     * @return 链标识集合
     */
    public Map<String, Web3j> getAllClients() {
        return new ConcurrentHashMap<>(web3jClients);
    }
}
