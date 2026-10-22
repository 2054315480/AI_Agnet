<template>
  <div class="exp-page">
    <header class="exp-head">
      <button class="back-btn" @click="$router.push('/')">返回对话</button>
      <h1>实验中心</h1>
      <span class="exp-sub">点一下，用真实数字验证机制确实生效</span>
    </header>

    <div class="exp-grid">
      <!-- 实验 1：幂等并发写入 -->
      <section class="exp-card">
        <div class="card-head">
          <h2>幂等并发写入</h2>
          <span class="tag accent">唯一索引兜底</span>
        </div>
        <p class="card-desc">
          N 个请求携带同一个幂等键并发写入。预期只有 1 次成功，其余被数据库唯一索引拒绝，表内只留 1 行。
        </p>
        <div class="controls">
          <label>尝试次数 <input v-model.number="idemAttempts" type="number" min="2" max="50" /></label>
          <button class="run-btn" :disabled="running.idem" @click="runIdempotency">
            {{ running.idem ? '运行中…' : '运行实验' }}
          </button>
        </div>

        <StateBlock v-if="running.idem" loading loading-text="并发请求写入中..." />
        <StateBlock v-else-if="errors.idem" :error="errors.idem" @retry="runIdempotency" />
        <StateBlock v-else-if="attempted.idem && !results.idem" empty empty-text="暂无幂等实验结果" />
        <div v-else-if="results.idem" class="result">
          <div class="result-top">
            <span class="result-meta">{{ resultMeta('idem') }}</span>
            <span class="pass-badge" :class="idemView.pass ? 'ok' : 'fail'">
              {{ idemView.pass ? 'PASS' : 'FAIL' }}
            </span>
          </div>

          <div class="kpi-grid four">
            <div class="kpi">
              <span class="kpi-k">尝试</span>
              <b class="kpi-v">{{ idemView.attempts }}</b>
              <span class="kpi-sub">次并发请求</span>
            </div>
            <div class="kpi success">
              <span class="kpi-k">成功写入</span>
              <b class="kpi-v">{{ idemView.success }}</b>
              <span class="kpi-sub">唯一键命中</span>
            </div>
            <div class="kpi danger">
              <span class="kpi-k">唯一索引拒绝</span>
              <b class="kpi-v">{{ idemView.rejected }}</b>
              <span class="kpi-sub">重复请求</span>
            </div>
            <div class="kpi">
              <span class="kpi-k">表内行数</span>
              <b class="kpi-v">{{ idemView.rows }}</b>
              <span class="kpi-sub">{{ idemView.rows === 1 ? '符合幂等预期' : '需关注' }}</span>
            </div>
          </div>

          <BaseChart :option="idemChart" height="100px" />
          <p class="viz-caption">每一次尝试的去向：成功写入与唯一索引拒绝堆叠展示。</p>

          <details class="raw-fold">
            <summary>原始响应（专业人员排查用）</summary>
            <pre>{{ prettyJson(results.idem) }}</pre>
          </details>
        </div>
      </section>

      <!-- 实验 2：并行 vs 串行检索基准 -->
      <section class="exp-card">
        <div class="card-head">
          <h2>并行 vs 串行基准</h2>
          <span class="tag accent">延迟分位数</span>
        </div>
        <p class="card-desc">
          分别串行、并行执行多路检索，对比 P50 / P95 延迟。柱越短越快，右侧大数字显示实际 speedup。
        </p>
        <div class="controls">
          <label>迭代 <input v-model.number="benchIterations" type="number" min="1" max="10" /> 次</label>
          <button class="run-btn" :disabled="running.bench" @click="runBench">
            {{ running.bench ? '基准运行中…' : '运行基准' }}
          </button>
        </div>

        <StateBlock v-if="running.bench" loading loading-text="正在采集 P50 / P95..." />
        <StateBlock v-else-if="errors.bench" :error="errors.bench" @retry="runBench" />
        <StateBlock v-else-if="attempted.bench && !results.bench" empty empty-text="暂无基准测试结果" />
        <div v-else-if="results.bench" class="result">
          <div class="result-top">
            <span class="result-meta">{{ resultMeta('bench') }}</span>
            <span class="bench-speedup" v-if="benchView.speedup > 0">
              并行提速 <b>{{ benchView.speedupText }}×</b>
            </span>
          </div>

          <div v-if="benchView.valid" class="chart-block">
            <BaseChart :option="benchChart" height="220px" />
          </div>
          <div v-else class="inline-empty">后端返回中未识别到 P50 / P95 字段，请展开原始响应核对契约。</div>

          <div class="kpi-grid four compact">
            <div class="kpi"><span class="kpi-k">串行 P50</span><b class="kpi-v small">{{ benchView.serialP50 }}<em>ms</em></b></div>
            <div class="kpi"><span class="kpi-k">并行 P50</span><b class="kpi-v small accent">{{ benchView.parallelP50 }}<em>ms</em></b></div>
            <div class="kpi"><span class="kpi-k">串行 P95</span><b class="kpi-v small">{{ benchView.serialP95 }}<em>ms</em></b></div>
            <div class="kpi"><span class="kpi-k">并行 P95</span><b class="kpi-v small accent">{{ benchView.parallelP95 }}<em>ms</em></b></div>
          </div>

          <details class="raw-fold">
            <summary>原始响应（专业人员排查用）</summary>
            <pre>{{ prettyJson(results.bench) }}</pre>
          </details>
        </div>
      </section>

      <!-- 实验 3：缓存击穿单飞 -->
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
          <button class="run-btn" :disabled="running.stampede" @click="runStampede">
            {{ running.stampede ? '运行中…' : '运行实验' }}
          </button>
        </div>

        <StateBlock v-if="running.stampede" loading loading-text="并发请求触发中..." />
        <StateBlock v-else-if="errors.stampede" :error="errors.stampede" @retry="runStampede" />
        <StateBlock v-else-if="attempted.stampede && !results.stampede" empty empty-text="暂无缓存实验结果" />
        <div v-else-if="results.stampede" class="result">
          <div class="result-top">
            <span class="result-meta">{{ resultMeta('stampede') }}</span>
            <span class="pass-badge" :class="stampedeView.pass ? 'ok' : 'fail'">
              {{ stampedeView.pass ? 'PASS' : 'FAIL' }}
            </span>
          </div>

          <div class="kpi-grid three">
            <div class="kpi">
              <span class="kpi-k">请求</span>
              <b class="kpi-v">{{ stampedeView.requests }}</b>
            </div>
            <div class="kpi success">
              <span class="kpi-k">真实回源</span>
              <b class="kpi-v">{{ stampedeView.realBackendCalls }}</b>
              <span class="kpi-sub">预期 ≤ 1</span>
            </div>
            <div class="kpi accent">
              <span class="kpi-k">缓存命中</span>
              <b class="kpi-v">{{ stampedeView.cacheHits }}</b>
            </div>
          </div>

          <BaseChart :option="stampedeChart" height="102px" />
          <div class="latency-compare">
            <div class="lc-row">
              <span class="lc-k">实测总耗时</span>
              <span class="lc-track"><i :style="{ width: barWidth(stampedeView.totalMs, stampedeView.maxLatency) + '%' }"></i></span>
              <span class="lc-v mono">{{ stampedeView.totalMs }}ms</span>
            </div>
            <div class="lc-row muted">
              <span class="lc-k">串行估算</span>
              <span class="lc-track"><i :style="{ width: barWidth(stampedeView.serialEstimateMs, stampedeView.maxLatency) + '%' }"></i></span>
              <span class="lc-v mono">{{ stampedeView.serialEstimateMs }}ms</span>
            </div>
          </div>
          <p v-if="stampedeView.speedup > 0" class="viz-caption">
            单飞合并把并发请求收敛到一次回源，实测比串行估算快 <b>{{ stampedeView.speedup.toFixed(1) }}×</b>。
          </p>

          <details class="raw-fold">
            <summary>原始响应（专业人员排查用）</summary>
            <pre>{{ prettyJson(results.stampede) }}</pre>
          </details>
        </div>
      </section>

      <!-- 实验 4：缓存命中率 -->
      <section class="exp-card">
        <div class="card-head">
          <h2>缓存命中率</h2>
          <span class="tag">回放真实问题</span>
        </div>
        <p class="card-desc">
          用一批高频问题各回放一次，统计 L1 / L2 命中率。环形图看整体命中，右侧数字看分层贡献。
        </p>
        <div class="controls">
          <button class="run-btn" :disabled="running.hit" @click="runHitRate">
            {{ running.hit ? '运行中…' : '运行实验' }}
          </button>
        </div>

        <StateBlock v-if="running.hit" loading loading-text="缓存实验运行中..." />
        <StateBlock v-else-if="errors.hit" :error="errors.hit" @retry="runHitRate" />
        <StateBlock v-else-if="attempted.hit && !results.hit" empty empty-text="暂无缓存实验数据" />
        <div v-else-if="results.hit" class="result">
          <div class="result-top">
            <span class="result-meta">{{ resultMeta('hit') }}</span>
            <span class="pass-badge ok">HIT {{ hitView.ratePct }}%</span>
          </div>
          <div class="hit-layout">
            <div class="hit-chart">
              <BaseChart :option="hitChart" height="170px" />
            </div>
            <div class="hit-kpis">
              <div class="hit-row"><span>问题数</span><b>{{ hitView.queries }}</b></div>
              <div class="hit-row"><span>命中</span><b class="success-text">{{ hitView.hits }}</b></div>
              <div class="hit-row"><span>L1 命中</span><b class="accent-text">{{ hitView.l1Hits }}</b></div>
              <div class="hit-row"><span>L2 命中</span><b class="accent-text">{{ hitView.l2Hits }}</b></div>
            </div>
          </div>
          <details class="raw-fold">
            <summary>原始响应（专业人员排查用）</summary>
            <pre>{{ prettyJson(results.hit) }}</pre>
          </details>
        </div>
      </section>

      <!-- 实验 5：系统韧性 -->
      <section class="exp-card">
        <div class="card-head">
          <h2>系统韧性</h2>
          <span class="tag warn">熔断 + 限流</span>
        </div>
        <p class="card-desc">
          熔断器三态与令牌桶限流的实时状态。状态灯 + 失败率仪表盘，手动打开熔断可直接观察下降级。
        </p>
        <div class="controls">
          <button class="run-btn warn" :disabled="loadingFlags.resilience" @click="circuitControl('open')">手动熔断</button>
          <button class="ghost-btn" :disabled="loadingFlags.resilience" @click="circuitControl('reset')">恢复</button>
          <button class="ghost-btn" :disabled="loadingFlags.resilience" @click="loadResilience(true)">刷新状态</button>
        </div>

        <StateBlock v-if="loadingFlags.resilience && !results.resilience" loading loading-text="正在读取熔断状态..." />
        <StateBlock v-else-if="errors.resilience" :error="errors.resilience" @retry="loadResilience(true)" />
        <StateBlock v-else-if="!results.resilience" empty empty-text="暂无熔断状态数据" />
        <div v-else class="result">
          <div class="result-top">
            <span class="result-meta">{{ resultMeta('resilience', '状态同步') }}</span>
            <span class="state-pill" :class="resilienceView.stateClass">{{ resilienceView.state }}</span>
          </div>
          <div class="resilience-layout">
            <div class="gauge" :style="resilienceGaugeStyle">
              <div class="gauge-inner">
                <span class="gauge-dot" :class="resilienceView.stateClass"></span>
                <b>{{ resilienceView.state }}</b>
                <span>窗口失败率 {{ resilienceView.failureRate }}%</span>
              </div>
            </div>
            <div class="res-kpis">
              <div class="res-kpi">
                <span>熔断拒绝</span>
                <b class="danger-text">{{ resilienceView.rejectedByCircuit }}</b>
              </div>
              <div class="res-kpi">
                <span>限流拒绝</span>
                <b class="warn-text">{{ resilienceView.rateLimitRejected }}</b>
              </div>
              <div class="res-kpi">
                <span>窗口请求</span>
                <b>{{ resilienceView.windowTotal }}</b>
              </div>
              <div class="res-kpi">
                <span>限流桶</span>
                <b>{{ resilienceView.rateLimitBuckets }}</b>
              </div>
            </div>
          </div>
          <p class="viz-caption">{{ resilienceView.hint }}</p>
          <details class="raw-fold">
            <summary>原始响应（专业人员排查用）</summary>
            <pre>{{ prettyJson(results.resilience) }}</pre>
          </details>
        </div>
      </section>

      <!-- 实验 6：LLM 降级演练 -->
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
          <button class="run-btn warn" :disabled="running.fault" @click="injectFault">
            {{ running.fault ? '注入中…' : '注入故障' }}
          </button>
          <button class="ghost-btn" :disabled="running.fault" @click="clearFault">清除</button>
        </div>

        <StateBlock v-if="(loadingFlags.faults || running.fault) && !faultCount && !results.fault" loading loading-text="正在读取故障状态..." />
        <StateBlock v-else-if="errors.fault" :error="errors.fault" @retry="loadFaults(true)" />
        <StateBlock v-else-if="!faultCount && !results.fault" empty empty-text="当前无活动故障，注入后会在这里亮灯" />
        <div v-else class="result">
          <div class="result-top">
            <span class="result-meta">{{ resultMeta('fault', '状态同步') }}</span>
            <span class="fault-badge" :class="{ on: faultCount }">{{ faultCount ? 'FAULT ON' : 'CLEAR' }}</span>
          </div>
          <div class="fault-state" :class="{ active: faultCount }">
            <span class="fault-light"></span>
            <div>
              <b>{{ faultCount ? '故障注入中' : '故障已清除' }}</b>
              <p v-if="faultCount">系统会按降级链路返回 200，而不是把异常抛给用户。</p>
              <p v-else>{{ faultHint }}</p>
            </div>
          </div>
          <div v-if="faultEntries.length" class="fault-chips">
            <span v-for="[name, remain] in faultEntries" :key="name" class="fault-chip">
              <b>{{ name }}</b><span>剩余 {{ remain }}s</span>
            </span>
          </div>
          <details v-if="results.fault" class="raw-fold">
            <summary>原始响应（专业人员排查用）</summary>
            <pre>{{ prettyJson(results.fault) }}</pre>
          </details>
        </div>
      </section>

      <!-- 实验 7：检索解释 -->
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
          <button class="run-btn" :disabled="running.explain" @click="runExplain">
            {{ running.explain ? '检索中…' : '解释这次检索' }}
          </button>
        </div>

        <StateBlock v-if="running.explain" loading loading-text="检索解释生成中..." />
        <StateBlock v-else-if="errors.explain" :error="errors.explain" @retry="runExplain" />
        <StateBlock v-else-if="attempted.explain && !results.explain" empty empty-text="暂无检索解释数据" />
        <div v-else-if="results.explain" class="explain-result">
          <div class="result-top">
            <span class="result-meta">{{ resultMeta('explain') }}</span>
            <span class="explain-meta">
              RRF k = <b class="mono">{{ explainView.rrfK }}</b>
              <i>·</i> 向量阈值 = <b class="mono">{{ explainView.similarityThreshold }}</b>
            </span>
          </div>

          <div class="timing-bar" v-if="explainView.totalMs > 0">
            <span
              v-for="seg in explainView.timingSegments"
              :key="seg.name"
              class="timing-seg"
              :style="{ width: seg.width + '%', background: seg.color }"
              :title="seg.name + ' ' + seg.ms + 'ms'"
            ></span>
          </div>
          <div class="timing-legend">
            <span v-for="seg in explainView.timingSegments" :key="'lg' + seg.name">
              <i :style="{ background: seg.color }"></i>{{ seg.name }} <b class="mono">{{ seg.ms }}ms</b>
            </span>
            <span class="timing-total">合计 <b class="mono">{{ explainView.totalMs }}ms</b></span>
          </div>

          <div class="explain-cols">
            <div v-for="col in explainView.columns" :key="col.key" class="ex-col">
              <div class="ex-title">{{ col.title }}（{{ col.items.length }}）</div>
              <div v-for="(it, i) in col.items" :key="col.key + i" class="ex-item">
                <span class="ex-rank">{{ i + 1 }}</span>
                <span class="ex-main">
                  <span class="ex-text" :title="it.preview">{{ it.preview || '无预览文本' }}</span>
                  <span class="score-track">
                    <i :style="{ width: scoreWidth(it.score, col.items) + '%' }"></i>
                  </span>
                </span>
                <span class="ex-score mono">{{ it.score.toFixed(col.precision) }}</span>
              </div>
              <div v-if="!col.items.length" class="ex-empty">无命中</div>
            </div>
          </div>
          <p class="ex-note">
            说明：BM25 分与余弦相似度不是一个量纲，所以 RRF 只用排名不用分数；阈值只作用于向量路。
          </p>
          <details class="raw-fold">
            <summary>原始响应（专业人员排查用）</summary>
            <pre>{{ prettyJson(results.explain) }}</pre>
          </details>
        </div>
      </section>

      <!-- 知识图谱可视化 -->
      <KgGraphPanel />

      <!-- 能力自证 -->
      <CapabilityProbe />

      <!-- 系统自证条 -->
      <section class="exp-card wide">
        <div class="card-head">
          <h2>系统自证</h2>
          <span class="tag">真实运行数据</span>
        </div>
        <StateBlock v-if="loadingFlags.info && !info" loading loading-text="正在读取系统自证信息..." />
        <StateBlock v-else-if="errors.info" :error="errors.info" @retry="loadInfo(true)" />
        <StateBlock v-else-if="!info" empty empty-text="暂无系统自证数据" />
        <div v-else class="proof-grid">
          <div class="pg-item"><span class="pg-k">构建</span><span class="pg-v mono">{{ info.gitCommit }}</span></div>
          <div class="pg-item"><span class="pg-k">运行</span><span class="pg-v">{{ info.uptimeHuman }}</span></div>
          <div class="pg-item"><span class="pg-k">累计请求</span><span class="pg-v mono">{{ info.counters?.chatRequests }}</span></div>
          <div class="pg-item"><span class="pg-k">LLM 调用</span><span class="pg-v mono">{{ info.counters?.llmCalls }}</span></div>
          <div class="pg-item"><span class="pg-k">L1/L2 命中</span><span class="pg-v mono">{{ info.counters?.cacheL1Hits }} / {{ info.counters?.cacheL2Hits }}</span></div>
          <div class="pg-item"><span class="pg-k">锁获取</span><span class="pg-v mono">{{ info.counters?.lockAcquired }}</span></div>
          <div class="pg-item"><span class="pg-k">链路追踪</span><span class="pg-v mono">{{ info.counters?.traces }}</span></div>
          <div class="pg-item"><span class="pg-k">会话 / 消息</span><span class="pg-v mono">{{ info.tables?.conversations }} / {{ info.tables?.messages }}</span></div>
        </div>
        <p class="card-desc small">这些数字每 5 秒刷新一次，来自真实运行的实例，不是截图。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { get, post } from '../api/http.js'
