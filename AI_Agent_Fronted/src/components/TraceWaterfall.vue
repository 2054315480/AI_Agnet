<template>
  <StateBlock
    :loading="loading"
    loading-text="正在读取链路瀑布..."
    :error="error || false"
    :empty="!loading && !error && !model"
    empty-text="暂无链路数据"
    @retry="$emit('retry')"
  >
    <article v-if="model" class="trace-waterfall">
      <!-- 顶部概要：traceId / 状态 / 总耗时 / Token -->
      <header class="tw-head">
        <div class="tw-head-top">
          <div class="tw-kicker">TRACE WATERFALL</div>
          <span class="tw-status" :class="statusClass(model.status)">
            <span class="tw-status-dot"></span>{{ statusText(model.status) }}
          </span>
        </div>
        <div class="tw-trace-id mono" :title="model.traceId || '未提供 traceId'">
          {{ model.traceId || '未提供 traceId' }}
        </div>
        <div class="tw-stats">
          <div class="tw-stat">
            <span class="tw-stat-k">总耗时</span>
            <strong class="tw-stat-v">{{ fmtMs(model.totalMs) }}</strong>
          </div>
          <div class="tw-stat">
            <span class="tw-stat-k">Token</span>
            <strong class="tw-stat-v">{{ model.totalTokens == null ? '—' : fmtNumber(model.totalTokens) }}</strong>
          </div>
          <div class="tw-stat">
            <span class="tw-stat-k">跳数</span>
            <strong class="tw-stat-v">{{ model.hops.length }}</strong>
          </div>
          <div class="tw-stat">
            <span class="tw-stat-k">意图</span>
            <strong class="tw-stat-v tw-stat-intent">{{ model.intent || '—' }}</strong>
          </div>
        </div>
        <div v-if="model.query" class="tw-query-row">
          <span class="tw-query-k">原问题</span>
          <span class="tw-query-v">{{ model.query }}</span>
        </div>
        <div v-if="model.rewrittenQuery" class="tw-query-row">
          <span class="tw-query-k">改写后</span>
          <span class="tw-query-v rewritten">{{ model.rewrittenQuery }}</span>
        </div>
      </header>

      <!-- 全景压缩条：先一眼看出耗时集中在哪一跳 -->
      <div v-if="model.hops.length" class="tw-overview" aria-label="链路耗时全景">
        <span
          v-for="(hop, i) in model.hops"
          :key="'ov' + i"
          class="tw-ov-seg"
          :style="{ width: hop.widthPct + '%', background: hop.color }"
          :title="hop.name + ' · ' + fmtMs(hop.durationMs)"
        ></span>
      </div>

      <div v-if="!model.hops.length" class="tw-hops-empty">
        已获取 trace 概要，但后端尚未返回 hops / spans 明细。
      </div>

      <!-- 横向瀑布条 -->
      <div v-else class="tw-hop-list">
        <div
          v-for="(hop, i) in model.hops"
          :key="hop.name + i"
          class="tw-hop"
          :class="statusClass(hop.status)"
        >
          <button class="tw-hop-main" type="button" @click="toggleHop(i)">
            <span class="tw-index mono">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="tw-hop-label">
              <span class="tw-hop-name">
                {{ hop.name }}
                <em v-if="hop.type" class="tw-type">{{ hop.type }}</em>
              </span>
              <span class="tw-hop-detail" :title="hop.detail">{{ hop.detail || '无补充说明' }}</span>
            </span>
            <span class="tw-track">
              <span class="tw-track-rail"></span>
              <span
                class="tw-bar"
                :style="{ left: hop.leftPct + '%', width: hop.widthPct + '%', background: hop.color }"
              ></span>
            </span>
            <span class="tw-ms mono">{{ fmtMs(hop.durationMs) }}</span>
            <svg
              class="tw-chevron"
              :class="{ open: expanded[i] }"
              width="13" height="13" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
            >
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>

          <div v-if="expanded[i]" class="tw-data">
            <div v-if="hop.entries.length" class="tw-data-grid">
              <span v-for="entry in hop.entries" :key="entry.k" class="tw-chip">
                <span class="tw-chip-k">{{ entry.k }}</span>
                <span class="tw-chip-v mono" :title="entry.v">{{ entry.v }}</span>
              </span>
            </div>
            <div v-else class="tw-no-data">该跳没有附加 data</div>
          </div>
        </div>
      </div>
    </article>
  </StateBlock>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StateBlock from './StateBlock.vue'

