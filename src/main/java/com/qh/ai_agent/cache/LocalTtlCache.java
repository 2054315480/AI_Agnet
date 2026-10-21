package com.qh.ai_agent.cache;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L1 本地缓存：单实例内的瞬时热点。
 *
 * 为什么需要 L1：同一问题在 1 秒内被问 10 次时，连 Redis 的网络往返（1-2ms）都不必要。
 * 为什么不用 Caffeine：为减少依赖，这里手写 TTL + 容量上限，逻辑透明便于面试讲解。
 *
 * 注意：多实例场景下 L1 不共享，所以只作为 L2（Redis）的前置加速，一致性靠短 TTL 兜底。
 */
@Component
public class LocalTtlCache {

    private record Entry(String value, long expireAt) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    private static final int MAX_SIZE = 2000;

    public String get(String key) {
        Entry e = store.get(key);
        if (e == null) {
            misses.incrementAndGet();
            return null;
        }
        if (e.expireAt() < System.currentTimeMillis()) {
            store.remove(key);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return e.value();
    }

    public void put(String key, String value, long ttlSeconds) {
        if (store.size() >= MAX_SIZE) {
            // 简单淘汰：清理已过期的，仍超限就清掉最早的一批
            store.entrySet().removeIf(en -> en.getValue().expireAt() < System.currentTimeMillis());
            if (store.size() >= MAX_SIZE) {
                int toRemove = store.size() / 4;
                for (String k : store.keySet()) {
                    if (toRemove-- <= 0) {
                        break;
                    }
                    store.remove(k);
                    evictions.incrementAndGet();
                }
            }
        }
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    public void invalidateAll() {
        store.clear();
    }

    public long getHits() { return hits.get(); }
    public long getMisses() { return misses.get(); }
    public long getEvictions() { return evictions.get(); }
    public int size() { return store.size(); }
}