import BaseChart from './charts/BaseChart.vue'
import KgGraphPanel from './KgGraphPanel.vue'
import CapabilityProbe from './CapabilityProbe.vue'
import StateBlock from './StateBlock.vue'

const idemAttempts = ref(20)
const benchIterations = ref(3)
const concurrency = ref(20)
const faultSec = ref(60)
const explainQuery = ref('耳机左耳没声音了还能保修吗')
const info = ref(null)
const faults = ref({})
const faultHint = ref('注入故障后，聊天请求会走降级链路；状态每 5 秒自动刷新。')

const results = reactive({
  idem: null,
  bench: null,
  stampede: null,
  hit: null,
  resilience: null,
  fault: null,
  explain: null
})
const running = reactive({
  idem: false,
  bench: false,
  stampede: false,
  hit: false,
  resilience: false,
  fault: false,
  explain: false
})
const attempted = reactive({
  idem: false,
  bench: false,
  stampede: false,
  hit: false,
  fault: false,
  explain: false
})
const errors = reactive({
  idem: '',
  bench: '',
  stampede: '',
  hit: '',
  resilience: '',
  fault: '',
  explain: '',
  info: ''
})
const loadingFlags = reactive({ resilience: false, faults: false, info: false })
const meta = reactive({
  idem: null,
  bench: null,
  stampede: null,
  hit: null,
  resilience: null,
  fault: null,
  explain: null
})

