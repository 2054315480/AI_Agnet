package com.qh.ai_agent.experiment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 故障注入器：用于"降级演练"。
 *
 * 安全设计（非常重要）：
 * 1. 必须带到期时间，到点自动失效，避免忘记关闭把服务搞挂
 * 2. 只有 ADMIN 能开启
 * 3. 每次开启都打日志，便于审计
 */
@Slf4j
@Component
public class FaultInjector {

    /** target -> 到期时间戳 */
    private final Map<String, Long> active = new ConcurrentHashMap<>();

    public void inject(String target, long durationSeconds) {
        long expireAt = Instant.now().plusSeconds(durationSeconds).toEpochMilli();
        active.put(target, expireAt);
        log.warn("[FaultInjector] 已注入故障 target={} 持续 {}s（{} 后自动失效）", target, durationSeconds, expireAt);
    }

    public void clear(String target) {
        active.remove(target);
        log.warn("[FaultInjector] 已清除故障 target={}", target);
    }

    public void clearAll() {
        active.clear();
        log.warn("[FaultInjector] 已清除全部故障注入");
    }

    /** 判断某个目标当前是否处于故障状态（过期自动清理） */
    public boolean isFaulty(String target) {
        Long expireAt = active.get(target);
        if (expireAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expireAt) {
            active.remove(target);
            log.info("[FaultInjector] 故障 target={} 已到期自动失效", target);
            return false;
        }
        return true;
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        active.forEach((k, v) -> {
            long remain = Math.max(0, (v - System.currentTimeMillis()) / 1000);
            if (remain > 0) {
                out.put(k, remain + "s");
            }
        });
        return out;
    }
}
