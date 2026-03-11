package com.nexus.bridgegateway.core.monitor;

import java.math.BigInteger;

/**
 * 网关指标数据
 */
public class GatewayMetrics {

    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private double averageResponseTime;
    private BigInteger currentGasPrice;
    private long nodeLatency;
    private CircuitBreaker.State circuitBreakerState;

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getSuccessfulRequests() {
        return successfulRequests;
    }

    public void setSuccessfulRequests(long successfulRequests) {
        this.successfulRequests = successfulRequests;
    }

    public long getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(long failedRequests) {
        this.failedRequests = failedRequests;
    }

    public double getAverageResponseTime() {
        return averageResponseTime;
    }

    public void setAverageResponseTime(double averageResponseTime) {
        this.averageResponseTime = averageResponseTime;
    }

    public BigInteger getCurrentGasPrice() {
        return currentGasPrice;
    }

    public void setCurrentGasPrice(BigInteger currentGasPrice) {
        this.currentGasPrice = currentGasPrice;
    }

    public long getNodeLatency() {
        return nodeLatency;
    }

    public void setNodeLatency(long nodeLatency) {
        this.nodeLatency = nodeLatency;
    }

    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public void setCircuitBreakerState(CircuitBreaker.State circuitBreakerState) {
        this.circuitBreakerState = circuitBreakerState;
    }
}