let timer = null

function errorText(e, fallback = '请求失败，请稍后重试') {
  if (e?.status === 403) return '无权限（需要管理员）'
  if (e?.status === 401) return '登录已过期，请重新登录'
  return e?.message || fallback
}

function nowLabel() {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function beginRun(key) {
  running[key] = true
  attempted[key] = true
  errors[key] = ''
  results[key] = null
  meta[key] = null
  return performance.now()
}

function endRun(key, start) {
  running[key] = false
  meta[key] = { at: nowLabel(), elapsed: Math.round(performance.now() - start) }
}

function syncMeta(key, start) {
  meta[key] = { at: nowLabel(), elapsed: Math.round(performance.now() - start) }
}

function resultMeta(key, verb = '运行') {
  const m = meta[key]
  if (!m) return '尚未记录运行信息'
  return `${m.at} ${verb} · 前端耗时 ${m.elapsed}ms`
}

function prettyJson(value) {
  try {
    return JSON.stringify(value, null, 2)
  } catch (e) {
    return String(value)
  }
}

function readPath(obj, path) {
  return String(path).split('.').reduce((current, key) => {
    if (current === null || current === undefined) return undefined
    return current[key]
  }, obj)
}

function pick(obj, ...paths) {
  for (const path of paths) {
    const value = readPath(obj, path)
    if (value !== undefined && value !== null && value !== '') return value
  }
  return undefined
}

function toNum(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function barWidth(value, max) {
  if (!max || max <= 0) return 0
  return Math.max(Math.min((toNum(value) / max) * 100, 100), 0)
}

function scoreWidth(score, items) {
  const max = Math.max(...items.map(item => toNum(item.score)), 0) || 1
  return Math.max(Math.min((toNum(score) / max) * 100, 100), 3)
}

/* ===== 幂等并发 ===== */
const idemView = computed(() => {
  const r = results.idem || {}
  const attempts = Math.max(toNum(pick(r, 'attempts', 'totalAttempts', 'requests', 'concurrency'), idemAttempts.value), 0)
  let success = toNum(pick(r, 'successCount', 'successful', 'inserted', 'created', 'insertedRows'), NaN)
  if (!Number.isFinite(success)) success = typeof r.success === 'number' ? r.success : 0
  let rejected = toNum(pick(r, 'rejected', 'rejectedCount', 'duplicateRejected', 'rejectedByUniqueIndex', 'uniqueRejected', 'conflicts'), NaN)
  if (!Number.isFinite(rejected)) rejected = Math.max(attempts - success, 0)
  const rows = toNum(pick(r, 'tableRows', 'rowCount', 'rows', 'storedRows'), success || 1)

  const rawPass = pick(r, 'pass', 'passed')
  const explicitPass = rawPass === true || String(rawPass).toLowerCase() === 'pass'
    ? true
    : (rawPass === false || String(rawPass).toLowerCase() === 'fail' ? false : null)
  const pass = explicitPass === null ? (success === 1 && rows === 1) : explicitPass

  return { attempts, success, rejected, rows, pass }
})

const idemChart = computed(() => {
  const v = idemView.value
  const max = Math.max(v.attempts, v.success + v.rejected, 1)
  return {
    animationDuration: 500,
    grid: { left: 8, right: 8, top: 26, bottom: 6, containLabel: false },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      top: 0,
      right: 4,
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: 'var(--text-tertiary)', fontSize: 10 }
    },
    xAxis: { type: 'value', max, show: false },
    yAxis: { type: 'category', data: ['尝试结果'], show: false },
    series: [
      {
        name: '成功写入',
        type: 'bar',
        stack: 'result',
        barWidth: 24,
        data: [v.success],
        itemStyle: { color: 'token:success', borderRadius: [4, 0, 0, 4] },
        label: {
          show: v.success > 0,
          position: 'inside',
          color: 'var(--text-inverse)',
          fontSize: 10,
          formatter: '{c}'
        }
      },
      {
        name: '唯一索引拒绝',
        type: 'bar',
        stack: 'result',
        barWidth: 24,
        data: [v.rejected],
        itemStyle: { color: 'token:danger', borderRadius: [0, 4, 4, 0] },
        label: {
          show: v.rejected > 0,
          position: 'inside',
          color: 'var(--text-inverse)',
          fontSize: 10,
          formatter: '{c}'
        }
      }
    ]
  }
})

