package com.nexus.bridgegateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.util.List;

@Configuration
public class Web3Config {

    @Bean
    @ConfigurationProperties(prefix = "web3")
    public Web3Properties web3Properties() {
        return new Web3Properties();
    }

    @Bean
    public Web3j web3j(Web3Properties properties) throws IOException {
        HttpService httpService = new HttpService(properties.getRpcUrl());
        return Web3j.build(httpService);
    }

    public static class Web3Properties {
        private String rpcUrl;
        private int timeout;
        private List<String> backupRpcUrls;

        public String getRpcUrl() {
            return rpcUrl;
        }

        public void setRpcUrl(String rpcUrl) {
            this.rpcUrl = rpcUrl;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public List<String> getBackupRpcUrls() {
            return backupRpcUrls;
        }

        public void setBackupRpcUrls(List<String> backupRpcUrls) {
            this.backupRpcUrls = backupRpcUrls;
        }
    }
}
