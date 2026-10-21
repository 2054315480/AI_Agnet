<template>
  <div class="exp-page">
    <header class="exp-head">
      <button class="back-btn" @click="$router.push('/')">返回对话</button>
      <h1>实验中心</h1>
      <span class="exp-sub">点一下，用真实数字验证机制确实生效</span>
    </header>

    <div class="exp-grid">
      <!-- 实验 1：缓存击穿单飞 -->
      <section class="exp-card">
        <div class="card-head">
          <h2>缓存击穿单飞</h2>
          <span class="tag">Redis 分布式锁</span>
        </div>
        <p class="card-desc">
          清空缓存后，用 N 个线程同时问同一个问题。预期只有 1 个线程真正回源，其余走缓存。
        </p>
        <div class="controls">
          <label>并发数 <input v-model.number="concurrency" type="number" min="2" max="50" /></label>
          <button class="run-btn" :disabled="running1" @click="runStampede">
            {{ running1 ? '运行中…' : '运行实验' }}
          </button>
        </div>
        <div v-if="r1" class="result">
          <div class="verdict" :class="r1.pass ? 'ok' : 'fail'">
            {{ r1.pass ? '通过' : '未通过' }}
          </div>
          <table class="kv-table">
            <tr><td>请求数</td><td class="mono">{{ r1.requests }}</td></tr>
            <tr><td>真实回源次数</td><td class="mono strong">{{ r1.realBackendCalls }}</td></tr>
            <tr><td>缓存命中</td><td class="mono">{{ r1.cacheHits }}</td></tr>
            <tr><td>锁获取次数</td><td class="mono">{{ r1.lockAcquiredDelta }}</td></tr>
            <tr><td>总耗时</td><td class="mono">{{ r1.totalMs }}ms</td></tr>
            <tr><td>串行估算</td><td class="mono muted">{{ r1.serialEstimateMs }}ms</td></tr>
            <tr><td>预期</td><td class="muted">{{ r1.expectation }}</td></tr>
          </table>
        </div>
      </section>

      <!-- 实验 2：LLM 降级演练 -->
      <section class="exp-card">
        <div class="card-head">
          <h2>LLM 降级演练</h2>
          <span class="tag warn">故障注入</span>
        </div>
        <p class="card-desc">
          注入 LLM 故障后发消息，系统应返回 200 并标记降级，而不是直接报错。到点自动失效。
        </p>
        <div class="controls">
          <label>持续 <input v-model.number="faultSec" type="number" min="10" max="300" /> 秒</label>
          <button class="run-btn warn" :disabled="running2" @click="injectFault">
            {{ running2 ? '注入中…' : '注入故障' }}
          </button>
          <button class="ghost-btn" @click="clearFault">清除</button>
        </div>
        <div v-if="faults && Object.keys(faults).length" class="fault-bar">
          当前故障：<span v-for="(v,k) in faults" :key="k" class="fault-item">{{ k }} (剩余 {{ v }})</span>
        </div>
        <div v-if="r2" class="result">
          <div class="hint-box">{{ r2.hint }}</div>
        </div>
      </section>

      <!-- 实验 3：缓存命中率 -->
      <section class="exp-card">
        <div class="card-head">
          <h2>缓存命中率</h2>
          <span class="tag">回放真实问题</span>
        </div>
        <p class="card-desc">
          用一批高频问题各回放一次，统计 L1/L2 命中率。用于评估缓存在真实分布下的收益。
        </p>
        <div class="controls">
          <button class="run-btn" :disabled="running3" @click="runHitRate">
            {{ running3 ? '运行中…' : '运行实验' }}
          </button>
        </div>
        <div v-if="r3" class="result">
          <table class="kv-table">
            <tr><td>问题数</td><td class="mono">{{ r3.queries }}</td></tr>
            <tr><td>命中</td><td class="mono strong">{{ r3.hits }}</td></tr>
            <tr><td>命中率</td><td class="mono strong">{{ (r3.hitRate * 100).toFixed(1) }}%</td></tr>
            <tr><td>L1 / L2</td><td class="mono">{{ r3.l1Hits }} / {{ r3.l2Hits }}</td></tr>
          </table>
        </div>
      </section>

      <!-- 实验 5：系统韧性 -->
      <section class="exp-card">
        <div class="card-head">
          <h2>系统韧性</h2>
          <span class="tag">熔断 + 限流</span>
        </div>
        <p class="card-desc">
          熔断器三态（CLOSED/OPEN/HALF_OPEN）与令牌桶限流的实时状态。可手动打开熔断观察降级。
        </p>
        <div class="controls">
          <button class="run-btn warn" @click="circuitControl('open')">手动熔断</button>
          <button class="ghost-btn" @click="circuitControl('reset')">恢复</button>
        </div>
        <div v-if="resilience" class="result">
          <table class="kv-table">
            <tr><td>熔断状态</td><td class="mono strong">{{ resilience.circuitState }}</td></tr>
            <tr><td>窗口失败率</td><td class="mono">{{ resilience.failureRate }}%</td></tr>
            <tr><td>窗口请求数</td><td class="mono">{{ resilience.windowTotal }}</td></tr>
            <tr><td>熔断拒绝数</td><td class="mono">{{ resilience.rejectedByCircuit }}</td></tr>
            <tr><td>限流拒绝数</td><td class="mono">{{ resilience.rateLimitRejected }}</td></tr>
            <tr><td>限流桶数量</td><td class="mono">{{ resilience.rateLimitBuckets }}</td></tr>
          </table>
        </div>
      </section>

      <!-- 实验 4：检索解释 -->
      <section class="exp-card wide">
        <div class="card-head">
          <h2>检索解释</h2>
          <span class="tag">RAG 透明化</span>
        </div>
        <p class="card-desc">
          输入一个问题，看每一路检索召回了什么、各自多少分、RRF 融合后怎么排、最终谁入选。
        </p>
        <div class="controls">
          <input v-model="explainQuery" class="q-input" placeholder="输入问题，如：耳机左耳没声音了还能保修吗" @keyup.enter="runExplain" />
          <button class="run-btn" :disabled="running4" @click="runExplain">
            {{ running4 ? '检索中…' : '解释这次检索' }}
          </button>
        </div>

        <div v-if="r4" class="explain-result">
          <div class="explain-meta">
            <span>RRF k = <b class="mono">{{ r4.rrfK }}</b></span>
            <span>向量阈值 = <b class="mono">{{ r4.similarityThreshold }}</b></span>
            <span>BM25 <b class="mono">{{ r4.timings.bm25Ms }}ms</b></span>
            <span>向量 <b class="mono">{{ r4.timings.vectorMs }}ms</b></span>
            <span>融合 <b class="mono">{{ r4.timings.fuseMs }}ms</b></span>
            <span>合计 <b class="mono">{{ r4.timings.totalMs }}ms</b></span>
          </div>

          <div class="explain-cols">
            <div class="ex-col">
              <div class="ex-title">BM25 候选（{{ r4.bm25.length }}）</div>
              <div v-for="(it, i) in r4.bm25" :key="'b'+i" class="ex-item">
                <span class="ex-rank">{{ i + 1 }}</span>
                <span class="ex-text" :title="it.preview">{{ it.preview }}</span>
                <span class="ex-score mono">{{ it.score.toFixed(2) }}</span>
              </div>
              <div v-if="!r4.bm25.length" class="ex-empty">无命中</div>
            </div>

            <div class="ex-col">
              <div class="ex-title">向量候选（{{ r4.vector.length }}）</div>
              <div v-for="(it, i) in r4.vector" :key="'v'+i" class="ex-item">
                <span class="ex-rank">{{ i + 1 }}</span>
                <span class="ex-text" :title="it.preview">{{ it.preview }}</span>
                <span class="ex-score mono">{{ it.score.toFixed(3) }}</span>
              </div>
              <div v-if="!r4.vector.length" class="ex-empty">无命中</div>
            </div>

            <div class="ex-col">
              <div class="ex-title">RRF 融合后（{{ r4.fused.length }}）</div>
              <div v-for="(it, i) in r4.fused" :key="'f'+i" class="ex-item fused">
                <span class="ex-rank">{{ i + 1 }}</span>
                <span class="ex-text" :title="it.preview">{{ it.preview }}</span>
                <span class="ex-score mono">{{ it.score.toFixed(4) }}</span>
              </div>
              <div v-if="!r4.fused.length" class="ex-empty">无命中</div>
            </div>
          </div>
          <p class="ex-note">
            说明：BM25 分与余弦相似度不是一个量纲，所以 RRF 只用排名不用分数；阈值只作用于向量路。
          </p>
        </div>
      </section>

      <!-- 实验 6：知识图谱可视化 -->
      <KgGraphPanel />

      <!-- 实验 7：能力自证（原始响应对照） -->
      <CapabilityProbe />

      <!-- 自证条 -->
      <section class="exp-card wide">
        <div class="card-head">
          <h2>系统自证</h2>
          <span class="tag">真实运行数据</span>
        </div>
        <div v-if="info" class="proof-grid">
          <div class="pg-item"><span class="pg-k">构建</span><span class="pg-v mono">{{ info.gitCommit }}</span></div>
          <div class="pg-item"><span class="pg-k">运行</span><span class="pg-v">{{ info.uptimeHuman }}</span></div>
          <div class="pg-item"><span class="pg-k">累计请求</span><span class="pg-v mono">{{ info.counters.chatRequests }}</span></div>
          <div class="pg-item"><span class="pg-k">LLM 调用</span><span class="pg-v mono">{{ info.counters.llmCalls }}</span></div>
          <div class="pg-item"><span class="pg-k">L1/L2 命中</span><span class="pg-v mono">{{ info.counters.cacheL1Hits }} / {{ info.counters.cacheL2Hits }}</span></div>
          <div class="pg-item"><span class="pg-k">锁获取</span><span class="pg-v mono">{{ info.counters.lockAcquired }}</span></div>
          <div class="pg-item"><span class="pg-k">链路追踪</span><span class="pg-v mono">{{ info.counters.traces }}</span></div>
          <div class="pg-item"><span class="pg-k">会话 / 消息</span><span class="pg-v mono">{{ info.tables.conversations }} / {{ info.tables.messages }}</span></div>
        </div>
        <p class="card-desc small">这些数字每 5 秒刷新一次，来自真实运行的实例，不是截图。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import KgGraphPanel from './KgGraphPanel.vue'
