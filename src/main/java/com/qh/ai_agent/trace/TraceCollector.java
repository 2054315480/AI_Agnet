package com.qh.ai_agent.trace;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 链路采集入口。
 *
 * 用法：
 *   TraceContext ctx = TraceCollector.start(traceId);   // 请求入口开启
 *   TraceContext.Span s = TraceCollector.begin("retrieve");
 *   ... 业务 ...
 *   TraceCollector.end(s, Map.of("candidates", 20));    // 结束时结束 span
 *   TraceCollector.finish();                            // 请求收尾，触发持久化与清理
 */
@Slf4j
public final class TraceCollector {

    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    /** 采集开关：默认关闭，由配置或请求头开启，避免生产环境无谓开销 */
    private static volatile boolean enabled = true;  // 演示项目默认开启，生产可置 false

    /** 实时回调：开启后每产生一个 span 就通知（用于 SSE 实时推送） */
    private static final ThreadLocal<SpanListener> LISTENER = new ThreadLocal<>();

    /** 简单的累计计数，用于自证条 */
    private static final AtomicLong TOTAL_TRACES = new AtomicLong();

    private TraceCollector() {
    }

    public interface SpanListener {
        void onSpan(TraceContext.Span span, String phase);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static long totalTraces() {
        return TOTAL_TRACES.get();
    }

    /** 开启采集；返回 null 表示未启用 */
    public static TraceContext start(String traceId, String sessionId, String userQuery, SpanListener listener) {
        if (!enabled) {
            return null;
        }
        TraceContext ctx = new TraceContext(traceId);
        ctx.setSessionId(sessionId);
        ctx.setUserQuery(userQuery);
        CURRENT.set(ctx);
        if (listener != null) {
            LISTENER.set(listener);
        }
        TOTAL_TRACES.incrementAndGet();
        return ctx;
    }

    public static TraceContext current() {
        return CURRENT.get();
    }

    public static TraceContext.Span begin(String name) {
        TraceContext ctx = CURRENT.get();
        if (ctx == null) {
            return null;
        }
        TraceContext.Span span = ctx.begin(name);
        SpanListener listener = LISTENER.get();
        if (listener != null) {
            listener.onSpan(span, "start");
        }
        return span;
    }

    public static void end(TraceContext.Span span, java.util.Map<String, Object> attrs) {
        TraceContext ctx = CURRENT.get();
        if (ctx == null || span == null) {
            return;
        }
        ctx.end(span, attrs);
        SpanListener listener = LISTENER.get();
        if (listener != null) {
            listener.onSpan(span, "end");
        }
    }

    public static void fail(TraceContext.Span span, String reason) {
        TraceContext ctx = CURRENT.get();
        if (ctx == null || span == null) {
            return;
        }
        ctx.fail(span, reason);
        SpanListener listener = LISTENER.get();
        if (listener != null) {
            listener.onSpan(span, "fail");
        }
    }

    public static void markDegraded(String reason) {
        TraceContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.markDegraded(reason);
        }
    }

    public static void metric(String key, Object value) {
        TraceContext ctx = CURRENT.get();
        if (ctx == null) {
            return;
        }
        ctx.getMetrics().put(key, value);
        if ("intent".equals(key) && value != null) {
            ctx.setIntent(String.valueOf(value));
        }
        SpanListener listener = LISTENER.get();
        if (listener != null && listener instanceof MetricsListener) {
            ((MetricsListener) listener).onMetrics(ctx.getMetrics());
        }
    }

    public interface MetricsListener {
        void onMetrics(java.util.Map<String, Object> metrics);
    }

    /** 请求收尾：清理 ThreadLocal，防止线程池复用串数据 / 内存泄漏 */
    public static void finish() {
        CURRENT.remove();
        LISTENER.remove();
    }

    /** 供异步线程继承上下文（配合 TaskDecorator 使用） */
    public static void attach(TraceContext ctx, SpanListener listener) {
        if (ctx != null) {
            CURRENT.set(ctx);
            if (listener != null) {
                LISTENER.set(listener);
            }
        }
    }
}
