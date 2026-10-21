<template>
  <aside class="live-panel" :class="{ collapsed: !open }">
    <header class="lp-head" @click="$emit('toggle')">
      <span class="lp-dot" :class="{ on: running }"></span>
      <span class="lp-title">实时链路</span>
      <span class="lp-total tnum" v-if="totalMs">{{ totalMs }}ms</span>
      <svg class="lp-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polyline :points="open ? '18 15 12 9 6 15' : '6 9 12 15 18 9'"/>
      </svg>
    </header>

    <div v-if="open" class="lp-body">
      <!-- 空态 -->
      <div v-if="spans.length === 0" class="lp-empty">
        发送一条消息，这里会实时显示它经过的每个环节。
      </div>

      <!-- 时间轴 -->
      <div v-else class="lp-timeline">
        <div v-for="(s, i) in spans" :key="s.name + i" class="lp-span" :class="s.status">
          <span class="sp-mark">
            <span v-if="s.status === 'running'" class="sp-pulse"></span>
            <svg v-else-if="s.status === 'ok'" width="10" height="10" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            <svg v-else width="10" height="10" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </span>
          <span class="sp-name">{{ label(s.name) }}</span>
          <span class="sp-bar">
            <span class="sp-bar-fill" :style="barStyle(s)"></span>
          </span>
          <span class="sp-ms tnum">{{ s.status === 'running' ? '…' : s.durationMs + 'ms' }}</span>
        </div>
      </div>

      <!-- 本轮属性 -->
      <div v-if="spanAttrs.length" class="lp-attrs">
        <div class="la-title">本轮属性</div>
        <div v-for="a in spanAttrs" :key="a.k" class="la-row">
          <span class="la-k">{{ a.k }}</span>
          <span class="la-v">{{ a.v }}</span>
        </div>
      </div>

      <!-- 累计指标 -->
      <div v-if="Object.keys(metrics).length" class="lp-metrics">
        <div v-for="(v, k) in metrics" :key="k" class="lm-row">
          <span class="lm-k">{{ k }}</span>
          <span class="lm-v tnum">{{ v }}</span>
        </div>
      </div>

      <div class="lp-foot">
        数据来自本次请求的真实 span，随请求实时推送
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: true },
  spans: { type: Array, default: () => [] },
  metrics: { type: Object, default: () => ({}) },
  running: { type: Boolean, default: false },
  totalMs: { type: Number, default: 0 }
})
defineEmits(['toggle'])

const LABELS = {
  intent: '意图识别',
  rewrite: '查询改写',
  bm25: 'BM25 检索',
  vector: '向量检索',
  retrieve: '知识库检索',
  rrf: 'RRF 融合',
  rerank: '重排',
  kg: '图谱增强',
  llm: '回答生成',
  persist: '数据落库',
  cache: '缓存查询'
}

function label(name) {
  return LABELS[name] || name
}

function barStyle(s) {
  const total = props.totalMs || 1
  const left = ((s.offsetMs || 0) / total) * 100
  const width = Math.max(2, ((s.durationMs || 0) / total) * 100)
  return { marginLeft: left + '%', width: Math.min(width, 100 - left) + '%' }
}

const spanAttrs = computed(() => {
  const out = []
  for (const s of props.spans) {
    for (const [k, v] of Object.entries(s.attrs || {})) {
      out.push({ k: `${label(s.name)} · ${k}`, v: String(v) })
    }
  }
  return out.slice(0, 8)
})
</script>

<style scoped>
.live-panel {
  width: 268px;
  flex-shrink: 0;
  border-left: 1px solid var(--rule);
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.live-panel.collapsed { width: 44px; }

.lp-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  height: 48px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--rule);
  cursor: pointer;
  user-select: none;
}

.lp-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--text-tertiary);
  flex-shrink: 0;
}

.lp-dot.on {
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-bg);
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.lp-title {
  font-size: 0.75rem;
  color: var(--text-secondary);
  flex: 1;
  white-space: nowrap;
}

.collapsed .lp-title, .collapsed .lp-total { display: none; }

.lp-total {
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
}

.lp-chevron { color: var(--text-tertiary); flex-shrink: 0; }

.lp-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px 16px;
}

.lp-empty {
  font-size: 0.75rem;
  line-height: 1.7;
  color: var(--text-tertiary);
  padding: 8px 0;
}

.lp-span {
  display: grid;
  grid-template-columns: 14px 1fr 60px 44px;
  align-items: center;
  gap: 6px;
  padding: 5px 0;
  font-size: 0.75rem;
}

.sp-mark { display: flex; align-items: center; justify-content: center; color: var(--text-tertiary); }
.lp-span.ok .sp-mark { color: var(--accent); }
.lp-span.fail .sp-mark { color: var(--danger); }
.lp-span.running .sp-mark { color: var(--accent-love); }

.sp-pulse {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--accent-love);
  animation: pulse 1s ease-in-out infinite;
}

.sp-name {
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lp-span.running .sp-name { color: var(--text-primary); }

.sp-bar {
  height: 3px;
  background: var(--bg-step);
  border-radius: 2px;
  overflow: hidden;
}

.sp-bar-fill {
  display: block;
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 200ms var(--ease-out), margin-left 200ms var(--ease-out);
}

.lp-span.running .sp-bar-fill { background: var(--accent-love); }
.lp-span.fail .sp-bar-fill { background: var(--danger); }

.sp-ms {
  text-align: right;
  font-family: var(--font-mono);
  font-size: 0.625rem;
  color: var(--text-tertiary);
}

.lp-attrs, .lp-metrics {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--rule);
}

.la-title {
  font-size: 0.625rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  margin-bottom: 6px;
}

.la-row, .lm-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 0;
  font-size: 0.6875rem;
}

.la-k, .lm-k { color: var(--text-tertiary); }
.la-v, .lm-v { color: var(--text-secondary); font-family: var(--font-mono); }

.lp-foot {
  margin-top: 14px;
  font-size: 0.625rem;
  line-height: 1.6;
  color: var(--text-tertiary);
  opacity: 0.8;
}

@media (max-width: 1100px) {
  .live-panel { display: none; }
}
</style>
