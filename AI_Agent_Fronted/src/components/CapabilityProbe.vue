<template>
  <section class="exp-card wide">
    <div class="card-head">
      <h2>能力自证：直接调后端接口</h2>
      <span class="tag">原始响应对照</span>
    </div>
    <p class="card-desc">
      选一个能力，前端会把后端返回的**原始 JSON** 原样贴出来，同时把关键字段渲染成可读形式。
      这些数字来自数据库实时查询，刷新后会变（尤其累计计数），前端无法伪造。
    </p>

    <div class="controls">
      <select v-model="selected" class="q-select">
        <option v-for="p in probes" :key="p.key" :value="p.key">{{ p.label }}</option>
      </select>
      <button class="run-btn" :disabled="running" @click="run">{{ running ? '请求中…' : '调用后端' }}</button>
      <span v-if="elapsed" class="muted mono">耗时 {{ elapsed }}ms</span>
      <span v-if="status" class="status" :class="status === 200 ? 'ok' : 'fail'">HTTP {{ status }}</span>
    </div>

    <div v-if="summary" class="probe-summary">{{ summary }}</div>

    <div v-if="raw" class="raw-box">
      <div class="raw-title">原始响应（{{ currentProbe.method }} {{ currentProbe.path }}）</div>
      <pre class="raw-json">{{ raw }}</pre>
    </div>

    <div class="probe-hint">
      <div class="ph-title">这些接口证明什么</div>
      <div v-for="p in probes" :key="'h'+p.key" class="ph-row">
        <span class="ph-name">{{ p.label }}</span>
        <span class="ph-desc">{{ p.proves }}</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed } from 'vue'

const probes = [
  { key: 'sysinfo', label: '系统自证信息', method: 'GET', path: '/api/system/info',
    proves: '累计请求数/LLM 调用数只增不减；表行数来自数据库 COUNT(*)，刷新会变',
    summary: d => `commit=${d.gitCommit} 运行=${d.uptimeHuman} 累计请求=${d.counters.chatRequests} LLM=${d.counters.llmCalls} 向量=${d.tables.csVectorStore}` },
  { key: 'caps', label: '能力清单', method: 'GET', path: '/api/system/capabilities',
    proves: '每项能力的 metrics 都是后端实时算的（DB COUNT / 内存计数器 / pg_indexes 检查）',
    summary: d => `已实现 ${d.implemented}/${d.total}，生成时间 ${d.generatedAt}` },
  { key: 'resilience', label: '熔断/限流状态', method: 'GET', path: '/api/admin/experiments/resilience',
    proves: '熔断器三态与限流拒绝数来自内存计数器，手动熔断后重新请求会变化',
    summary: d => `熔断=${d.circuitState} 失败率=${d.failureRate}% 限流拒绝=${d.rateLimitRejected}` },
  { key: 'kgstats', label: '知识图谱统计', method: 'GET', path: '/api/admin/kg/stats',
    proves: '实体/关系数来自 kg_entity / kg_relation 表的 COUNT(*)，可自己去数据库核对',
    summary: d => `实体=${d.entities} 提及=${d.mentions} 关系=${d.relations}` },
  { key: 'eval', label: '评测历史', method: 'GET', path: '/api/admin/eval/history?limit=5',
    proves: '每次评测结果落 eval_run 表，可看到不同版本的 Recall/MRR/nDCG 变化',
    summary: d => Array.isArray(d) ? `${d.length} 次评测记录` : '' },
  { key: 'fault', label: '故障注入状态', method: 'GET', path: '/api/admin/experiments/fault/status',
    proves: '注入故障后这里会显示剩余秒数，到点自动清空（不会忘记关）',
    summary: d => `当前故障: ${JSON.stringify(d.activeFaults)}` },
  { key: 'traces', label: '链路追踪记录', method: 'GET', path: '/api/admin/trace/recent?limit=3',
    proves: '每次对话的 span 落库，可看到真实耗时分解',
    summary: d => Array.isArray(d) ? `${d.length} 条 trace` : '' }
]

const selected = ref('sysinfo')
const raw = ref('')
const summary = ref('')
const status = ref(null)
const elapsed = ref(0)
const running = ref(false)

const currentProbe = computed(() => probes.find(p => p.key === selected.value) || probes[0])

function headers() {
  const t = localStorage.getItem('token')
  return { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) }
}

async function run() {
  running.value = true
  raw.value = ''
  summary.value = ''
  status.value = null
  const t0 = performance.now()
  try {
    const p = currentProbe.value
    const res = await fetch(p.path, { headers: headers() })
    status.value = res.status
    const text = await res.text()
    try {
      const json = JSON.parse(text)
      raw.value = JSON.stringify(json, null, 2)
      summary.value = p.summary ? p.summary(json) : ''
    } catch (e) {
      raw.value = text
      summary.value = '（非 JSON 响应）'
    }
  } catch (e) {
    raw.value = '请求失败: ' + e.message
    summary.value = ''
  } finally {
    elapsed.value = Math.round(performance.now() - t0)
    running.value = false
  }
}
</script>

<style scoped>
.q-select { padding: 7px 10px; border: 1px solid var(--border-input); border-radius: var(--radius-sm); background: var(--bg-input); color: var(--text-primary); font-size: 0.8125rem; min-width: 220px; }
.muted { color: var(--text-tertiary); font-size: 0.75rem; }
.status { font-size: 0.6875rem; padding: 1px 8px; border-radius: var(--radius-full); font-family: var(--font-mono); }
.status.ok { background: var(--success-bg); color: var(--success); }
.status.fail { background: var(--danger-bg); color: var(--danger); }
.probe-summary { margin: 10px 0 6px; padding: 8px 12px; border-left: 2px solid var(--accent); background: var(--bg-step); font-family: var(--font-mono); font-size: 0.75rem; color: var(--text-secondary); }
.raw-box { margin-top: 10px; border: 1px solid var(--rule); border-radius: var(--radius-sm); overflow: hidden; }
.raw-title { padding: 7px 10px; font-size: 0.6875rem; color: var(--text-tertiary); background: var(--bg-step); border-bottom: 1px solid var(--rule); }
.raw-json { margin: 0; padding: 10px; max-height: 300px; overflow: auto; font-family: var(--font-mono); font-size: 0.6875rem; line-height: 1.6; color: var(--text-secondary); }
.probe-hint { margin-top: 14px; padding-top: 10px; border-top: 1px solid var(--rule); }
.ph-title { font-size: 0.625rem; letter-spacing: 0.12em; text-transform: uppercase; color: var(--text-tertiary); margin-bottom: 6px; }
.ph-row { display: flex; gap: 12px; padding: 3px 0; font-size: 0.6875rem; }
.ph-name { width: 120px; flex-shrink: 0; color: var(--text-secondary); }
.ph-desc { color: var(--text-tertiary); line-height: 1.6; }
</style>
