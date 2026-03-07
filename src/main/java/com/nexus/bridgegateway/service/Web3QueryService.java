package com.nexus.bridgegateway.service;

import com.nexus.bridgegateway.exception.GatewayRpcException;
import com.nexus.bridgegateway.model.BalanceResponse;
import com.nexus.bridgegateway.model.BlockResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.exceptions.MessageDecodingException;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.Executors;
import org.web3j.protocol.core.Response;

@Service
public class Web3QueryService {
    private static final Logger logger = LoggerFactory.getLogger(Web3QueryService.class);
    private static final String ETH_UNIT = "ETH";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final Web3j web3j;

    public Web3QueryService(Web3j web3j) {
        this.web3j = web3j;
    }

    public BalanceResponse getEtherBalance(String address) {
        logger.info("Querying ETH balance for address: {}", address);
        
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            attempt++;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                logger.debug("Attempt {}/{} to get balance for address: {}", attempt, MAX_RETRIES, address);
                var future = executor.submit(() -> {
                    logger.debug("Sending RPC request to get balance for address: {}", address);
                    EthGetBalance ethGetBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
                    if (ethGetBalance.hasError()) {
                        Response.Error error = ethGetBalance.getError();
                        logger.error("RPC Error {}: {}", error.getCode(), error.getMessage());
                        // 抛出自定义的网关异常，而不是让 Web3j 直接崩溃
                        throw new GatewayRpcException("上游 RPC 节点返回错误: " + error.getMessage(), error.getCode());
                    }
                    BigInteger balanceWei = ethGetBalance.getBalance();
                    logger.debug("Received balance in Wei: {}", balanceWei);
                    BigDecimal balanceEth = Convert.fromWei(new BigDecimal(balanceWei), Convert.Unit.ETHER);
                    logger.debug("Converted balance to ETH: {}", balanceEth);
                    return balanceEth.toPlainString();
                });
                
                String balance = future.get();
                logger.info("Successfully retrieved balance for address {}: {} ETH", address, balance);
                return new BalanceResponse(address, balance, ETH_UNIT);
            } catch (Exception e) {
                logger.warn("Attempt {}/{} failed for address {}: {}", attempt, MAX_RETRIES, address, e.getMessage());
                
                if (attempt >= MAX_RETRIES) {
                    logger.error("Failed to query balance for address: {} after {} attempts. Error: {}", address, MAX_RETRIES, e.getMessage(), e);
                    throw new RuntimeException("Failed to query balance from RPC node after " + MAX_RETRIES + " attempts: " + e.getMessage(), e);
                }
                
                if (e.getCause() instanceof MessageDecodingException) {
                    logger.warn("RPC node returned internal error, retrying...");
                }
                
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Query interrupted", ie);
                }
            }
        }
        
        throw new RuntimeException("Failed to query balance from RPC node");
    }

    public BlockResponse getLatestBlockNumber() {
        logger.info("Querying latest block number");
        
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            attempt++;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                logger.debug("Attempt {}/{} to get latest block number", attempt, MAX_RETRIES);
                var future = executor.submit(() -> {
                    EthBlockNumber ethBlockNumber = web3j.ethBlockNumber().send();
                    return ethBlockNumber.getBlockNumber().longValue();
                });
                
                Long blockNumber = future.get();
                logger.info("Successfully retrieved latest block number: {}", blockNumber);
                return new BlockResponse(blockNumber);
            } catch (Exception e) {
                logger.warn("Attempt {}/{} failed to get latest block number: {}", attempt, MAX_RETRIES, e.getMessage());
                
                if (attempt >= MAX_RETRIES) {
                    logger.error("Failed to query latest block number after {} attempts. Error: {}", MAX_RETRIES, e.getMessage(), e);
                    throw new RuntimeException("Failed to query latest block number from RPC node after " + MAX_RETRIES + " attempts: " + e.getMessage(), e);
                }
                
                if (e.getCause() instanceof MessageDecodingException) {
                    logger.warn("RPC node returned internal error, retrying...");
                }
                
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Query interrupted", ie);
                }
            }
        }
        
        throw new RuntimeException("Failed to query latest block number from RPC node");
    }
}
