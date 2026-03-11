package com.nexus.bridgegateway.core.monitor;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器
 * 异常时自动熔断保护
 */
@Component
public class CircuitBreaker {

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile Instant lastFailureTime;

    private final int failureThreshold = 5;
    private final Duration timeout = Duration.ofSeconds(30);
    private final BigInteger gasPriceThreshold = BigInteger.valueOf(500_000_000_000L); // 500 Gwei

    /**
     * 检查是否允许请求通过
     */
    public boolean allowRequest() {
        State currentState = state.get();

        if (currentState == State.OPEN) {
            if (Duration.between(lastFailureTime, Instant.now()).compareTo(timeout) > 0) {
                state.set(State.HALF_OPEN);
                return true;
            }
            return false;
        }

        return true;
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        failureCount.set(0);
        state.set(State.CLOSED);
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        lastFailureTime = Instant.now();
        int failures = failureCount.incrementAndGet();

        if (failures >= failureThreshold) {
            state.set(State.OPEN);
        }
    }

    /**
     * 检查 Gas 价格是否触发熔断
     */
    public boolean shouldTrip(BigInteger gasPrice) {
        if (gasPrice.compareTo(gasPriceThreshold) > 0) {
            recordFailure();
            return true;
        }
        return false;
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        return state.get();
    }

    /**
     * 熔断器状态
     */
    public enum State {
        /**
         * 关闭 - 正常处理请求
         */
        CLOSED,

        /**
         * 开启 - 拒绝请求
         */
        OPEN,

        /**
         * 半开 - 允许部分请求测试
         */
        HALF_OPEN
    }
}