const props = defineProps({
  trace: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: [Boolean, String, Object], default: false }
})
defineEmits(['retry'])

const expanded = ref({})

/** 兼容 camelCase / snake_case，也兼容旧接口的 { trace, spans } 形态 */
function firstDefined(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') return value
  }
  return undefined
}

function toNumber(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function parseMaybeJson(value) {
  if (typeof value !== 'string') return value
  const text = value.trim()
  if (!text) return value
  if (!/^[\[{]/.test(text)) return value
  try {
    return JSON.parse(text)
  } catch (e) {
    return value
  }
}

function fmtNumber(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value ?? '—')
  return n.toLocaleString('zh-CN')
}

function fmtMs(value) {
  const n = toNumber(value, 0)
  if (n >= 10000) return (n / 1000).toFixed(1) + 's'
  if (n >= 1000) return (n / 1000).toFixed(2) + 's'
  if (n > 0 && n < 1) return '<1ms'
  return Math.round(n * 10) / 10 + 'ms'
}

function statusClass(status) {
  const s = String(status || '').toLowerCase()
  if (['success', 'ok', 'completed', 'closed'].includes(s)) return 'ok'
  if (['error', 'fail', 'failed', 'timeout', 'open'].includes(s)) return 'danger'
  if (['running', 'half_open', 'degraded'].includes(s)) return 'warning'
  return 'neutral'
}

function statusText(status) {
  const s = String(status || '').toLowerCase()
  const map = {
    success: '成功',
    ok: '正常',
    completed: '完成',
    running: '进行中',
    error: '失败',
    fail: '失败',
    failed: '失败',
    timeout: '超时',
    degraded: '降级'
  }
  return map[s] || (status ? String(status) : '未知')
}

function hopType(name, raw) {
  if (raw && raw.type) return String(raw.type)
  const n = String(name || '').toLowerCase()
  if (/intent|意图/.test(n)) return 'intent'
  if (/rewrite|改写/.test(n)) return 'rewrite'
  if (/bm25|关键词/.test(n)) return 'bm25'
  if (/vector|向量/.test(n)) return 'vector'
  if (/rrf|融合/.test(n)) return 'rrf'
  if (/rerank|精排|重排/.test(n)) return 'rerank'
  if (/llm|generate|回答|生成/.test(n)) return 'llm'
  if (/persist|落库|write/.test(n)) return 'persist'
  if (/response|响应/.test(n)) return 'response'
  if (/kg|图谱/.test(n)) return 'kg'
  if (/cache|缓存/.test(n)) return 'cache'
  return ''
}

function colorFor(type, status) {
  if (statusClass(status) === 'danger') return 'var(--danger)'
  if (type === 'llm') return 'var(--accent-love)'
  if (type === 'persist' || type === 'response') return 'var(--success)'
  if (type === 'intent' || type === 'rewrite' || type === 'cache') return 'var(--accent-love)'
  if (type) return 'var(--accent)'
  return 'var(--text-tertiary)'
}

function dataOf(raw) {
  const value = firstDefined(raw.data, raw.attributes, raw.attrs)
  const parsed = parseMaybeJson(value)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    if (parsed === undefined || parsed === null || parsed === '') return {}
    return { value: parsed }
  }
  return parsed
}

function fallbackDetail(raw, data) {
  if (raw.detail) return String(raw.detail)
  const entries = Object.entries(data || {})
  if (!entries.length) return ''
  return entries.slice(0, 2).map(([k, v]) => {
    if (Array.isArray(v)) return k + ': ' + v.length + ' 项'
    if (v && typeof v === 'object') return k + ': ' + Object.keys(v).length + ' 字段'
    return k + ': ' + String(v)
  }).join(' · ')
}

function valueText(value) {
  if (value === null) return 'null'
  if (value === undefined) return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') {
    if (Array.isArray(value)) return value.length + ' 项'
    return Object.keys(value).length + ' 字段'
  }
  return String(value)
}

