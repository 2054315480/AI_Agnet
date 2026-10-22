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
      <!-- 加载骨架：接口未返回时不再整页空白 -->
      <div v-if="capsLoading" class="caps-skeleton" aria-label="能力地图加载中">
        <div class="caps-skeleton-summary"></div>
        <div class="caps-skeleton-grid">
          <div v-for="n in 6" :key="n" class="caps-skeleton-card">
            <span class="sk-line sk-line-sm"></span>
            <span class="sk-line sk-line-lg"></span>
            <span class="sk-line"></span>
            <span class="sk-line sk-line-md"></span>
          </div>
        </div>
      </div>

      <StateBlock v-else-if="capsError" :error="capsError" @retry="loadCaps">
        <template #actions>
          <div class="state-actions">
            <button class="state-action-btn" type="button" @click="loadCaps">重试</button>
            <button class="state-action-btn" type="button" @click="relogin">重新登录</button>
          </div>
        </template>
      </StateBlock>

      <StateBlock
        v-else-if="!caps || !capItems.length"
        empty
        empty-text="暂无能力矩阵数据"
      >
        <template #empty>
          <button class="state-action-btn" type="button" @click="relogin">重新登录</button>
        </template>
      </StateBlock>

      <template v-else>
        <!-- 顶部能力概览：大数字 + ECharts 环形进度 -->
        <section class="caps-overview">
          <div class="caps-overview-copy">
            <span class="overview-kicker">能力概览</span>
            <div class="overview-number">
              <b>{{ implementedCount }}</b><span>/ {{ totalCount }}</span>
            </div>
            <p class="overview-note">
              已实现能力占比 <strong>{{ achievementRate }}%</strong>
              <span class="muted">· 数据实时来自后端</span>
            </p>
            <div class="overview-legend">
              <span><i class="legend-dot done"></i>已实现 {{ implementedCount }}</span>
              <span><i class="legend-dot partial"></i>部分实现 {{ partialCount }}</span>
              <span><i class="legend-dot planned"></i>计划中 {{ plannedCount }}</span>
            </div>
          </div>
          <div class="caps-ring">
            <BaseChart :option="ringOption" height="154px" />
          </div>
        </section>

        <!-- 主体：按 category 分组的能力卡片网格 -->
        <div class="cap-groups">
          <section v-for="group in capGroups" :key="group.category" class="cap-group">
            <header class="cap-group-head">
              <div>
                <h2>{{ group.category }}</h2>
                <span class="cap-group-sub">{{ group.implemented }} / {{ group.items.length }} 项已实现</span>
              </div>
              <span class="cap-group-line"></span>
            </header>
            <div class="cap-grid">
              <button
                v-for="c in group.items"
                :key="c.name"
                type="button"
                class="cap-card"
                :class="statusClass(c.status)"
                @click="openCap(c)"
              >
                <div class="cap-top">
                  <span class="cap-status" :class="statusClass(c.status)">{{ statusLabel(c.status) }}</span>
                  <span class="cap-open-hint">详情 ↗</span>
                </div>
                <h3 class="cap-name">{{ c.name }}</h3>
                <p class="cap-evidence">{{ c.evidence || '暂无证据说明' }}</p>
                <div v-if="metricEntries(c.metrics).length" class="cap-chips">
                  <span
                    v-for="[k, v] in metricEntries(c.metrics).slice(0, 3)"
                    :key="k"
                    class="cap-chip"
                  >
                    <span class="chip-k">{{ metricLabel(k) }}</span>
                    <b class="chip-v mono">{{ metricValue(v) }}</b>
                  </span>
                  <span v-if="metricEntries(c.metrics).length > 3" class="cap-chip more">
                    +{{ metricEntries(c.metrics).length - 3 }}
                  </span>
                </div>
              </button>
            </div>
          </section>
        </div>
      </template>
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

      <StateBlock v-if="demoRunning" loading loading-text="正在运行完整演示..." />
      <StateBlock v-else-if="demoError" :error="demoError" @retry="runDemo" />
      <StateBlock
        v-else-if="!demo"
        empty
        empty-text="尚未运行演示，点击右上角按钮开始。"
      />

      <div v-else class="demo-result">
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
        <label>样本数<input v-model.number="evalSize" type="number" min="10" max="100" /></label>
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

      <StateBlock v-if="evalRunning" loading loading-text="正在运行评测..." />
      <StateBlock v-else-if="evalError" :error="evalError" @retry="runEval" />

      <template v-else>
        <div v-if="evalResult" class="eval-cards">
          <div class="ecard"><span class="ec-k">Recall@5</span><span class="ec-v mono">{{ evalResult.recallAtK.toFixed(3) }}</span></div>
          <div class="ecard"><span class="ec-k">MRR</span><span class="ec-v mono">{{ evalResult.mrr.toFixed(3) }}</span></div>
          <div class="ecard"><span class="ec-k">nDCG@5</span><span class="ec-v mono">{{ evalResult.ndcgAtK.toFixed(3) }}</span></div>
          <div class="ecard"><span class="ec-k">样本 / 耗时</span><span class="ec-v mono">{{ evalResult.sampleCount }} / {{ evalResult.elapsedMs }}ms</span></div>
        </div>
        <StateBlock
          v-else
          empty
          empty-text="尚未运行评测，运行后会在此展示指标与明细。"
        />

        <StateBlock v-if="historyLoading" loading loading-text="正在加载评测历史..." />
        <StateBlock v-else-if="historyError" :error="historyError" @retry="loadHistory" />
        <StateBlock v-else-if="!evalHistory.length" empty empty-text="暂无评测历史" />
        <div v-else class="eval-history">
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
      </template>
    </section>
    <!-- 能力详情抽屉：点击卡片展开全部 evidence / metrics -->
    <Teleport to="body">
      <Transition name="cap-drawer">
        <div v-if="selectedCap" class="cap-overlay" @click.self="closeCap">
          <aside class="cap-drawer" role="dialog" aria-modal="true" aria-label="能力详情">
            <header class="drawer-head">
              <div>
                <span class="drawer-kicker">CAPABILITY DETAIL</span>
                <span class="drawer-cat">{{ selectedCap.category || '未分类' }}</span>
              </div>
              <button class="drawer-close" type="button" aria-label="关闭" @click="closeCap">×</button>
            </header>
            <div class="drawer-body">
              <div class="drawer-hero">
                <span class="cap-status" :class="statusClass(selectedCap.status)">{{ statusLabel(selectedCap.status) }}</span>
                <h2>{{ selectedCap.name }}</h2>
              </div>

              <section class="drawer-section">
                <h3>证据</h3>
                <p class="drawer-evidence">{{ selectedCap.evidence || '暂无证据说明' }}</p>
              </section>

              <section class="drawer-section">
                <h3>全部指标 <span class="drawer-count">{{ metricEntries(selectedCap.metrics).length }}</span></h3>
                <div v-if="metricEntries(selectedCap.metrics).length" class="drawer-metrics">
                  <div v-for="[k, v] in metricEntries(selectedCap.metrics)" :key="k" class="drawer-metric">
                    <span class="dm-k">{{ metricLabel(k) }}</span>
                    <span class="dm-v mono">{{ metricValue(v) }}</span>
                  </div>
                </div>
                <p v-else class="drawer-empty">暂无指标</p>
              </section>
            </div>
          </aside>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { get, post, clearToken } from '../api/http.js'
