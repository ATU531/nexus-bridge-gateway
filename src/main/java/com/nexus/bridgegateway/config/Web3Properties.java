package com.nexus.bridgegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Web3 多链配置属性类
 * 映射 application.yml 中的 nexus.web3.chains 配置
 */
@Component
@ConfigurationProperties(prefix = "nexus.web3")
public class Web3Properties {

    /**
     * 链配置映射表
     * key: 链标识 (eth, bsc, polygon)
     * value: 链配置信息
     */
    private Map<String, ChainConfig> chains;

    public Map<String, ChainConfig> getChains() {
        return chains;
    }

    public void setChains(Map<String, ChainConfig> chains) {
        this.chains = chains;
    }

    /**
     * 单链配置信息
     */
    public static class ChainConfig {
        /**
         * RPC 节点 URL
         */
        private String rpcUrl;

        /**
         * 原生代币符号
         */
        private String symbol;

        public String getRpcUrl() {
            return rpcUrl;
        }

        public void setRpcUrl(String rpcUrl) {
            this.rpcUrl = rpcUrl;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }
    }
}
