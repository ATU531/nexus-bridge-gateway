package com.nexus.bridgegateway.core.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控与熔断中心
 * 实时监控链上状态和网关健康度
 */
@Service
public class MonitorCenter {

    private final MeterRegistry meterRegistry;
    private final Web3j web3j;
    private final AtomicLong gasPriceGauge;
    private final AtomicLong nodeLatencyGauge;

    private final Counter requestCounter;
    private final Timer responseTimer;

    public MonitorCenter(MeterRegistry meterRegistry, Web3j web3j) {
        this.meterRegistry = meterRegistry;
        this.web3j = web3j;

        this.gasPriceGauge = new AtomicLong(0);
        this.nodeLatencyGauge = new AtomicLong(0);

        Gauge.builder("blockchain.gas.price", gasPriceGauge, AtomicLong::get)
            .description("Current Gas Price in Gwei")
            .register(meterRegistry);

        Gauge.builder("blockchain.node.latency", nodeLatencyGauge, AtomicLong::get)
            .description("RPC Node Latency in ms")
            .register(meterRegistry);

        this.requestCounter = Counter.builder("gateway.requests.total")
            .description("Total number of requests")
            .register(meterRegistry);

        this.responseTimer = Timer.builder("gateway.response.time")
            .description("Response time in milliseconds")
            .register(meterRegistry);
    }

    /**
     * 记录请求
     */
    public void recordRequest() {
        requestCounter.increment();
    }

    /**
     * 记录响应时间
     */
    public void recordResponseTime(long millis) {
        responseTimer.record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * 更新 Gas 价格指标
     */
    public void updateGasPrice(BigInteger gasPrice) {
        gasPriceGauge.set(gasPrice.longValue());
    }

    /**
     * 更新节点延迟指标
     */
    public void updateNodeLatency(long latencyMs) {
        nodeLatencyGauge.set(latencyMs);
    }

    /**
     * 获取当前 Gas 价格
     */
    public BigInteger getCurrentGasPrice() throws Exception {
        long startTime = System.currentTimeMillis();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        long latency = System.currentTimeMillis() - startTime;

        updateGasPrice(gasPrice);
        updateNodeLatency(latency);

        return gasPrice;
    }
}
