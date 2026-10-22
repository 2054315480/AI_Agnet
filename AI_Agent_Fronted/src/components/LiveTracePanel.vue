<template>
  <aside class="live-panel" :class="{ collapsed: !open }">
    <header class="lp-head" @click="$emit('toggle')">
      <span class="lp-dot" :class="{ on: running }"></span>
      <span class="lp-title">实时链路</span>
      <span class="lp-total tnum" v-if="totalMs && viewMode === 'live'">{{ totalMs }}ms</span>
      <svg class="lp-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polyline :points="open ? '18 15 12 9 6 15' : '6 9 12 15 18 9'"/>
      </svg>
    </header>

    <div v-if="open" class="lp-body">
      <!-- 实时 span 与历史 trace 两个视角 -->
      <div class="lp-tabs">
        <button type="button" :class="{ on: viewMode === 'live' }" @click="viewMode = 'live'">实时</button>
        <button type="button" :class="{ on: viewMode === 'history' }" @click="showHistory">历史瀑布</button>
      </div>

      <template v-if="viewMode === 'live'">
        <!-- 空态 -->
        <div v-if="spans.length === 0" class="lp-empty">
          发送一条消息，这里会实时显示它经过的每个环节。运行结束后可在「历史瀑布」点开完整链路。
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
      </template>

      <!-- 历史 trace：点击一条 → 打开 9 跳瀑布图 -->
      <template v-else>
        <div class="lp-history-head">
          <span>最近链路</span>
          <button type="button" :disabled="recentLoading" @click="loadRecent(true)">
            {{ recentLoading ? '读取中' : '刷新' }}
          </button>
        </div>

        <StateBlock v-if="recentLoading" loading loading-text="正在读取最近链路..." />
        <StateBlock v-else-if="recentError" :error="recentError" @retry="loadRecent(true)" />
        <StateBlock v-else-if="!recentTraces.length" empty empty-text="暂无可查看的链路记录" />
        <div v-else class="lp-trace-list">
          <button
            v-for="t in recentTraces"
            :key="traceIdOf(t)"
            type="button"
            class="lp-trace-item"
            @click="openTrace(t)"
          >
            <span class="tr-top">
              <span class="tr-query">{{ queryOf(t) }}</span>
              <span class="tr-status" :class="traceStatusClass(t)">{{ traceStatusText(t) }}</span>
            </span>
            <span class="tr-meta">
              <span class="mono">{{ shortId(traceIdOf(t)) }}</span>
              <span>{{ fmtMs(traceTotalMs(t)) }}</span>
              <span v-if="traceTokens(t) != null">{{ traceTokens(t) }} tok</span>
            </span>
          </button>
        </div>
      </template>
    </div>

    <!-- 瀑布图弹层：用 Teleport 避免被 268px 侧栏挤压 -->
    <Teleport to="body">
      <Transition name="trace-dialog">
        <div v-if="traceOpen" class="trace-overlay" @click.self="closeTrace">
          <div class="trace-dialog" role="dialog" aria-modal="true" aria-label="链路瀑布图">
            <header class="td-head">
              <div class="td-title-wrap">
                <span class="td-kicker">链路追踪</span>
                <span class="td-title">九跳瀑布图</span>
                <span class="td-id mono">{{ shortId(selectedTraceId, 18) }}</span>
              </div>
              <button type="button" class="td-close" @click="closeTrace">关闭</button>
            </header>
            <div class="td-body">
              <TraceWaterfall
                :trace="traceDetail"
                :loading="traceLoading"
                :error="traceError || false"
                @retry="loadTrace(selectedTraceId)"
              />
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </aside>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { get } from '../api/http.js'
import StateBlock from './StateBlock.vue'
import TraceWaterfall from './TraceWaterfall.vue'

const props = defineProps({
  open: { type: Boolean, default: true },
  spans: { type: Array, default: () => [] },
  metrics: { type: Object, default: () => ({}) },
  running: { type: Boolean, default: false },
  totalMs: { type: Number, default: 0 }
})
defineEmits(['toggle'])

const viewMode = ref('live')
const recentTraces = ref([])
const recentLoading = ref(false)
const recentError = ref('')
const recentLoaded = ref(false)
const traceOpen = ref(false)
const selectedTraceId = ref('')
const traceDetail = ref(null)
const traceLoading = ref(false)
const traceError = ref('')

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

function errorText(e, fallback = '加载失败，请稍后重试') {
  if (e?.status === 403) return '无权限（需要管理员）'
  if (e?.status === 401) return '登录已过期，请重新登录'
  return e?.message || fallback
}

function fmtMs(value) {
  const n = Number(value)
  if (!Number.isFinite(n) || n < 0) return '—'
  if (n >= 1000) return (n / 1000).toFixed(2) + 's'
  return Math.round(n) + 'ms'
}

function traceIdOf(t) {
  return t?.trace_id || t?.traceId || t?.id || ''
}

function queryOf(t) {
  return t?.user_query || t?.query || t?.queryText || '未命名问题'
}

function traceTotalMs(t) {
  return t?.total_ms ?? t?.totalMs ?? t?.durationMs ?? 0
}

function traceTokens(t) {
  return t?.total_tokens ?? t?.totalTokens ?? null
}

function traceStatusClass(t) {
  const s = String(t?.status || '').toLowerCase()
  if (['success', 'ok', 'completed'].includes(s)) return 'ok'
  if (['error', 'fail', 'failed', 'timeout'].includes(s)) return 'danger'
  return 'neutral'
}