import BaseChart from './charts/BaseChart.vue'
import StateBlock from './StateBlock.vue'

const tab = ref('caps')
const caps = ref(null)
const capsLoading = ref(false)
const capsError = ref('')
const demo = ref(null)
const demoRunning = ref(false)
const demoError = ref('')
const evalResult = ref(null)
const evalHistory = ref([])
const historyLoading = ref(false)
const historyError = ref('')
const evalRunning = ref(false)
const evalError = ref('')
const evalSize = ref(30)
const evalMode = ref('paraphrase')
const selectedCap = ref(null)

const capItems = computed(() => Array.isArray(caps.value?.items) ? caps.value.items : [])
const implementedCount = computed(() => {
  const declared = Number(caps.value?.implemented)
  if (Number.isFinite(declared)) return declared
  return capItems.value.filter(c => String(c.status || '').toLowerCase() === 'implemented').length
})
const totalCount = computed(() => {
  const declared = Number(caps.value?.total)
  return Number.isFinite(declared) && declared > 0 ? declared : capItems.value.length
})
const partialCount = computed(() => capItems.value.filter(c => String(c.status || '').toLowerCase() === 'partial').length)
const plannedCount = computed(() => Math.max(totalCount.value - implementedCount.value - partialCount.value, 0))
const achievementRate = computed(() => {
  if (!totalCount.value) return 0
  return Math.round((implementedCount.value / totalCount.value) * 100)
})
const capGroups = computed(() => {
  const map = new Map()
  for (const item of capItems.value) {
    const category = item.category || '未分类'
    if (!map.has(category)) map.set(category, [])
    map.get(category).push(item)
  }
  return Array.from(map.entries()).map(([category, items]) => ({
    category,
    items,
    implemented: items.filter(c => String(c.status || '').toLowerCase() === 'implemented').length
  }))
})
const ringOption = computed(() => {
  const done = Math.max(implementedCount.value, 0)
  const rest = Math.max(totalCount.value - done, 0)
  return {
    animationDuration: 700,
    title: {
      text: achievementRate.value + '%',
      subtext: '实现率',
      left: 'center',
      top: '35%',
      textStyle: {
        color: 'var(--text-primary)',
        fontFamily: 'var(--font-serif)',
        fontSize: 25,
        fontWeight: 500
      },
      subtextStyle: {
        color: 'var(--text-tertiary)',
        fontFamily: 'var(--font-sans)',
        fontSize: 10
      }
    },
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    series: [{
      type: 'pie',
      radius: ['69%', '87%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: false,
      silent: false,
      label: { show: false },
      labelLine: { show: false },
      itemStyle: { borderWidth: 0 },
      data: [
        { value: done, name: '已实现', itemStyle: { color: 'token:accent' } },
        { value: rest, name: '待实现', itemStyle: { color: 'var(--bg-step)' } }
      ]
    }]
  }
})

function statusClass(status) {
  const s = String(status || '').toLowerCase()
  if (s === 'implemented' || s === 'done' || s === 'ok') return 'implemented'
  if (s === 'partial' || s === 'in_progress' || s === 'warning') return 'partial'
  return 'planned'
}

function statusLabel(status) {
  const s = String(status || '').toLowerCase()
  if (s === 'implemented' || s === 'done' || s === 'ok') return '已实现'
  if (s === 'partial' || s === 'in_progress' || s === 'warning') return '部分实现'
  return '计划中'
}

function metricEntries(metrics) {
  if (!metrics || typeof metrics !== 'object' || Array.isArray(metrics)) return []
  return Object.entries(metrics)
}

function metricLabel(key) {
  return String(key || '')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
}

function metricValue(value) {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) return value.length ? value.join(', ') : '空'
  if (typeof value === 'object') {
    const entries = Object.entries(value)
    if (!entries.length) return '空对象'
    return entries.map(([k, v]) => metricLabel(k) + ': ' + metricValue(v)).join(' · ')
  }
  return String(value)
}

