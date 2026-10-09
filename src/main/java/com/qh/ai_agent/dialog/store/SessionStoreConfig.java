package com.qh.ai_agent.dialog.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 会话状态存储装配。
 *
 * cs.session.store = memory（默认，单实例）
 * cs.session.store = redis （多实例共享；启动时探活失败会自动退回内存）
 */
@Slf4j
@Configuration
public class SessionStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "cs.session.store", havingValue = "redis")
    public DialogStateStore redisDialogStateStore(StringRedisTemplate stringRedisTemplate,
                                                  @Value("${cs.session.ttl-minutes:30}") int ttlMinutes,
                                                  @Value("${cs.session.max-sessions:5000}") int maxSessions) {
        try {
            // 启动探活：连不上就退回内存，避免把整个应用拖挂
            stringRedisTemplate.hasKey("cs:session:__health_check__");
            log.info("[SessionStore] 使用 Redis 存储会话状态（多实例共享，ttl={}min）", ttlMinutes);
            return new RedisDialogStateStore(stringRedisTemplate, ttlMinutes, maxSessions);
        } catch (Exception e) {
            log.warn("[SessionStore] Redis 不可用，回退内存存储: {}", e.getMessage());
            return new InMemoryDialogStateStore(ttlMinutes, maxSessions);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "cs.session.store", havingValue = "memory", matchIfMissing = true)
    public DialogStateStore inMemoryDialogStateStore(@Value("${cs.session.ttl-minutes:30}") int ttlMinutes,
                                                     @Value("${cs.session.max-sessions:5000}") int maxSessions) {
        log.info("[SessionStore] 使用内存存储会话状态（单实例，ttl={}min, max={}）", ttlMinutes, maxSessions);
        return new InMemoryDialogStateStore(ttlMinutes, maxSessions);
    }
}
