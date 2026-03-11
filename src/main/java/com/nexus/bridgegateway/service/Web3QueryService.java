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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.web3j.protocol.core.Response;

@Service
public class Web3QueryService {
    private static final Logger logger = LoggerFactory.getLogger(Web3QueryService.class);
    private static final String ETH_UNIT = "ETH";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final Web3j web3j;
    // 使用 Java 21 虚拟线程池
    private final Scheduler virtualThreadScheduler;

    public Web3QueryService(Web3j web3j) {
        this.web3j = web3j;
        this.virtualThreadScheduler = Schedulers.fromExecutor(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    public Mono<BalanceResponse> getEtherBalance(String address) {
        logger.info("Querying ETH balance for address: {}", address);
        
        return Mono.fromCallable(() -> {
            logger.debug("Sending RPC request to get balance for address: {}", address);
            EthGetBalance ethGetBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            if (ethGetBalance.hasError()) {
                Response.Error error = ethGetBalance.getError();
                logger.error("RPC Error {}: {}", error.getCode(), error.getMessage());
                throw new GatewayRpcException("上游 RPC 节点返回错误: " + error.getMessage(), error.getCode());
            }
            BigInteger balanceWei = ethGetBalance.getBalance();
            logger.debug("Received balance in Wei: {}", balanceWei);
            BigDecimal balanceEth = Convert.fromWei(new BigDecimal(balanceWei), Convert.Unit.ETHER);
            logger.debug("Converted balance to ETH: {}", balanceEth);
            return balanceEth.toPlainString();
        })
        .subscribeOn(virtualThreadScheduler)
        .map(balance -> {
            logger.info("Successfully retrieved balance for address {}: {} ETH", address, balance);
            return new BalanceResponse(address, balance, ETH_UNIT);
        })
        .retryWhen(reactor.util.retry.Retry.backoff(MAX_RETRIES, Duration.ofMillis(RETRY_DELAY_MS))
            .filter(throwable -> {
                if (throwable.getCause() instanceof MessageDecodingException) {
                    logger.warn("RPC node returned internal error, retrying...");
                    return true;
                }
                return true;
            })
            .doBeforeRetry(retrySignal -> 
                logger.warn("Retry attempt {}/{} for address {}", 
                    retrySignal.totalRetries() + 1, MAX_RETRIES, address)
            ))
        .onErrorMap(throwable -> {
            logger.error("Failed to query balance for address: {} after {} attempts. Error: {}", 
                address, MAX_RETRIES, throwable.getMessage(), throwable);
            return new RuntimeException("Failed to query balance from RPC node after " + MAX_RETRIES + " attempts: " + throwable.getMessage(), throwable);
        });
    }

    public Mono<BlockResponse> getLatestBlockNumber() {
        logger.info("Querying latest block number");
        
        return Mono.fromCallable(() -> {
            logger.debug("Sending RPC request to get latest block number");
            EthBlockNumber ethBlockNumber = web3j.ethBlockNumber().send();
            return ethBlockNumber.getBlockNumber().longValue();
        })
        .subscribeOn(virtualThreadScheduler)
        .map(blockNumber -> {
            logger.info("Successfully retrieved latest block number: {}", blockNumber);
            return new BlockResponse(blockNumber);
        })
        .retryWhen(reactor.util.retry.Retry.backoff(MAX_RETRIES, Duration.ofMillis(RETRY_DELAY_MS))
            .filter(throwable -> {
                if (throwable.getCause() instanceof MessageDecodingException) {
                    logger.warn("RPC node returned internal error, retrying...");
                    return true;
                }
                return true;
            })
            .doBeforeRetry(retrySignal -> 
                logger.warn("Retry attempt {}/{} for latest block number", 
                    retrySignal.totalRetries() + 1, MAX_RETRIES)
            ))
        .onErrorMap(throwable -> {
            logger.error("Failed to query latest block number after {} attempts. Error: {}", 
                MAX_RETRIES, throwable.getMessage(), throwable);
            return new RuntimeException("Failed to query latest block number from RPC node after " + MAX_RETRIES + " attempts: " + throwable.getMessage(), throwable);
        });
    }
}