function openCap(cap) {
  selectedCap.value = cap
}

function closeCap() {
  selectedCap.value = null
}

function handleDrawerKey(event) {
  if (event.key === 'Escape') closeCap()
}

function errorText(e, fallback = '数据加载失败，请稍后重试') {
  if (e && e.status === 403) return '无权限（需要管理员）'
  if (e && e.status === 401) return '登录已过期，请重新登录'
  return (e && e.message) || fallback
}

function relogin() {
  clearToken()
  localStorage.removeItem('user')
  const current = `${window.location.pathname}${window.location.search}`
  window.location.href = `/login?redirect=${encodeURIComponent(current)}`
}

async function loadCaps() {
  capsLoading.value = true
  capsError.value = ''
  caps.value = null
  try {
    caps.value = await get('/system/capabilities')
  } catch (e) {
    capsError.value = errorText(e, '能力矩阵加载失败')
  } finally {
    capsLoading.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  historyError.value = ''
  try {
    const data = await get('/admin/eval/history', { limit: 10 })
    evalHistory.value = Array.isArray(data) ? data : (data?.items || [])
  } catch (e) {
    evalHistory.value = []
    historyError.value = errorText(e, '评测历史加载失败')
  } finally {
    historyLoading.value = false
  }
}

async function runDemo() {
  demoRunning.value = true
  demo.value = null
  demoError.value = ''
  try {
    demo.value = await post('/admin/demo/run', {})
  } catch (e) {
    demoError.value = errorText(e, '演示请求失败')
  } finally {
    demoRunning.value = false
  }
}

async function runEval() {
  evalRunning.value = true
  evalResult.value = null
  evalError.value = ''
  try {
    evalResult.value = await post('/admin/eval/auto', {
      size: evalSize.value,
      topK: 5,
      mode: evalMode.value,
      runName: evalMode.value + '-' + Date.now()
    })
    await loadHistory()
  } catch (e) {
    evalError.value = errorText(e, '评测运行失败')
  } finally {
    evalRunning.value = false
  }
}

function fmt(v) {
  return v == null ? '-' : Number(v).toFixed(3)
}

onMounted(() => {
  loadCaps()
  loadHistory()
  window.addEventListener('keydown', handleDrawerKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleDrawerKey)
})
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

.caps-skeleton-summary { height: 18px; width: 220px; border-radius: var(--radius-xs); background: var(--bg-hover); margin-bottom: 14px; }
.caps-skeleton-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.caps-skeleton-card { display: flex; flex-direction: column; gap: 9px; padding: 14px; border: 1px solid var(--rule); border-radius: var(--radius); background: var(--bg-sidebar); }
.sk-line { display: block; height: 10px; width: 100%; border-radius: var(--radius-xs); background: var(--bg-hover); animation: state-pulse 1.4s ease-in-out infinite; }
.sk-line-sm { width: 34%; height: 8px; }
.sk-line-lg { width: 62%; height: 16px; }
.sk-line-md { width: 76%; }
@keyframes state-pulse { 0%, 100% { opacity: .45; } 50% { opacity: .9; } }
.state-actions { display: flex; justify-content: center; gap: 8px; }
.state-action-btn { padding: 5px 14px; border: 1px solid var(--border-secondary); border-radius: var(--radius-sm); color: var(--text-secondary); background: var(--bg-hover); font-size: .75rem; }
.state-action-btn:hover { color: var(--accent); border-color: var(--accent-border); }

/* ===== 能力地图 ===== */
.caps-overview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 250px;
  align-items: center;
  gap: 28px;
  margin-bottom: 26px;
  padding: 20px 24px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--bg-sidebar), var(--bg-step));
}
.caps-overview-copy { min-width: 0; }
.overview-kicker {
  font-size: 0.625rem;
  letter-spacing: 0.18em;
  color: var(--text-tertiary);
}
.overview-number {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 6px 0 4px;
  font-family: var(--font-serif);
}
.overview-number b {
  font-size: clamp(3rem, 6vw, 5rem);
  line-height: 0.95;
  letter-spacing: -0.04em;
  font-weight: 500;
  color: var(--text-primary);
}
.overview-number span {
  font-size: 1.375rem;
  color: var(--text-tertiary);
}
.overview-note {
  margin: 0 0 14px;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}