/** 把 data 展平成 key: value 的小 chip，不展示原始 JSON */
function flattenEntries(data, prefix = '', out = []) {
  if (!data || typeof data !== 'object') {
    if (prefix) out.push({ k: prefix, v: valueText(data) })
    return out
  }
  for (const [key, value] of Object.entries(data)) {
    const label = prefix ? prefix + '.' + key : key
    if (value && typeof value === 'object') {
      if (Array.isArray(value)) {
        if (value.every(item => item === null || typeof item !== 'object')) {
          out.push({ k: label, v: value.length ? value.join(', ') : '空' })
        } else {
          out.push({ k: label, v: value.length + ' 项' })
        }
      } else {
        flattenEntries(value, label, out)
      }
    } else {
      out.push({ k: label, v: valueText(value) })
    }
  }
  return out
}

const model = computed(() => {
  const source = props.trace
  if (!source || typeof source !== 'object') return null

  const meta = source.trace && typeof source.trace === 'object' ? source.trace : {}
  const rawHops = Array.isArray(source.hops)
    ? source.hops
    : (Array.isArray(source.spans) ? source.spans : [])

  let cursor = 0
  const rawEnriched = rawHops.map((raw, index) => {
    const name = String(firstDefined(raw.name, raw.span_name, raw.spanName, '第 ' + (index + 1) + ' 跳'))
    const durationMs = toNumber(firstDefined(raw.durationMs, raw.duration_ms, raw.duration, raw.ms), 0)
    const explicitStart = firstDefined(raw.startMs, raw.start_ms, raw.offsetMs, raw.offset_ms)
    const startMs = explicitStart === undefined ? cursor : toNumber(explicitStart, cursor)
    const data = dataOf(raw)
    cursor = Math.max(cursor, startMs + durationMs)
    const type = hopType(name, raw)
    return {
      name,
      durationMs,
      startMs,
      status: firstDefined(raw.status, 'ok'),
      type,
      detail: fallbackDetail(raw, data),
      entries: flattenEntries(data),
      color: colorFor(type, raw.status)
    }
  })

  const llmTokens = rawEnriched.reduce((sum, hop) => {
    const prompt = toNumber(firstDefined(
      hop.entries.find(e => /prompt.?tokens/i.test(e.k))?.v,
      0
    ), 0)
    const completion = toNumber(firstDefined(
      hop.entries.find(e => /completion.?tokens/i.test(e.k))?.v,
      0
    ), 0)
    return sum + prompt + completion
  }, 0)

  const declaredTotalMs = toNumber(firstDefined(
    source.totalMs,
    source.total_ms,
    meta.total_ms,
    meta.totalMs
  ), 0)
  const totalMs = Math.max(declaredTotalMs, cursor, 0)
  const trackTotal = Math.max(totalMs, 1)

  const hops = rawEnriched.map(hop => {
    const rawLeft = (hop.startMs / trackTotal) * 100
    const leftPct = Math.min(Math.max(rawLeft, 0), 99.8)
    const rawWidth = hop.durationMs > 0
      ? (hop.durationMs / trackTotal) * 100
      : 0.25
    const widthPct = Math.max(Math.min(rawWidth, 100 - leftPct), hop.durationMs > 0 ? 0.8 : 0.25)
    return { ...hop, leftPct, widthPct }
  })

  return {
    traceId: firstDefined(source.traceId, source.trace_id, meta.trace_id, meta.traceId),
    sessionId: firstDefined(source.sessionId, source.session_id, meta.session_id),
    query: firstDefined(source.query, source.userQuery, source.user_query, meta.user_query),
    rewrittenQuery: firstDefined(source.rewrittenQuery, source.rewritten_query, meta.rewritten_query),
    intent: firstDefined(source.intent, meta.intent),
    totalMs,
    totalTokens: firstDefined(
      source.totalTokens,
      source.total_tokens,
      meta.total_tokens,
      llmTokens > 0 ? llmTokens : undefined
    ),
    status: firstDefined(source.status, meta.status, hops.length ? 'success' : 'unknown'),
    hops
  }
})

watch(() => model.value?.traceId, () => { expanded.value = {} })

function toggleHop(index) {
  expanded.value = { ...expanded.value, [index]: !expanded.value[index] }
}
</script>

