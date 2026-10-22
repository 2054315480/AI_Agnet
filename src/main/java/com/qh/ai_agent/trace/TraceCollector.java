package com.qh.ai_agent.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 链路采集入口。
 *
 * 用法：
 *   TraceContext ctx = TraceCollector.start(traceId);   // 请求入口开启
 *   TraceContext.Span s = TraceCollector.begin("retrieve");
 *   ... 业务 ...
 *   TraceCollector.end(s, Map.of("candidates", 20));    // 结束 span
 *   TraceCollector.finish();                            // 请求收尾
 */
@Slf4j
public final class TraceCollector {

    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private static volatile boolean enabled = true;

    private static final ThreadLocal<SpanListener> LISTENER = new ThreadLocal<>();

    private static final AtomicLong TOTAL_TRACES = new AtomicLong();

    private TraceCollector() {
    }

    public interface SpanListener {
        void onSpan(TraceContext.Span span, String phase);
    }

    public interface MetricsListener {
        void onMetrics(Map<String, Object> metrics);
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

    /** 开启采集；返回 null 表示未启用。 */
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

    /** 按显式计时补一条 span（并行子任务用） */
    public static void recordSpan(String name, long startOffsetMs, long durationMs,
                                  Map<String, Object> attributes) {
        TraceContext ctx = CURRENT.get();
        if (ctx == null) {
            return;
        }
        ctx.addSpan(name, startOffsetMs, durationMs, attributes);
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

    public static void end(TraceContext.Span span, Map<String, Object> attrs) {
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

    /**
     * 从 ChatResponse.metadata.usage 采集 token。
     *
     * 同一个 trace 可能有多次 LLM 调用（ReAct 多轮、指代消解），这里累加到
     * TraceContext.metrics，TraceStore 再用 totalTokens 落 agent_trace.total_tokens。
     * 返回本次调用的 token 数，调用方可直接并入 llm span.attributes，便于事后算输入/输出费用。
     */
    public static Map<String, Object> recordChatUsage(ChatResponse response) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        TraceContext ctx = CURRENT.get();
        if (ctx == null) {
            return attrs;
        }

        Usage usage = null;
        try {
            if (response != null && response.getMetadata() != null) {
                usage = response.getMetadata().getUsage();
            }
        } catch (Exception e) {
            log.debug("[TraceCollector] 读取 usage 失败: {}", e.getMessage());
        }
        if (usage == null) {
            markDegraded("token_usage_unavailable: ChatResponse.metadata.usage is null");
            return attrs;
        }

        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        Integer total = usage.getTotalTokens();
        if (prompt == null && completion == null && total == null) {
            markDegraded("token_usage_unavailable: usage fields are null");
            return attrs;
        }

        int p = prompt == null ? 0 : prompt;
        int c = completion == null ? 0 : completion;
        int t = total == null ? p + c : total;

        synchronized (ctx) {
            if (p > 0) {
                addIntMetric(ctx, "promptTokens", p);
            }
            if (c > 0) {
                addIntMetric(ctx, "completionTokens", c);
            }
            if (t > 0) {
                addIntMetric(ctx, "totalTokens", t);
            }
        }

        if (prompt != null) {
            attrs.put("promptTokens", prompt);
        }
        if (completion != null) {
            attrs.put("completionTokens", completion);
        }
        if (total != null || t > 0) {
            attrs.put("totalTokens", t);
        }
        return attrs;
    }

    private static void addIntMetric(TraceContext ctx, String key, int delta) {
        Object old = ctx.getMetrics().get(key);
        int base = old instanceof Number n ? n.intValue() : 0;
        ctx.getMetrics().put(key, base + delta);
    }

    /** 请求收尾：清理 ThreadLocal，防止线程池复用串数据 / 内存泄漏。 */
    public static void finish() {
        CURRENT.remove();
        LISTENER.remove();
    }

    /** 供异步线程继承上下文（配合 TaskDecorator 使用）。 */
    public static void attach(TraceContext ctx, SpanListener listener) {
        if (ctx != null) {
            CURRENT.set(ctx);
            if (listener != null) {
                LISTENER.set(listener);
            }
        }
    }
}