.overview-note strong { color: var(--accent); font-weight: 600; }
.overview-legend { display: flex; flex-wrap: wrap; gap: 16px; font-size: 0.6875rem; color: var(--text-tertiary); }
.overview-legend span { display: inline-flex; align-items: center; gap: 6px; }
.legend-dot { width: 6px; height: 6px; border-radius: 50%; display: inline-block; }
.legend-dot.done { background: var(--accent); }
.legend-dot.partial { background: var(--accent-love); }
.legend-dot.planned { background: var(--text-tertiary); opacity: 0.45; }
.caps-ring { min-width: 0; }

.cap-group { margin-bottom: 24px; }
.cap-group-head { display: flex; align-items: center; gap: 14px; margin-bottom: 10px; }
.cap-group-head h2 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--text-primary);
}
.cap-group-sub { display: block; margin-top: 2px; font-size: 0.625rem; color: var(--text-tertiary); }
.cap-group-line { flex: 1; height: 1px; background: var(--rule); }

.cap-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.cap-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  min-height: 168px;
  padding: 14px 15px;
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  background: var(--bg-sidebar);
  text-align: left;
  color: inherit;
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-out), border-color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);
}
.cap-card:hover {
  transform: translateY(-2px);
  border-color: var(--accent-border);
  background: var(--bg-hover);
}
.cap-card.planned { opacity: 0.68; }
.cap-card.planned:hover { opacity: 1; }
.cap-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.cap-status {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: 0.625rem;
  letter-spacing: 0.02em;
}
.cap-status.implemented { background: var(--success-bg); color: var(--success); }
.cap-status.partial { background: var(--accent-love-bg); color: var(--accent-love); }
.cap-status.planned { background: var(--bg-step); color: var(--text-tertiary); }
.cap-open-hint { font-size: 0.625rem; color: var(--text-tertiary); opacity: 0; transition: opacity var(--duration-fast) var(--ease-out); }
.cap-card:hover .cap-open-hint { opacity: 1; }
.cap-name {
  margin: 0 0 6px;
  font-family: var(--font-serif);
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--text-primary);
}
.cap-evidence {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.9em;
  margin: 0 0 10px;
  font-size: 0.75rem;
  line-height: 1.55;
  color: var(--text-tertiary);
}
.cap-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: auto; }
.cap-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 7px;
  border: 1px solid var(--rule);
  border-radius: var(--radius-xs);
  background: var(--bg-step);
  font-size: 0.625rem;
}
.chip-k { color: var(--text-tertiary); white-space: nowrap; }
.chip-v { color: var(--accent); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cap-chip.more { color: var(--text-tertiary); }

.cap-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
  background: rgba(0, 0, 0, 0.46);
  backdrop-filter: blur(2px);
}
.cap-drawer {
  width: min(460px, 92vw);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-sidebar);
  border-left: 1px solid var(--rule);
  box-shadow: var(--shadow-lg);
}
.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 22px 16px;
  border-bottom: 1px solid var(--rule);
}
.drawer-kicker { display: block; font-size: 0.5625rem; letter-spacing: 0.18em; color: var(--text-tertiary); }
.drawer-cat { display: block; margin-top: 4px; font-size: 0.75rem; color: var(--text-secondary); }
.drawer-close {
  width: 30px;
  height: 30px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 1.125rem;
  line-height: 1;
}
.drawer-close:hover { color: var(--accent); border-color: var(--accent-border); }
.drawer-body { flex: 1; overflow-y: auto; padding: 22px; }
.drawer-hero { padding-bottom: 18px; border-bottom: 1px solid var(--rule); }
.drawer-hero h2 { margin: 10px 0 0; font-family: var(--font-serif); font-size: 1.5rem; font-weight: 500; }
.drawer-section { margin-top: 22px; }
.drawer-section h3 {
  margin: 0 0 10px;
  font-size: 0.625rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  font-weight: 500;
}
.drawer-count { margin-left: 6px; color: var(--accent); }
.drawer-evidence {
  margin: 0;
  padding: 12px 14px;
  border-left: 2px solid var(--accent);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: var(--bg-step);
  font-size: 0.8125rem;
  line-height: 1.8;
  color: var(--text-secondary);
}
.drawer-metrics { border: 1px solid var(--rule); border-radius: var(--radius-sm); overflow: hidden; }
.drawer-metric {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 9px 12px;
  font-size: 0.75rem;
}
.drawer-metric + .drawer-metric { border-top: 1px solid var(--rule); }
.dm-k { color: var(--text-tertiary); word-break: break-word; }
.dm-v { color: var(--accent); text-align: right; word-break: break-word; }
.drawer-empty { font-size: 0.75rem; color: var(--text-tertiary); }
.cap-drawer-enter-active, .cap-drawer-leave-active { transition: opacity 200ms var(--ease-out); }
.cap-drawer-enter-active .cap-drawer, .cap-drawer-leave-active .cap-drawer { transition: transform 240ms var(--ease-out); }
.cap-drawer-enter-from, .cap-drawer-leave-to { opacity: 0; }
.cap-drawer-enter-from .cap-drawer, .cap-drawer-leave-to .cap-drawer { transform: translateX(100%); }

@media (max-width: 720px) {
  .caps-overview { grid-template-columns: 1fr; gap: 8px; padding: 16px 18px; }
  .caps-ring { display: none; }
  .cap-grid { grid-template-columns: 1fr; }
}
</style>