/* ===== 并行 / 串行基准 ===== */
const benchView = computed(() => {
  const r = results.bench || {}
  const serialP50 = toNum(pick(r,
    'serialP50', 'serialP50Ms', 'serial.p50', 'serial.p50Ms', 'serialLatencyP50', 'serialMs'
  ), 0)
  const parallelP50 = toNum(pick(r,
    'parallelP50', 'parallelP50Ms', 'parallel.p50', 'parallel.p50Ms', 'parallelLatencyP50', 'parallelMs'
  ), 0)
  const serialP95 = toNum(pick(r,
    'serialP95', 'serialP95Ms', 'serial.p95', 'serial.p95Ms', 'serialLatencyP95'
  ), serialP50)
  const parallelP95 = toNum(pick(r,
    'parallelP95', 'parallelP95Ms', 'parallel.p95', 'parallel.p95Ms', 'parallelLatencyP95'
  ), parallelP50)
  const speedup = toNum(pick(r, 'speedup', 'speedUp', 'acceleration', 'speedupRatio'), 0) ||
    (parallelP50 > 0 ? serialP50 / parallelP50 : 0)
  return {
    serialP50: Math.round(serialP50),
    parallelP50: Math.round(parallelP50),
    serialP95: Math.round(serialP95),
    parallelP95: Math.round(parallelP95),
    speedup,
    speedupText: speedup.toFixed(2),
    valid: serialP50 > 0 || parallelP50 > 0 || serialP95 > 0 || parallelP95 > 0
  }
})