<style scoped>
.trace-waterfall {
  --tw-track-height: 9px;
  color: var(--text-primary);
}
.tw-head {
  padding: 16px 18px 14px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--bg-sidebar), var(--bg-step));
}
.tw-head-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.tw-kicker {
  font-size: 0.625rem;
  letter-spacing: 0.18em;
  color: var(--text-tertiary);
}
.tw-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  font-size: 0.6875rem;
  border: 1px solid var(--border-secondary);
  color: var(--text-secondary);
}
.tw-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.tw-status.ok { color: var(--success); background: var(--success-bg); border-color: transparent; }
.tw-status.danger { color: var(--danger); background: var(--danger-bg); border-color: transparent; }
.tw-status.warning { color: var(--warning); background: var(--warning-bg); border-color: transparent; }
.tw-trace-id {
  margin-top: 7px;
  font-size: 0.75rem;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tw-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}
.tw-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.tw-stat-k {
  font-size: 0.625rem;
  letter-spacing: 0.08em;
  color: var(--text-tertiary);
}
.tw-stat-v {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tw-stat-intent { font-size: 0.9375rem; }
.tw-query-row {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  font-size: 0.75rem;
  line-height: 1.6;
}
.tw-query-k { flex-shrink: 0; width: 48px; color: var(--text-tertiary); }
.tw-query-v { color: var(--text-secondary); }
.tw-query-v.rewritten { color: var(--accent); }

.tw-overview {
  display: flex;
  width: 100%;
  height: 5px;
  margin: 16px 0 8px;
  border-radius: var(--radius-full);
  overflow: hidden;
  background: var(--bg-step);
}
.tw-ov-seg { display: block; height: 100%; min-width: 2px; opacity: 0.9; }
.tw-ov-seg + .tw-ov-seg { box-shadow: -1px 0 0 var(--bg-app); }

.tw-hop-list {
  margin-top: 12px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg-sidebar);
}
.tw-hop + .tw-hop { border-top: 1px solid var(--rule); }
.tw-hop-main {
  display: grid;
  grid-template-columns: 32px minmax(150px, 1.1fr) minmax(140px, 2fr) 64px 14px;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 12px;
  text-align: left;
  color: inherit;
  background: transparent;
  transition: background var(--duration-fast) var(--ease-out);
}
.tw-hop-main:hover { background: var(--bg-hover); }
.tw-index { color: var(--text-tertiary); font-size: 0.625rem; }
.tw-hop-label { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.tw-hop-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tw-type {
  padding: 0 5px;
  border-radius: var(--radius-full);
  background: var(--bg-step);
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 0.5625rem;
  font-style: normal;
}
.tw-hop-detail {
  font-size: 0.625rem;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tw-track {
  position: relative;
  display: block;
  height: var(--tw-track-height);
}
.tw-track-rail {
  position: absolute;
  inset: 0;
  border-radius: var(--radius-full);
  background: var(--bg-step);
}
.tw-bar {
  position: absolute;
  top: 0;
  bottom: 0;
  min-width: 2px;
  border-radius: var(--radius-full);
  opacity: 0.9;
  transition: width 280ms var(--ease-out), left 280ms var(--ease-out);
}
.tw-hop.danger .tw-bar { opacity: 1; }
.tw-ms { text-align: right; font-size: 0.6875rem; color: var(--text-secondary); }
.tw-chevron { color: var(--text-tertiary); transition: transform var(--duration-fast) var(--ease-out); }
.tw-chevron.open { transform: rotate(180deg); }

.tw-data {
  padding: 0 12px 11px 54px;
}
.tw-data-grid { display: flex; flex-wrap: wrap; gap: 6px; }
.tw-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  padding: 3px 7px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-xs);
  background: var(--bg-step);
  font-size: 0.625rem;
}
.tw-chip-k { color: var(--text-tertiary); }
.tw-chip-v { color: var(--accent); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tw-no-data { font-size: 0.6875rem; color: var(--text-tertiary); }
.tw-hops-empty { padding: 26px; text-align: center; color: var(--text-tertiary); font-size: 0.75rem; border: 1px dashed var(--rule); border-radius: var(--radius); }

@media (max-width: 700px) {
  .tw-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .tw-hop-main { grid-template-columns: 24px minmax(110px, 1fr) 50px 12px; }
  .tw-track { grid-column: 1 / -1; grid-row: 2; }
  .tw-hop-detail { max-width: 40vw; }
  .tw-data { padding-left: 44px; }
}
</style>