import CapabilityProbe from './CapabilityProbe.vue'

const concurrency = ref(20)
const faultSec = ref(60)
const running1 = ref(false)
const running2 = ref(false)
const running3 = ref(false)
const running4 = ref(false)
const explainQuery = ref('耳机左耳没声音了还能保修吗')
const r4 = ref(null)
const resilience = ref(null)
const r1 = ref(null)
const r2 = ref(null)
const r3 = ref(null)
const info = ref(null)
const faults = ref({})
let timer = null

function headers() {
  const t = localStorage.getItem('token')
  return { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) }
}

async function post(url, body) {
  const res = await fetch(url, { method: 'POST', headers: headers(), body: JSON.stringify(body || {}) })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.json()
}

async function runStampede() {
  running1.value = true
  r1.value = null
  try {
    r1.value = await post('/api/admin/experiments/stampede', { concurrency: concurrency.value })
  } catch (e) {
    r1.value = { pass: false, expectation: '请求失败: ' + e.message }
  } finally {
    running1.value = false
    loadInfo()
  }
}

async function injectFault() {
  running2.value = true
  try {
    r2.value = await post('/api/admin/experiments/fault', { target: 'llm', durationSec: faultSec.value })
    await loadFaults()
  } catch (e) {
    r2.value = { hint: '注入失败: ' + e.message }
  } finally {
    running2.value = false
  }
}