const benchChart = computed(() => {
  const v = benchView.value
  return {
    animationDuration: 600,
    grid: { left: 44, right: 18, top: 34, bottom: 28 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      top: 0,
      right: 8,
      itemWidth: 9,
      itemHeight: 9,
      textStyle: { color: 'var(--text-tertiary)', fontSize: 10 }
    },
    xAxis: {
      type: 'category',
      data: ['P50', 'P95'],
      axisLine: { lineStyle: { color: 'var(--border-primary)' } },
      axisTick: { show: false },
      axisLabel: { color: 'var(--text-tertiary)', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      name: 'ms',
      nameTextStyle: { color: 'var(--text-tertiary)', fontSize: 10 },
      axisLabel: { color: 'var(--text-tertiary)', fontSize: 10 },
      splitLine: { lineStyle: { color: 'var(--rule)' } }
    },
    series: [
      {
        name: '串行',
        type: 'bar',
        barMaxWidth: 28,
        data: [v.serialP50, v.serialP95],
        itemStyle: { color: 'token:accent-love', borderRadius: [3, 3, 0, 0] },
        label: { show: true, position: 'top', color: 'var(--text-tertiary)', fontSize: 9 }
      },
      {
        name: '并行',
        type: 'bar',
        barMaxWidth: 28,
        data: [v.parallelP50, v.parallelP95],
        itemStyle: { color: 'token:accent', borderRadius: [3, 3, 0, 0] },
        label: { show: true, position: 'top', color: 'var(--accent)', fontSize: 9 }
      }
    ]
  }
})

/* ===== 缓存击穿单飞 ===== */
const stampedeView = computed(() => {
  const r = results.stampede || {}
  const requests = toNum(pick(r, 'requests', 'totalRequests'), concurrency.value)
  const realBackendCalls = toNum(pick(r, 'realBackendCalls', 'backendCalls', 'llmCallsDelta'), 0)
  const cacheHits = toNum(pick(r, 'cacheHits', 'hitCount'), 0)
  const totalMs = toNum(pick(r, 'totalMs', 'elapsedMs'), 0)
  const serialEstimateMs = toNum(pick(r, 'serialEstimateMs', 'serialMs', 'estimatedSerialMs'), 0)
  const other = Math.max(requests - realBackendCalls - cacheHits, 0)
  const speedup = totalMs > 0 ? serialEstimateMs / totalMs : 0
  const rawPass = pick(r, 'pass', 'passed')
  const explicitPass = rawPass === true || String(rawPass).toLowerCase() === 'pass'
    ? true
    : (rawPass === false || String(rawPass).toLowerCase() === 'fail' ? false : null)
  return {
    requests,
    realBackendCalls,
    cacheHits,
    other,
    totalMs,
    serialEstimateMs,
    maxLatency: Math.max(totalMs, serialEstimateMs, 1),
    speedup,
    pass: explicitPass === null ? realBackendCalls <= 1 : explicitPass
  }
})

const stampedeChart = computed(() => {
  const v = stampedeView.value
  const max = Math.max(v.requests, v.realBackendCalls + v.cacheHits + v.other, 1)
  const base = { type: 'bar', stack: 'requests', barWidth: 24, label: { show: true, position: 'inside', color: 'var(--text-inverse)', fontSize: 10, formatter: '{c}' } }
  return {
    animationDuration: 500,
    grid: { left: 8, right: 8, top: 26, bottom: 6, containLabel: false },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, right: 4, itemWidth: 8, itemHeight: 8, textStyle: { color: 'var(--text-tertiary)', fontSize: 10 } },
    xAxis: { type: 'value', max, show: false },
    yAxis: { type: 'category', data: ['请求去向'], show: false },
    series: [
      { ...base, name: '真实回源', data: [v.realBackendCalls], itemStyle: { color: 'token:accent-love', borderRadius: [4, 0, 0, 4] } },
      { ...base, name: '缓存命中', data: [v.cacheHits], itemStyle: { color: 'token:success' } },
      { ...base, name: '其他', data: [v.other], itemStyle: { color: 'var(--bg-step)', borderRadius: [0, 4, 4, 0] }, label: { show: false } }
    ]
  }
})

/* ===== 缓存命中率 ===== */
const hitView = computed(() => {
  const r = results.hit || {}
  const queries = toNum(pick(r, 'queries', 'queryCount', 'total'), 0)
  const hits = toNum(pick(r, 'hits', 'hitCount'), 0)
  let rate = toNum(pick(r, 'hitRate', 'hitRatePercent'), 0)
  if (rate > 1) rate = rate / 100
  const ratePct = Math.round(rate * 1000) / 10
  return {
    queries,
    hits,
    miss: Math.max(queries - hits, 0),
    ratePct,
    l1Hits: toNum(pick(r, 'l1Hits', 'l1', 'cacheL1Hits'), 0),
    l2Hits: toNum(pick(r, 'l2Hits', 'l2', 'cacheL2Hits'), 0)
  }
})

const hitChart = computed(() => {
  const v = hitView.value
  return {
    animationDuration: 600,
    title: {
      text: v.ratePct + '%',
      subtext: '命中率',
      left: 'center',
      top: '36%',
      textStyle: { color: 'var(--text-primary)', fontFamily: 'var(--font-serif)', fontSize: 24, fontWeight: 500 },
      subtextStyle: { color: 'var(--text-tertiary)', fontSize: 10 }
    },
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    series: [{
      type: 'pie',
      radius: ['66%', '84%'],
      center: ['50%', '50%'],
      label: { show: false },
      labelLine: { show: false },
      itemStyle: { borderWidth: 0 },
      data: [
        { value: v.hits, name: '命中', itemStyle: { color: 'token:success' } },
        { value: v.miss, name: '未命中', itemStyle: { color: 'var(--bg-step)' } }
      ]
    }]
  }
})

/* ===== 系统韧性 ===== */
const resilienceView = computed(() => {
  const r = results.resilience || {}
  const state = String(pick(r, 'circuitState', 'state') || 'UNKNOWN').toUpperCase()
  const failureRate = Math.max(0, Math.min(toNum(pick(r, 'failureRate', 'failureRatePercent'), 0), 100))
  return {
    state,
    stateClass: state === 'CLOSED' ? 'closed' : (state === 'OPEN' ? 'open' : (state === 'HALF_OPEN' ? 'half' : 'unknown')),
    failureRate: Math.round(failureRate * 10) / 10,
    rejectedByCircuit: toNum(pick(r, 'rejectedByCircuit', 'circuitRejected', 'rejectedCount'), 0),
    rateLimitRejected: toNum(pick(r, 'rateLimitRejected', 'rateLimited'), 0),
    windowTotal: toNum(pick(r, 'windowTotal', 'total'), 0),
    rateLimitBuckets: toNum(pick(r, 'rateLimitBuckets', 'bucketCount'), 0),
    hint: state === 'OPEN'
      ? '熔断器已打开：新请求会被快速拒绝，等待恢复后进入半开探测。'
      : (state === 'HALF_OPEN'
        ? '熔断器半开：少量探测请求正在试探下游是否恢复。'
        : '熔断器关闭：请求正常放行，窗口失败率超过阈值后会自动打开。')
  }
})

