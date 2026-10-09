package com.qh.ai_agent.dialog.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qh.ai_agent.dialog.model.DialogState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis 版会话状态存储：解决多实例部署会话不共享、重启丢状态的问题。
 *
 * - Key：cs:session:{sessionId}，Value：DialogState 的 JSON
 * - 滑动 TTL：每次读取都续期
 * - 降级：Redis 读写异常时退回本地内存，保证对话不中断（降级期间状态不跨实例）
 */
@Slf4j
public class RedisDialogStateStore implements DialogStateStore {

    private static final String KEY_PREFIX = "cs:session:";

    private final StringRedisTemplate redis;
    /** 序列化器：容错配置，新增字段时旧数据不会导致读取失败；提成静态工厂便于单测 */
    private final ObjectMapper objectMapper = jsonMapper();

    public static ObjectMapper jsonMapper() {
        return new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    private final Duration ttl;
    private final InMemoryDialogStateStore fallback;

    public RedisDialogStateStore(StringRedisTemplate redis, int ttlMinutes, int maxSessions) {
        this.redis = redis;
        this.ttl = Duration.ofMinutes(Math.max(1, ttlMinutes));
        this.fallback = new InMemoryDialogStateStore(ttlMinutes, maxSessions);
    }

    @Override
    public DialogState get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        String key = KEY_PREFIX + sessionId;
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return fallback.get(sessionId);
            }
            redis.expire(key, ttl);
            return objectMapper.readValue(json, DialogState.class);
        } catch (Exception e) {
            log.warn("[SessionStore] Redis 读取失败，使用本地状态兜底: {}", e.getMessage());
            return fallback.get(sessionId);
        }
    }

    @Override
    public void put(String sessionId, DialogState state) {
        if (sessionId == null || state == null) {
            return;
        }
        fallback.put(sessionId, state);
        String key = KEY_PREFIX + sessionId;
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(state), ttl);
        } catch (Exception e) {
            log.warn("[SessionStore] Redis 写入失败，仅本地生效: {}", e.getMessage());
        }
    }

    @Override
    public DialogState getOrCreate(String sessionId, Supplier<DialogState> factory) {
        DialogState exist = get(sessionId);
        if (exist != null) {
            return exist;
        }
        DialogState created = factory.get();
        put(sessionId, created);
        return created;
    }

    @Override
    public DialogState remove(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        DialogState removed = fallback.remove(sessionId);
        try {
            redis.delete(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("[SessionStore] Redis 删除失败: {}", e.getMessage());
        }
        return removed;
    }

    @Override
    public int size() {
        return -1;
    }

    @Override
    public String describe() {
        return "redis(ttl=" + ttl.toMinutes() + "min, prefix=" + KEY_PREFIX + ") + fallback " + fallback.describe();
    }
}