async function clearFault() {
  try {
    await post('/api/admin/experiments/fault/clear', {})
    await loadFaults()
    r2.value = null
  } catch (e) { /* ignore */ }
}

async function loadFaults() {
  try {
    const res = await fetch('/api/admin/experiments/fault/status', { headers: headers() })
    if (res.ok) faults.value = (await res.json()).activeFaults || {}
  } catch (e) { /* ignore */ }
}

async function runHitRate() {
  running3.value = true
  try {
    r3.value = await post('/api/admin/experiments/cache-hit-rate', {
      queries: ['退货政策是什么？', '退款多久到账？', '保修期多久？', '怎么取消订单？']
    })
  } catch (e) {
    r3.value = null
  } finally {
    running3.value = false
  }
}

async function runExplain() {
  if (!explainQuery.value.trim()) return
  running4.value = true
  r4.value = null
  try {
    r4.value = await post('/api/admin/retrieval/explain', { query: explainQuery.value.trim() })
  } catch (e) {
    r4.value = null
  } finally {
    running4.value = false
  }
}

async function loadResilience() {
  try {
    const res = await fetch('/api/admin/experiments/resilience', { headers: headers() })
    if (res.ok) resilience.value = await res.json()
  } catch (e) { /* ignore */ }
}

async function circuitControl(action) {
  try {
    await post('/api/admin/experiments/resilience/control', { action })
    await loadResilience()
  } catch (e) { /* ignore */ }
}