const resilienceGaugeStyle = computed(() => {
  const rate = resilienceView.value.failureRate
  const color = resilienceView.value.stateClass === 'open'
    ? 'var(--danger)'
    : (resilienceView.value.stateClass === 'half' ? 'var(--warning)' : 'var(--success)')
  return { background: `conic-gradient(${color} 0 ${rate}%, var(--bg-step) ${rate}% 100%)` }
})

/* ===== 故障注入 ===== */
const faultEntries = computed(() => Object.entries(faults.value || {}))
const faultCount = computed(() => faultEntries.value.length)

/* ===== 检索解释 ===== */
const explainView = computed(() => {
  const r = results.explain || {}
  const timings = r.timings || {}
  const bm25Ms = toNum(pick(timings, 'bm25Ms', 'bm25'), 0)
  const vectorMs = toNum(pick(timings, 'vectorMs', 'vector'), 0)
  const fuseMs = toNum(pick(timings, 'fuseMs', 'fuse', 'rrfMs'), 0)
  const totalMs = toNum(pick(timings, 'totalMs', 'total'), bm25Ms + vectorMs + fuseMs)
  const timingSegments = [
    { name: 'BM25', ms: bm25Ms, color: 'var(--accent)' },
    { name: '向量', ms: vectorMs, color: 'var(--accent-love)' },
    { name: '融合', ms: fuseMs, color: 'var(--success)' }
  ].filter(seg => seg.ms > 0).map(seg => ({
    ...seg,
    width: totalMs > 0 ? Math.max((seg.ms / totalMs) * 100, 2) : 0
  }))

  const normalizeItems = list => (Array.isArray(list) ? list : []).map(item => ({
    ...item,
    preview: item.preview || item.content || item.text || '',
    score: toNum(item.score, 0)
  }))

  return {
    rrfK: pick(r, 'rrfK', 'k') ?? '—',
    similarityThreshold: pick(r, 'similarityThreshold', 'threshold') ?? '—',
    totalMs,
    timingSegments,
    columns: [
      { key: 'bm25', title: 'BM25 候选', precision: 2, items: normalizeItems(r.bm25) },
      { key: 'vector', title: '向量候选', precision: 3, items: normalizeItems(r.vector) },
      { key: 'fused', title: 'RRF 融合后', precision: 4, items: normalizeItems(r.fused) }
    ]
  }
})

/* ===== 请求动作 ===== */
async function runIdempotency() {
  const start = beginRun('idem')
  try {
    results.idem = await post('/admin/experiments/idempotency', {
      attempts: idemAttempts.value,
      concurrency: idemAttempts.value
    }, { timeout: 60000 })
  } catch (e) {
    errors.idem = errorText(e, '幂等并发实验失败')
  } finally {
    endRun('idem', start)
  }
}

async function runBench() {
  const start = beginRun('bench')
  try {
    results.bench = await post('/admin/experiments/retrieval-bench', {
      topK: 5,
      iterations: benchIterations.value
    }, { timeout: 60000 })
  } catch (e) {
    errors.bench = errorText(e, '并行 vs 串行基准失败')
  } finally {
    endRun('bench', start)
  }
}

async function runStampede() {
  const start = beginRun('stampede')
  try {
    results.stampede = await post('/admin/experiments/stampede', { concurrency: concurrency.value })
  } catch (e) {
    errors.stampede = errorText(e, '缓存击穿实验失败')
  } finally {
    endRun('stampede', start)
    loadInfo(true)
  }
}

async function runHitRate() {
  const start = beginRun('hit')
  try {
    results.hit = await post('/admin/experiments/cache-hit-rate', {
      queries: ['退货政策是什么？', '退款多久到账？', '保修期多久？', '怎么取消订单？']
    })
  } catch (e) {
    errors.hit = errorText(e, '缓存命中率实验失败')
  } finally {
    endRun('hit', start)
  }
}

async function loadResilience(silent = false) {
  if (!silent) loadingFlags.resilience = true
  errors.resilience = ''
  const start = performance.now()
  try {
    results.resilience = await get('/admin/experiments/resilience')
    if (!silent || !meta.resilience) syncMeta('resilience', start)
  } catch (e) {
    errors.resilience = errorText(e, '熔断状态加载失败')
  } finally {
    loadingFlags.resilience = false
  }
}

async function circuitControl(action) {
  loadingFlags.resilience = true
  errors.resilience = ''
  const start = performance.now()
  try {
    await post('/admin/experiments/resilience/control', { action })
    results.resilience = await get('/admin/experiments/resilience')
    syncMeta('resilience', start)
  } catch (e) {
    errors.resilience = errorText(e, '熔断控制失败')
  } finally {
    loadingFlags.resilience = false
  }
}

async function loadFaults(silent = false) {
  if (!silent) loadingFlags.faults = true
  errors.fault = ''
  const start = performance.now()
  try {
    const data = await get('/admin/experiments/fault/status')
    faults.value = data?.activeFaults || {}
    if (!silent || !meta.fault) syncMeta('fault', start)
  } catch (e) {
    errors.fault = errorText(e, '故障状态加载失败')
  } finally {
    loadingFlags.faults = false
  }
}

async function injectFault() {
  const start = beginRun('fault')
  try {
    results.fault = await post('/admin/experiments/fault', {
      target: 'llm',
      durationSec: faultSec.value
    })
    faultHint.value = results.fault?.hint || '故障已注入，下一次聊天请求会触发降级链路。'
    await loadFaults(true)
  } catch (e) {
    errors.fault = errorText(e, '故障注入失败')
  } finally {
    endRun('fault', start)
  }
}

async function clearFault() {
  running.fault = true
  attempted.fault = true
  errors.fault = ''
  const start = performance.now()
  try {
    await post('/admin/experiments/fault/clear', {})
    results.fault = null
    await loadFaults(true)
    syncMeta('fault', start)
  } catch (e) {
    errors.fault = errorText(e, '清除故障失败')
  } finally {
    running.fault = false
  }
}

async function runExplain() {
  if (!explainQuery.value.trim()) return
  const start = beginRun('explain')
  try {
    results.explain = await post('/admin/retrieval/explain', { query: explainQuery.value.trim() }, { timeout: 60000 })
  } catch (e) {
    errors.explain = errorText(e, '检索解释失败')
  } finally {
    endRun('explain', start)
  }
}

async function loadInfo(silent = false) {
  if (!silent) loadingFlags.info = true
  errors.info = ''
  try {
    info.value = await get('/system/info')
  } catch (e) {
    errors.info = errorText(e, '系统自证信息加载失败')
  } finally {
    loadingFlags.info = false
  }
}

