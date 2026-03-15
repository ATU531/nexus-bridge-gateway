package com.nexus.bridgegateway.core.monitor;

import com.nexus.bridgegateway.core.query.Web3jRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控与熔断中心
 * 实时监控链上状态和网关健康度
 */
@Service
public class MonitorCenter {

    private final MeterRegistry meterRegistry;
    private final Web3jRegistry web3jRegistry;

    // 多链 Gas 价格指标
    private final Map<String, AtomicLong> gasPriceGauges = new ConcurrentHashMap<>();
    // 多链节点延迟指标
    private final Map<String, AtomicLong> nodeLatencyGauges = new ConcurrentHashMap<>();

    private final Counter requestCounter;
    private final Timer responseTimer;

    public MonitorCenter(MeterRegistry meterRegistry, Web3jRegistry web3jRegistry) {
        this.meterRegistry = meterRegistry;
        this.web3jRegistry = web3jRegistry;

        // 初始化多链监控指标
        initializeChainMetrics();

        this.requestCounter = Counter.builder("gateway.requests.total")
            .description("Total number of requests")
            .register(meterRegistry);

        this.responseTimer = Timer.builder("gateway.response.time")
            .description("Response time in milliseconds")
            .register(meterRegistry);
    }

    /**
     * 初始化多链监控指标
     */
    private void initializeChainMetrics() {
        // 为每条支持的链注册监控指标
        String[] chains = {"eth", "bsc", "polygon"};
        for (String chain : chains) {
            AtomicLong gasPriceGauge = new AtomicLong(0);
            AtomicLong nodeLatencyGauge = new AtomicLong(0);

            gasPriceGauges.put(chain, gasPriceGauge);
            nodeLatencyGauges.put(chain, nodeLatencyGauge);

            Gauge.builder("blockchain.gas.price", gasPriceGauge, AtomicLong::get)
                .tag("chain", chain)
                .description("Current Gas Price in Gwei for " + chain)
                .register(meterRegistry);

            Gauge.builder("blockchain.node.latency", nodeLatencyGauge, AtomicLong::get)
                .tag("chain", chain)
                .description("RPC Node Latency in ms for " + chain)
                .register(meterRegistry);
        }
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
    public void updateGasPrice(String chain, BigInteger gasPrice) {
        AtomicLong gauge = gasPriceGauges.get(chain);
        if (gauge != null) {
            gauge.set(gasPrice.longValue());
        }
    }

    /**
     * 更新节点延迟指标
     */
    public void updateNodeLatency(String chain, long latencyMs) {
        AtomicLong gauge = nodeLatencyGauges.get(chain);
        if (gauge != null) {
            gauge.set(latencyMs);
        }
    }

    /**
     * 获取指定链的当前 Gas 价格
     *
     * @param chain 链标识
     * @return Gas 价格
     */
    public BigInteger getCurrentGasPrice(String chain) throws Exception {
        long startTime = System.currentTimeMillis();
        // 从多链注册中心获取对应链的 Web3j 客户端
        var web3j = web3jRegistry.getClient(chain);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        long latency = System.currentTimeMillis() - startTime;

        updateGasPrice(chain, gasPrice);
        updateNodeLatency(chain, latency);

        return gasPrice;
    }
}