async function loadInfo() {
  try {
    const res = await fetch('/api/system/info')
    if (res.ok) info.value = await res.json()
  } catch (e) { /* ignore */ }
}

onMounted(() => {
  loadInfo()
  loadResilience()
  loadFaults()
  timer = setInterval(() => {
    if (document.visibilityState === 'visible') { loadInfo(); loadFaults(); loadResilience() }
  }, 5000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.exp-page { height: 100dvh; overflow-y: auto; padding: 24px 32px 60px; background: var(--bg-app); }
.exp-head { display: flex; align-items: center; gap: 14px; margin-bottom: 22px; padding-bottom: 14px; border-bottom: 1px solid var(--rule); }
.back-btn { padding: 6px 12px; border: 1px solid var(--border-primary); border-radius: var(--radius-sm); color: var(--text-secondary); font-size: 0.8125rem; }
.back-btn:hover { color: var(--accent); border-color: var(--accent-border); }
h1 { font-family: var(--font-serif); font-size: 1.375rem; }
.exp-sub { margin-left: auto; font-size: 0.75rem; color: var(--text-tertiary); }

.exp-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.exp-card { border: 1px solid var(--rule); border-radius: var(--radius); padding: 16px 18px; background: var(--bg-sidebar); }
.exp-card.wide { grid-column: 1 / -1; }
.card-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.card-head h2 { font-family: var(--font-serif); font-size: 1rem; }
.tag { font-size: 0.625rem; padding: 1px 7px; border: 1px solid var(--border-secondary); border-radius: var(--radius-full); color: var(--text-tertiary); }
.tag.warn { border-color: var(--accent-love-border); color: var(--accent-love); }
.card-desc { font-size: 0.8125rem; line-height: 1.7; color: var(--text-secondary); margin-bottom: 12px; }
.card-desc.small { font-size: 0.6875rem; color: var(--text-tertiary); margin: 10px 0 0; }

.controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.controls label { display: inline-flex; align-items: center; gap: 6px; font-size: 0.75rem; color: var(--text-tertiary); }
.controls input { width: 64px; padding: 4px 8px; border: 1px solid var(--border-input); border-radius: var(--radius-xs); background: var(--bg-input); color: var(--text-primary); font-family: var(--font-mono); }
.run-btn { padding: 7px 16px; border-radius: var(--radius-sm); background: var(--accent); color: var(--text-inverse); font-size: 0.8125rem; }
.run-btn:disabled { opacity: 0.6; }
.run-btn.warn { background: var(--accent-love); }
.ghost-btn { padding: 7px 12px; border: 1px solid var(--border-secondary); border-radius: var(--radius-sm); color: var(--text-secondary); font-size: 0.75rem; }

.result { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--rule); }
.verdict { display: inline-block; margin-bottom: 8px; padding: 2px 10px; border-radius: var(--radius-full); font-size: 0.6875rem; }
.verdict.ok { background: var(--success-bg); color: var(--success); }
.verdict.fail { background: var(--danger-bg); color: var(--danger); }
.kv-table { width: 100%; border-collapse: collapse; font-size: 0.75rem; }
.kv-table td { padding: 4px 0; border-bottom: 1px solid var(--rule); }
.kv-table td:first-child { color: var(--text-tertiary); width: 40%; }
.kv-table td:last-child { text-align: right; color: var(--text-secondary); }
.mono { font-family: var(--font-mono); }
.strong { color: var(--text-primary); font-weight: 600; }
.muted { color: var(--text-tertiary); }

.fault-bar { margin-top: 12px; padding: 8px 10px; border: 1px solid var(--accent-love-border); background: var(--accent-love-bg); border-radius: var(--radius-sm); font-size: 0.75rem; color: var(--accent-love); }
.fault-item { margin-left: 8px; font-family: var(--font-mono); }
.hint-box { padding: 10px 12px; border: 1px solid var(--border-secondary); border-radius: var(--radius-sm); font-size: 0.75rem; line-height: 1.7; color: var(--text-secondary); }

.proof-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px 18px; }
.pg-item { display: flex; flex-direction: column; gap: 2px; }
.pg-k { font-size: 0.625rem; letter-spacing: 0.1em; text-transform: uppercase; color: var(--text-tertiary); }
.pg-v { font-size: 0.9375rem; color: var(--text-primary); }

.q-input { flex: 1; min-width: 240px; padding: 7px 10px; border: 1px solid var(--border-input); border-radius: var(--radius-sm); background: var(--bg-input); color: var(--text-primary); font-size: 0.8125rem; }

.explain-result { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--rule); }
.explain-meta { display: flex; flex-wrap: wrap; gap: 16px; font-size: 0.6875rem; color: var(--text-tertiary); margin-bottom: 12px; }
.explain-meta b { color: var(--text-secondary); font-weight: 500; }
.explain-cols { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.ex-col { border: 1px solid var(--rule); border-radius: var(--radius-sm); padding: 10px; }
.ex-title { font-size: 0.625rem; letter-spacing: 0.1em; text-transform: uppercase; color: var(--text-tertiary); margin-bottom: 8px; }
.ex-item { display: flex; align-items: baseline; gap: 8px; padding: 4px 0; border-bottom: 1px solid var(--rule); font-size: 0.75rem; }
.ex-item:last-child { border-bottom: 0; }
.ex-item.fused .ex-text { color: var(--text-primary); }
.ex-rank { width: 16px; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 0.625rem; flex-shrink: 0; }
.ex-text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-secondary); }
.ex-score { flex-shrink: 0; color: var(--accent); font-size: 0.6875rem; }
.ex-empty { font-size: 0.75rem; color: var(--text-tertiary); padding: 4px 0; }
.ex-note { margin-top: 10px; font-size: 0.6875rem; color: var(--text-tertiary); }

@media (max-width: 900px) {
  .explain-cols { grid-template-columns: 1fr; }
  .exp-grid { grid-template-columns: 1fr; }
  .proof-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
