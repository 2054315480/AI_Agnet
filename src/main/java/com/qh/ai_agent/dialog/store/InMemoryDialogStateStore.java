package com.qh.ai_agent.dialog.store;

import com.qh.ai_agent.dialog.model.DialogState;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 内存版会话状态存储。
 *
 * 修复了原来"只 put 不清理"的问题：
 * - 滑动 TTL：超过 ttlMinutes 未访问的会话视为过期，读取时懒删除
 * - 容量上限：会话数超过 maxSessions 时先清过期、再按最久未访问淘汰（LRU）
 * - 命中率统计：describe() 输出当前会话数/命中/未命中，方便排障
 *
 * 局限：只在单个 JVM 内有效，重启丢失、多实例不共享；多实例请用 RedisDialogStateStore。
 */
@Slf4j
public class InMemoryDialogStateStore implements DialogStateStore {

    private static final class Entry {
        private final DialogState state;
        private volatile long lastAccess;

        private Entry(DialogState state, long lastAccess) {
            this.state = state;
            this.lastAccess = lastAccess;
        }
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxSessions;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    public InMemoryDialogStateStore(int ttlMinutes, int maxSessions) {
        this(java.time.Duration.ofMinutes(Math.max(1, ttlMinutes)), maxSessions);
    }

    /** 精确 TTL 构造，便于单测验证过期行为 */
    public InMemoryDialogStateStore(java.time.Duration ttl, int maxSessions) {
        this.ttlMillis = Math.max(1L, ttl.toMillis());
        this.maxSessions = Math.max(16, maxSessions);
    }

    @Override
    public DialogState get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        Entry entry = cache.get(sessionId);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }
        if (isExpired(entry)) {
            cache.remove(sessionId, entry);
            missCount.incrementAndGet();
            return null;
        }
        entry.lastAccess = System.currentTimeMillis();
        hitCount.incrementAndGet();
        return entry.state;
    }

    @Override
    public void put(String sessionId, DialogState state) {
        if (sessionId == null || state == null) {
            return;
        }
        cache.put(sessionId, new Entry(state, System.currentTimeMillis()));
        if (cache.size() > maxSessions) {
            evict();
        }
    }

    /** 原子版 getOrCreate：并发创建同一会话时只会有一个状态生效 */
    @Override
    public DialogState getOrCreate(String sessionId, Supplier<DialogState> factory) {
        if (sessionId == null) {
            return factory.get();
        }
        Entry entry = cache.compute(sessionId, (id, exist) -> {
            if (exist != null && !isExpired(exist)) {
                exist.lastAccess = System.currentTimeMillis();
                return exist;
            }
            return new Entry(factory.get(), System.currentTimeMillis());
        });
        return entry.state;
    }

    @Override
    public DialogState remove(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        Entry entry = cache.remove(sessionId);
        return entry != null ? entry.state : null;
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public String describe() {
        return "memory(ttl=" + (ttlMillis / 60_000L) + "min, max=" + maxSessions
                + ", sessions=" + cache.size() + ", hit=" + hitCount.get() + ", miss=" + missCount.get() + ")";
    }

    private boolean isExpired(Entry entry) {
        return System.currentTimeMillis() - entry.lastAccess > ttlMillis;
    }

    /** 先清理过期会话，仍超上限则按最久未访问淘汰 */
    private void evict() {
        cache.entrySet().removeIf(e -> isExpired(e.getValue()));
        int overflow = cache.size() - maxSessions;
        if (overflow <= 0) {
            return;
        }
        List<Map.Entry<String, Entry>> oldest = cache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastAccess))
                .limit(overflow)
                .toList();
        for (Map.Entry<String, Entry> e : oldest) {
            cache.remove(e.getKey(), e.getValue());
        }
        log.warn("[SessionStore] 会话数超过上限 {}，淘汰 {} 个最久未访问会话", maxSessions, oldest.size());
    }
}
