package com.qh.ai_agent.trace;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次请求的链路上下文。
 *
 * 设计要点：
 * 1. 用 ThreadLocal 是因为 span 埋点分散在意图识别、检索、重排、生成、落库等多处，
 *    显式一路传参会改动几十个方法签名，侵入性太大。
 * 2. 未开启采集时不产生任何对象，埋点调用直接 return，对主链路零开销。
 * 3. 异步线程需要 TaskDecorator 复制（SSE 在异步线程池执行，ThreadLocal 默认不传递）。
 */
@Data
public class TraceContext {

    private final String traceId;
    private final long startMs = System.currentTimeMillis();
    private String sessionId;
    private String userQuery;
    private String rewrittenQuery;
    private String intent;
    private String status = "success";
    private String degradedReason;

    /** 按开始顺序记录 span */
    private final List<Span> spans = new ArrayList<>();

    /** 自由指标：token、缓存命中等 */
    private final Map<String, Object> metrics = new LinkedHashMap<>();

    public TraceContext(String traceId) {
        this.traceId = traceId;
    }

    public Span begin(String name) {
        Span span = new Span();
        span.setName(name);
        span.setStartOffsetMs(System.currentTimeMillis() - startMs);
        span.setStatus("running");
        spans.add(span);
        return span;
    }

    public void end(Span span, Map<String, Object> attributes) {
        if (span == null) {
            return;
        }
        span.setDurationMs(System.currentTimeMillis() - startMs - span.getStartOffsetMs());
        span.setStatus("ok");
        if (attributes != null) {
            span.setAttributes(attributes);
        }
    }

    public void fail(Span span, String reason) {
        if (span == null) {
            return;
        }
        span.setDurationMs(System.currentTimeMillis() - startMs - span.getStartOffsetMs());
        span.setStatus("fail");
        span.setAttributes(Map.of("reason", reason == null ? "unknown" : reason));
    }

    public void markDegraded(String reason) {
        this.status = "degraded";
        this.degradedReason = reason;
    }

    public long totalMs() {
        return System.currentTimeMillis() - startMs;
    }

    @Data
    public static class Span {
        private String name;
        private long startOffsetMs;
        private long durationMs;
        private String status;
        private Map<String, Object> attributes = new LinkedHashMap<>();
    }
}