onMounted(() => {
  loadInfo()
  loadResilience()
  loadFaults()
  timer = setInterval(() => {
    if (document.visibilityState === 'visible') {
      loadInfo(true)
      loadFaults(true)
      loadResilience(true)
    }
  }, 5000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.exp-page { height: 100dvh; overflow-y: auto; padding: 24px 32px 60px; background: var(--bg-app); }
.exp-head { display: flex; align-items: center; gap: 14px; margin-bottom: 22px; padding-bottom: 14px; border-bottom: 1px solid var(--rule); }
.back-btn { padding: 6px 12px; border: 1px solid var(--border-primary); border-radius: var(--radius-sm); color: var(--text-secondary); font-size: 0.8125rem; }
.back-btn:hover { color: var(--accent); border-color: var(--accent-border); }
h1 { font-family: var(--font-serif); font-size: 1.375rem; }
.exp-sub { margin-left: auto; font-size: 0.75rem; color: var(--text-tertiary); }

.exp-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; align-items: start; }
.exp-card { border: 1px solid var(--rule); border-radius: var(--radius); padding: 16px 18px; background: var(--bg-sidebar); }
.exp-card.wide { grid-column: 1 / -1; }
.card-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.card-head h2 { font-family: var(--font-serif); font-size: 1rem; font-weight: 500; }
.tag { font-size: 0.625rem; padding: 1px 7px; border: 1px solid var(--border-secondary); border-radius: var(--radius-full); color: var(--text-tertiary); }
.tag.warn { border-color: var(--accent-love-border); color: var(--accent-love); }
.tag.accent { border-color: var(--accent-border); color: var(--accent); }
.card-desc { font-size: 0.8125rem; line-height: 1.7; color: var(--text-secondary); margin-bottom: 12px; }
.card-desc.small { font-size: 0.6875rem; color: var(--text-tertiary); margin: 10px 0 0; }

.controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.controls label { display: inline-flex; align-items: center; gap: 6px; font-size: 0.75rem; color: var(--text-tertiary); }
.controls input { width: 64px; padding: 4px 8px; border: 1px solid var(--border-input); border-radius: var(--radius-xs); background: var(--bg-input); color: var(--text-primary); font-family: var(--font-mono); }
.run-btn { padding: 7px 16px; border-radius: var(--radius-sm); background: var(--accent); color: var(--text-inverse); font-size: 0.8125rem; }
.run-btn:disabled { opacity: 0.6; }
.run-btn.warn { background: var(--accent-love); }
.ghost-btn { padding: 7px 12px; border: 1px solid var(--border-secondary); border-radius: var(--radius-sm); color: var(--text-secondary); font-size: 0.75rem; }
.ghost-btn:disabled { opacity: 0.55; }
.q-input { flex: 1; min-width: 240px; padding: 7px 10px; border: 1px solid var(--border-input); border-radius: var(--radius-sm); background: var(--bg-input); color: var(--text-primary); font-size: 0.8125rem; }

.result { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--rule); }
.result-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.result-meta { font-size: 0.625rem; color: var(--text-tertiary); font-family: var(--font-mono); }
.pass-badge { padding: 3px 10px; border-radius: var(--radius-full); font-size: 0.625rem; font-weight: 600; letter-spacing: 0.08em; }
.pass-badge.ok { color: var(--success); background: var(--success-bg); }
.pass-badge.fail { color: var(--danger); background: var(--danger-bg); }
.viz-caption { margin: 6px 0 0; font-size: 0.6875rem; line-height: 1.6; color: var(--text-tertiary); }
.viz-caption b { color: var(--accent); }
.inline-empty { padding: 24px 12px; text-align: center; font-size: 0.75rem; color: var(--text-tertiary); border: 1px dashed var(--rule); border-radius: var(--radius-sm); }

.kpi-grid { display: grid; gap: 8px; margin-bottom: 8px; }
.kpi-grid.four { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.kpi-grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.kpi-grid.compact .kpi { padding: 8px 9px; }
.kpi { padding: 10px 11px; border: 1px solid var(--rule); border-radius: var(--radius-sm); background: var(--bg-step); display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.kpi-k { font-size: 0.625rem; color: var(--text-tertiary); }
.kpi-v { font-family: var(--font-serif); font-size: 1.75rem; line-height: 1.15; font-weight: 500; color: var(--text-primary); }
.kpi-v.small { font-size: 1.25rem; }
.kpi-v em { margin-left: 2px; font-family: var(--font-mono); font-size: 0.625rem; font-style: normal; color: var(--text-tertiary); font-weight: 400; }
.kpi-v.accent { color: var(--accent); }
.kpi.success .kpi-v { color: var(--success); }
.kpi.danger .kpi-v { color: var(--danger); }
.kpi.accent .kpi-v { color: var(--accent); }
.kpi-sub { font-size: 0.5625rem; color: var(--text-tertiary); }

.raw-fold { margin-top: 12px; border: 1px solid var(--rule); border-radius: var(--radius-sm); overflow: hidden; }
.raw-fold summary { padding: 7px 10px; cursor: pointer; font-size: 0.625rem; color: var(--text-tertiary); background: var(--bg-step); user-select: none; }
.raw-fold summary:hover { color: var(--accent); }
.raw-fold pre { margin: 0; padding: 10px; max-height: 260px; overflow: auto; font-family: var(--font-mono); font-size: 0.625rem; line-height: 1.55; color: var(--text-secondary); background: var(--bg-app); }

.bench-speedup { font-size: 0.75rem; color: var(--text-tertiary); }
.bench-speedup b { margin-left: 4px; font-family: var(--font-serif); font-size: 1.5rem; color: var(--accent); font-weight: 500; }
.chart-block { margin: 0 -4px; }

.latency-compare { margin-top: 6px; display: flex; flex-direction: column; gap: 5px; }
.lc-row { display: grid; grid-template-columns: 72px 1fr 58px; align-items: center; gap: 8px; }
.lc-row.muted { opacity: 0.72; }
.lc-k { font-size: 0.625rem; color: var(--text-tertiary); }
.lc-track { display: block; height: 5px; border-radius: var(--radius-full); background: var(--bg-step); overflow: hidden; }
.lc-track i { display: block; height: 100%; border-radius: var(--radius-full); background: var(--accent); }
.lc-row.muted .lc-track i { background: var(--text-tertiary); }
.lc-v { text-align: right; font-size: 0.625rem; color: var(--text-secondary); }

.hit-layout { display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 16px; align-items: center; }
.hit-chart { min-width: 0; }
.hit-kpis { display: flex; flex-direction: column; }
.hit-row { display: flex; align-items: baseline; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid var(--rule); font-size: 0.75rem; color: var(--text-tertiary); }
.hit-row:last-child { border-bottom: 0; }
.hit-row b { font-family: var(--font-mono); font-size: 0.9375rem; color: var(--text-primary); }
.success-text { color: var(--success) !important; }
.accent-text { color: var(--accent) !important; }
.danger-text { color: var(--danger) !important; }
.warn-text { color: var(--warning) !important; }

.state-pill { padding: 2px 9px; border-radius: var(--radius-full); font-family: var(--font-mono); font-size: 0.625rem; }
.state-pill.closed { color: var(--success); background: var(--success-bg); }
.state-pill.open { color: var(--danger); background: var(--danger-bg); }
.state-pill.half { color: var(--warning); background: var(--warning-bg); }
.state-pill.unknown { color: var(--text-tertiary); background: var(--bg-step); }
.resilience-layout { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: 18px; align-items: center; }
.gauge { width: 150px; height: 150px; border-radius: 50%; padding: 12px; box-shadow: inset 0 0 0 1px var(--rule); }
.gauge-inner { width: 100%; height: 100%; border-radius: 50%; background: var(--bg-sidebar); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; border: 1px solid var(--rule); }
.gauge-inner b { font-family: var(--font-mono); font-size: 0.875rem; color: var(--text-primary); }
.gauge-inner span:last-child { font-size: 0.5625rem; color: var(--text-tertiary); }
.gauge-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--text-tertiary); }
.gauge-dot.closed { background: var(--success); box-shadow: 0 0 0 4px var(--success-bg); }
.gauge-dot.open { background: var(--danger); box-shadow: 0 0 0 4px var(--danger-bg); animation: state-pulse 1.2s ease-in-out infinite; }
.gauge-dot.half { background: var(--warning); box-shadow: 0 0 0 4px var(--warning-bg); }
.res-kpis { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.res-kpi { padding: 9px 10px; border: 1px solid var(--rule); border-radius: var(--radius-sm); background: var(--bg-step); display: flex; flex-direction: column; gap: 3px; }
.res-kpi span { font-size: 0.5625rem; color: var(--text-tertiary); }
.res-kpi b { font-family: var(--font-serif); font-size: 1.25rem; font-weight: 500; color: var(--text-primary); }

.fault-badge { padding: 2px 9px; border-radius: var(--radius-full); font-size: 0.625rem; color: var(--text-tertiary); background: var(--bg-step); }
.fault-badge.on { color: var(--danger); background: var(--danger-bg); }
.fault-state { display: flex; align-items: center; gap: 14px; padding: 14px; border: 1px solid var(--rule); border-radius: var(--radius-sm); background: var(--bg-step); }
.fault-state.active { border-color: rgba(180, 84, 74, 0.3); background: var(--danger-bg); }
.fault-light { width: 16px; height: 16px; flex-shrink: 0; border-radius: 50%; background: var(--success); box-shadow: 0 0 0 5px var(--success-bg); }
.fault-state.active .fault-light { background: var(--danger); box-shadow: 0 0 0 5px var(--danger-bg); animation: state-pulse 1.2s ease-in-out infinite; }
.fault-state b { display: block; font-size: 0.8125rem; color: var(--text-primary); }
.fault-state p { margin: 3px 0 0; font-size: 0.6875rem; line-height: 1.55; color: var(--text-tertiary); }
.fault-chips { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 10px; }
.fault-chip { display: inline-flex; align-items: center; gap: 7px; padding: 4px 9px; border: 1px solid var(--accent-love-border); border-radius: var(--radius-full); background: var(--accent-love-bg); font-size: 0.625rem; }
.fault-chip b { color: var(--accent-love); font-family: var(--font-mono); }
.fault-chip span { color: var(--text-secondary); }

.explain-meta { font-size: 0.625rem; color: var(--text-tertiary); }
.explain-meta b { color: var(--text-secondary); font-weight: 500; }
.explain-meta i { margin: 0 4px; font-style: normal; }
.timing-bar { display: flex; width: 100%; height: 9px; margin: 4px 0 8px; border-radius: var(--radius-full); overflow: hidden; background: var(--bg-step); }
.timing-seg { display: block; height: 100%; min-width: 2px; opacity: 0.9; }
.timing-seg + .timing-seg { box-shadow: -1px 0 0 var(--bg-sidebar); }
.timing-legend { display: flex; flex-wrap: wrap; gap: 16px; font-size: 0.625rem; color: var(--text-tertiary); margin-bottom: 14px; }
.timing-legend span { display: inline-flex; align-items: center; gap: 5px; }
.timing-legend i { width: 6px; height: 6px; border-radius: 50%; }
.timing-legend b { color: var(--text-secondary); font-weight: 500; }
.timing-total { margin-left: auto; }

.explain-cols { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.ex-col { border: 1px solid var(--rule); border-radius: var(--radius-sm); padding: 10px; }
.ex-title { font-size: 0.625rem; letter-spacing: 0.1em; text-transform: uppercase; color: var(--text-tertiary); margin-bottom: 8px; }
.ex-item { display: flex; align-items: center; gap: 8px; padding: 5px 0; border-bottom: 1px solid var(--rule); font-size: 0.75rem; }
.ex-item:last-child { border-bottom: 0; }
.ex-rank { width: 16px; color: var(--text-tertiary); font-family: var(--font-mono); font-size: 0.625rem; flex-shrink: 0; }
.ex-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.ex-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-secondary); }
.score-track { display: block; width: 100%; height: 3px; border-radius: var(--radius-full); background: var(--bg-step); overflow: hidden; }
.score-track i { display: block; height: 100%; border-radius: var(--radius-full); background: var(--accent); }
.ex-col:nth-child(2) .score-track i { background: var(--accent-love); }
.ex-col:nth-child(3) .score-track i { background: var(--success); }
.ex-score { flex-shrink: 0; color: var(--accent); font-size: 0.6875rem; }
.ex-empty { font-size: 0.75rem; color: var(--text-tertiary); padding: 4px 0; }
.ex-note { margin-top: 10px; font-size: 0.6875rem; color: var(--text-tertiary); }

.proof-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px 18px; }
.pg-item { display: flex; flex-direction: column; gap: 2px; }
.pg-k { font-size: 0.625rem; letter-spacing: 0.1em; text-transform: uppercase; color: var(--text-tertiary); }
.pg-v { font-size: 0.9375rem; color: var(--text-primary); }
.mono { font-family: var(--font-mono); }

@keyframes state-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

@media (max-width: 900px) {
  .exp-grid { grid-template-columns: 1fr; }
  .explain-cols { grid-template-columns: 1fr; }
  .proof-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .kpi-grid.four { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .resilience-layout, .hit-layout { grid-template-columns: 1fr; justify-items: center; }
  .hit-kpis, .res-kpis { width: 100%; }
}
</style>