function traceStatusText(t) {
  const s = String(t?.status || '').toLowerCase()
  if (['success', 'ok', 'completed'].includes(s)) return '成功'
  if (['error', 'fail', 'failed', 'timeout'].includes(s)) return '失败'
  return t?.status || '未知'
}

function shortId(value, max = 11) {
  const text = String(value || '')
  if (!text) return '无 ID'
  return text.length > max ? text.slice(0, max) + '…' : text
}

function showHistory() {
  viewMode.value = 'history'
  if (!recentLoaded.value || recentError.value) loadRecent(false)
  else if (props.open && !recentTraces.value.length) loadRecent(false)
}

async function loadRecent(force = false) {
  if (recentLoading.value) return
  if (recentLoaded.value && !force && recentTraces.value.length) return
  recentLoading.value = true
  recentError.value = ''
  try {
    const data = await get('/admin/trace/recent', { limit: 8 })
    const list = Array.isArray(data) ? data : (data?.items || data?.traces || [])
    recentTraces.value = Array.isArray(list) ? list : []
    recentLoaded.value = true
  } catch (e) {
    recentError.value = errorText(e, '最近链路加载失败')
  } finally {
    recentLoading.value = false
  }
}

async function openTrace(trace) {
  const id = traceIdOf(trace)
  if (!id) return
  traceOpen.value = true
  selectedTraceId.value = id
  await loadTrace(id)
}

async function loadTrace(id) {
  if (!id) return
  traceLoading.value = true
  traceError.value = ''
  traceDetail.value = null
  try {
    traceDetail.value = await get(`/admin/trace/${encodeURIComponent(id)}`)
  } catch (e) {
    traceError.value = errorText(e, '链路详情加载失败')
  } finally {
    traceLoading.value = false
  }
}

function closeTrace() {
  traceOpen.value = false
  selectedTraceId.value = ''
  traceDetail.value = null
  traceError.value = ''
}

function handleKeydown(event) {
  if (event.key === 'Escape' && traceOpen.value) closeTrace()
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
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

.lp-tabs {
  display: flex;
  gap: 3px;
  padding: 2px;
  margin-bottom: 12px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--bg-step);
}
.lp-tabs button {
  flex: 1;
  padding: 4px 6px;
  border-radius: calc(var(--radius-sm) - 1px);
  font-size: 0.6875rem;
  color: var(--text-tertiary);
}
.lp-tabs button.on {
  color: var(--text-primary);
  background: var(--bg-sidebar);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
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

.lp-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 0.625rem;
  letter-spacing: 0.12em;
  color: var(--text-tertiary);
}
.lp-history-head button {
  padding: 2px 8px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-xs);
  color: var(--text-tertiary);
  font-size: 0.625rem;
}
.lp-history-head button:hover { color: var(--accent); border-color: var(--accent-border); }
.lp-trace-list { display: flex; flex-direction: column; gap: 6px; }
.lp-trace-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 8px 9px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  background: var(--bg-step);
  text-align: left;
  color: inherit;
  transition: border-color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);
}
.lp-trace-item:hover { border-color: var(--accent-border); background: var(--bg-hover); }
.tr-top { display: flex; align-items: center; gap: 6px; min-width: 0; }
.tr-query { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.6875rem; color: var(--text-secondary); }
.tr-status { flex-shrink: 0; padding: 0 5px; border-radius: var(--radius-full); font-size: 0.5625rem; }
.tr-status.ok { color: var(--success); background: var(--success-bg); }
.tr-status.danger { color: var(--danger); background: var(--danger-bg); }
.tr-status.neutral { color: var(--text-tertiary); background: var(--bg-step); }
.tr-meta { display: flex; flex-wrap: wrap; gap: 8px; font-size: 0.5625rem; color: var(--text-tertiary); }
.tr-meta .mono { color: var(--accent); }

.trace-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.56);
  backdrop-filter: blur(3px);
}
.trace-dialog {
  width: min(980px, calc(100vw - 48px));
  max-height: min(860px, calc(100vh - 48px));
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  background: var(--bg-app);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
.td-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 20px;
  border-bottom: 1px solid var(--rule);
  background: var(--bg-sidebar);
}
.td-title-wrap { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.td-kicker { font-size: 0.5625rem; letter-spacing: 0.16em; color: var(--text-tertiary); }
.td-title { font-family: var(--font-serif); font-size: 1rem; color: var(--text-primary); }
.td-id { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.625rem; color: var(--text-tertiary); }
.td-close {
  flex-shrink: 0;
  padding: 5px 12px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--text-secondary);
}
.td-close:hover { color: var(--accent); border-color: var(--accent-border); }
.td-body { flex: 1; overflow-y: auto; padding: 18px; }

.trace-dialog-enter-active, .trace-dialog-leave-active { transition: opacity 180ms var(--ease-out); }
.trace-dialog-enter-active .trace-dialog, .trace-dialog-leave-active .trace-dialog { transition: transform 200ms var(--ease-out); }
.trace-dialog-enter-from, .trace-dialog-leave-to { opacity: 0; }
.trace-dialog-enter-from .trace-dialog, .trace-dialog-leave-to .trace-dialog { transform: scale(0.97) translateY(8px); }

@media (max-width: 1100px) {
  .live-panel { display: none; }
}
@media (max-width: 700px) {
  .trace-overlay { padding: 10px; }
  .trace-dialog { width: calc(100vw - 20px); max-height: calc(100vh - 20px); }
  .td-body { padding: 12px; }
}
</style>
