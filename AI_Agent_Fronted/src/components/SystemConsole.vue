<template>
  <div class="console-page">
    <header class="cn-head">
      <button class="back-btn" @click="$router.push('/')">返回对话</button>
      <h1>系统能力控制台</h1>
      <div class="cn-tabs">
        <button :class="{ on: tab === 'caps' }" @click="tab = 'caps'">能力矩阵</button>
        <button :class="{ on: tab === 'demo' }" @click="tab = 'demo'">一键演示</button>
        <button :class="{ on: tab === 'eval' }" @click="tab = 'eval'">评测报告</button>
      </div>
    </header>

    <section v-if="tab === 'caps'" class="cn-body">
      <div class="caps-summary" v-if="caps">
        已实现 <b>{{ caps.implemented }}</b> / {{ caps.total }} 项能力
        <span class="muted">（数据实时来自后端，刷新即更新）</span>
      </div>
      <div class="caps-grid">
        <article v-for="c in caps?.items || []" :key="c.name" class="cap-card" :class="c.status">
          <div class="cap-top">
            <span class="cap-cat">{{ c.category }}</span>
            <span class="cap-status" :class="c.status">
              {{ c.status === 'implemented' ? '已实现' : (c.status === 'partial' ? '部分' : '计划中') }}
            </span>
          </div>
          <h3 class="cap-name">{{ c.name }}</h3>
          <p class="cap-evidence">{{ c.evidence }}</p>
          <div class="cap-metrics" v-if="c.metrics && Object.keys(c.metrics).length">
            <span v-for="(v, k) in c.metrics" :key="k" class="cap-metric">
              <span class="cm-k">{{ k }}</span>
              <span class="cm-v mono">{{ v === true ? '有' : (v === false ? '无' : v) }}</span>
            </span>
          </div>
        </article>
      </div>
    </section>

    <section v-if="tab === 'demo'" class="cn-body">
      <div class="demo-head">
        <p class="demo-desc">
          一键跑完 8 项能力验证，输出「预期 vs 实测」报告。每项都包含真实数字。
        </p>
        <button class="run-btn" :disabled="demoRunning" @click="runDemo">
          {{ demoRunning ? '演示运行中…' : '运行完整演示' }}
        </button>
      </div>
      <div v-if="demo" class="demo-result">
        <div class="demo-summary" :class="demo.passed === demo.total ? 'ok' : 'warn'">
          {{ demo.passed }} / {{ demo.total }} 项通过 · 耗时 {{ demo.elapsedMs }}ms · 构建 {{ demo.gitCommit }}
        </div>
        <table class="demo-table">
          <thead>
            <tr><th>#</th><th>能力</th><th>预期</th><th>实测</th><th>结果</th><th>耗时</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in demo.results" :key="i" :class="{ fail: !r.pass }">
              <td class="mono">{{ i + 1 }}</td>
              <td>{{ r.name }}</td>
              <td class="muted">{{ r.expected }}</td>
              <td class="mono">{{ r.actual }}</td>
              <td><span class="badge" :class="r.pass ? 'ok' : 'fail'">{{ r.pass ? '通过' : '失败' }}</span></td>
              <td class="mono muted">{{ r.ms }}ms</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="tab === 'eval'" class="cn-body">
      <div class="eval-controls">
        <label>样本数 <input v-model.number="evalSize" type="number" min="10" max="100" /></label>
        <label>模式
          <select v-model="evalMode">
            <option value="paraphrase">paraphrase（同义改写，真实难度）</option>
            <option value="question">question（开卷，仅对照）</option>
            <option value="answer">answer（答案片段）</option>
          </select>
        </label>
        <button class="run-btn" :disabled="evalRunning" @click="runEval">
          {{ evalRunning ? '评测中…' : '运行评测' }}
        </button>
      </div>
      <div v-if="evalResult && !evalResult.error" class="eval-cards">
        <div class="ecard"><span class="ec-k">Recall@5</span><span class="ec-v mono">{{ evalResult.recallAtK.toFixed(3) }}</span></div>
        <div class="ecard"><span class="ec-k">MRR</span><span class="ec-v mono">{{ evalResult.mrr.toFixed(3) }}</span></div>
        <div class="ecard"><span class="ec-k">nDCG@5</span><span class="ec-v mono">{{ evalResult.ndcgAtK.toFixed(3) }}</span></div>
        <div class="ecard"><span class="ec-k">样本 / 耗时</span><span class="ec-v mono">{{ evalResult.sampleCount }} / {{ evalResult.elapsedMs }}ms</span></div>
      </div>
      <div v-if="evalHistory.length" class="eval-history">
        <div class="eh-title">历史对比</div>
        <table class="demo-table">
          <thead><tr><th>版本</th><th>样本</th><th>Recall@5</th><th>MRR</th><th>nDCG@5</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="h in evalHistory" :key="h.run_name + h.created_at">
              <td class="mono">{{ h.run_name }}</td>
              <td class="mono">{{ h.sample_count }}</td>
              <td class="mono">{{ fmt(h.recall_at_5) }}</td>
              <td class="mono">{{ fmt(h.mrr) }}</td>
              <td class="mono">{{ fmt(h.ndcg_at_5) }}</td>
              <td class="mono muted">{{ String(h.created_at).slice(0, 19).replace('T', ' ') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="evalResult && evalResult.details" class="eval-details">
        <div class="eh-title">明细（{{ evalResult.details.length }} 条）</div>
        <div v-for="d in evalResult.details" :key="d.id" class="ed-row">
          <span class="ed-badge" :class="d.recall > 0 ? 'ok' : 'fail'">{{ d.recall > 0 ? '命中' : '未命中' }}</span>
          <span class="ed-q">{{ d.query }}</span>
          <span class="ed-m mono">recall={{ d.recall }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const tab = ref('caps')
const caps = ref(null)
const demo = ref(null)
const demoRunning = ref(false)
const evalResult = ref(null)
const evalHistory = ref([])
const evalRunning = ref(false)
const evalSize = ref(30)
const evalMode = ref('paraphrase')

function headers() {
  const t = localStorage.getItem('token')
  return { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) }
}

async function post(url, body) {
  const res = await fetch(url, { method: 'POST', headers: headers(), body: JSON.stringify(body || {}) })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json()
}

async function get(url) {
  const res = await fetch(url, { headers: headers() })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json()
}

async function loadCaps() {
  try { caps.value = await get('/api/system/capabilities') } catch (e) { /* ignore */ }
}

async function loadHistory() {
  try { evalHistory.value = await get('/api/admin/eval/history?limit=10') } catch (e) { /* ignore */ }
}

async function runDemo() {
  demoRunning.value = true
  demo.value = null
  try { demo.value = await post('/api/admin/demo/run', {}) } catch (e) {
    demo.value = { total: 1, passed: 0, elapsedMs: 0, gitCommit: '-', results: [{ name: '演示请求失败', expected: '-', actual: e.message, pass: false, ms: 0 }] }
  } finally { demoRunning.value = false }
}

async function runEval() {
  evalRunning.value = true
  evalResult.value = null
  try {
    evalResult.value = await post('/api/admin/eval/auto', {
      size: evalSize.value, topK: 5, mode: evalMode.value,
      runName: evalMode.value + '-' + Date.now()
    })
    await loadHistory()
  } catch (e) {
    evalResult.value = { error: e.message }
  } finally { evalRunning.value = false }
}

function fmt(v) { return v == null ? '-' : Number(v).toFixed(3) }

onMounted(() => { loadCaps(); loadHistory() })
</script>

<style scoped>
.console-page { height: 100dvh; overflow-y: auto; padding: 24px 32px 60px; background: var(--bg-app); }
.cn-head { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; padding-bottom: 14px; border-bottom: 1px solid var(--rule); }
.back-btn { padding: 6px 12px; border: 1px solid var(--border-primary); border-radius: var(--radius-sm); color: var(--text-secondary); font-size: 0.8125rem; }
.back-btn:hover { color: var(--accent); border-color: var(--accent-border); }
h1 { font-family: var(--font-serif); font-size: 1.375rem; }
.cn-tabs { margin-left: auto; display: flex; gap: 6px; }
.cn-tabs button { padding: 6px 14px; border: 1px solid var(--border-secondary); border-radius: var(--radius-sm); color: var(--text-secondary); font-size: 0.8125rem; }
.cn-tabs button.on { background: var(--bg-active); color: var(--text-primary); border-color: var(--accent-border); }

.caps-summary { font-size: 0.8125rem; color: var(--text-secondary); margin-bottom: 14px; }
.caps-summary b { color: var(--accent); font-size: 1rem; }
.muted { color: var(--text-tertiary); }
.caps-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.cap-card { border: 1px solid var(--rule); border-radius: var(--radius); padding: 14px; background: var(--bg-sidebar); }
.cap-card.planned { opacity: 0.55; }
.cap-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.cap-cat { font-size: 0.5625rem; letter-spacing: 0.14em; text-transform: uppercase; color: var(--text-tertiary); }
.cap-status { font-size: 0.625rem; padding: 1px 7px; border-radius: var(--radius-full); }
.cap-status.implemented { background: var(--success-bg); color: var(--success); }
.cap-status.planned { background: var(--bg-step); color: var(--text-tertiary); }
.cap-name { font-family: var(--font-serif); font-size: 0.9375rem; margin-bottom: 6px; }
.cap-evidence { font-size: 0.75rem; line-height: 1.6; color: var(--text-tertiary); margin-bottom: 8px; }
.cap-metrics { display: flex; flex-wrap: wrap; gap: 8px 14px; }
.cap-metric { display: inline-flex; gap: 4px; font-size: 0.6875rem; }
.cm-k { color: var(--text-tertiary); }
.cm-v { color: var(--accent); }
.mono { font-family: var(--font-mono); }

.demo-head { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.demo-desc { flex: 1; font-size: 0.8125rem; line-height: 1.7; color: var(--text-secondary); }
.run-btn { padding: 8px 18px; border-radius: var(--radius-sm); background: var(--accent); color: var(--text-inverse); font-size: 0.8125rem; }
.run-btn:disabled { opacity: 0.6; }
.demo-summary { padding: 10px 14px; border-radius: var(--radius-sm); font-size: 0.8125rem; margin-bottom: 12px; }
.demo-summary.ok { background: var(--success-bg); color: var(--success); }
.demo-summary.warn { background: var(--danger-bg); color: var(--danger); }
.demo-table { width: 100%; border-collapse: collapse; font-size: 0.75rem; }
.demo-table th { text-align: left; padding: 6px 8px; color: var(--text-tertiary); font-weight: 500; border-bottom: 1px solid var(--rule); }
.demo-table td { padding: 6px 8px; border-bottom: 1px solid var(--rule); color: var(--text-secondary); }
.demo-table tr.fail td { color: var(--danger); }
.badge { padding: 1px 8px; border-radius: var(--radius-full); font-size: 0.625rem; }
.badge.ok { background: var(--success-bg); color: var(--success); }
.badge.fail { background: var(--danger-bg); color: var(--danger); }

.eval-controls { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; flex-wrap: wrap; }
.eval-controls label { display: inline-flex; align-items: center; gap: 6px; font-size: 0.75rem; color: var(--text-tertiary); }
.eval-controls input, .eval-controls select { padding: 5px 8px; border: 1px solid var(--border-input); border-radius: var(--radius-xs); background: var(--bg-input); color: var(--text-primary); font-size: 0.75rem; }
.eval-cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }
.ecard { border: 1px solid var(--rule); border-radius: var(--radius); padding: 14px; background: var(--bg-sidebar); display: flex; flex-direction: column; gap: 6px; }
.ec-k { font-size: 0.625rem; letter-spacing: 0.12em; text-transform: uppercase; color: var(--text-tertiary); }
.ec-v { font-size: 1.5rem; font-family: var(--font-serif); color: var(--text-primary); }
.eval-history, .eval-details { margin-top: 18px; }
.eh-title { font-size: 0.6875rem; letter-spacing: 0.12em; text-transform: uppercase; color: var(--text-tertiary); margin-bottom: 8px; }
.ed-row { display: flex; align-items: center; gap: 10px; padding: 5px 0; border-bottom: 1px solid var(--rule); font-size: 0.75rem; }
.ed-badge { font-size: 0.5625rem; padding: 1px 6px; border-radius: var(--radius-full); flex-shrink: 0; }
.ed-badge.ok { background: var(--success-bg); color: var(--success); }
.ed-badge.fail { background: var(--danger-bg); color: var(--danger); }
.ed-q { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-secondary); }
.ed-m { color: var(--text-tertiary); font-size: 0.6875rem; }

@media (max-width: 900px) { .eval-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
