package com.qh.ai_agent.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 轻量熔断器（手写，零依赖）。
 *
 * 三态模型：
 *   CLOSED    正常放行，统计失败率
 *   OPEN      失败率超阈值 -> 直接快速失败，不再调用下游
 *   HALF_OPEN 熔断时间到 -> 放行少量探测请求，成功则恢复，失败则继续熔断
 *
 * 为什么自己写而不是用 Resilience4j：
 * 项目只需要一个最基础的熔断，手写 100 行能讲清每个参数的含义；
 * 引入框架反而要解释一堆默认配置。
 */
@Slf4j
@Component
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger windowFailure = new AtomicInteger();
    private final AtomicInteger windowTotal = new AtomicInteger();
    private final AtomicInteger halfOpenSuccess = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();
    private final AtomicLong rejectedCount = new AtomicLong();

    /** 失败率阈值（0~100） */
    @Value("${cs.circuit.failure-rate-threshold:50}")
    private int failureRateThreshold;

    /** 统计窗口内的最小请求数（样本太少不熔断） */
    @Value("${cs.circuit.min-calls:10}")
    private int minCalls;

    /** 熔断后多久进入半开（毫秒） */
    @Value("${cs.circuit.open-duration-ms:30000}")
    private long openDurationMs;

    /** 半开状态需要几次成功才恢复 */
    @Value("${cs.circuit.half-open-success:2}")
    private int halfOpenSuccessNeeded;

    /** 是否允许本次调用 */
    public boolean tryAcquire() {
        State s = state.get();
        if (s == State.CLOSED) {
            return true;
        }
        if (s == State.OPEN) {
            if (System.currentTimeMillis() - openedAt.get() >= openDurationMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenSuccess.set(0);
                    log.warn("[CircuitBreaker] 进入 HALF_OPEN，开始探测");
                }
                return true;
            }
            rejectedCount.incrementAndGet();
            return false;
        }
        // HALF_OPEN：放行探测
        return true;
    }

    /** 记录一次成功 */
    public void recordSuccess() {
        State s = state.get();
        if (s == State.HALF_OPEN) {
            if (halfOpenSuccess.incrementAndGet() >= halfOpenSuccessNeeded) {
                state.set(State.CLOSED);
                windowFailure.set(0);
                windowTotal.set(0);
                log.info("[CircuitBreaker] 探测成功，恢复 CLOSED");
            }
            return;
        }
        windowTotal.incrementAndGet();
    }

    /** 记录一次失败 */
    public void recordFailure() {
        State s = state.get();
        if (s == State.HALF_OPEN) {
            state.set(State.OPEN);
            openedAt.set(System.currentTimeMillis());
            log.warn("[CircuitBreaker] 半开探测失败，重新 OPEN");
            return;
        }
        int total = windowTotal.incrementAndGet();
        int failures = windowFailure.incrementAndGet();
        if (total >= minCalls) {
            int rate = failures * 100 / total;
            if (rate >= failureRateThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    openedAt.set(System.currentTimeMillis());
                    log.warn("[CircuitBreaker] 失败率 {}% 超过阈值 {}%，进入 OPEN", rate, failureRateThreshold);
                }
            }
        }
    }

    /** 手动重置（演示用） */
    public void reset() {
        state.set(State.CLOSED);
        windowFailure.set(0);
        windowTotal.set(0);
        halfOpenSuccess.set(0);
        log.info("[CircuitBreaker] 手动重置为 CLOSED");
    }

    /** 手动打开（演示用） */
    public void forceOpen() {
        state.set(State.OPEN);
        openedAt.set(System.currentTimeMillis());
        log.warn("[CircuitBreaker] 手动强制 OPEN");
    }

    public String getState() {
        return state.get().name();
    }

    public long getRejectedCount() {
        return rejectedCount.get();
    }

    public int getFailureRate() {
        int t = windowTotal.get();
        return t == 0 ? 0 : windowFailure.get() * 100 / t;
    }

    public int getWindowTotal() {
        return windowTotal.get();
    }
}
