<template>
  <div class="analytics-page">
    <!-- 顶部栏 -->
    <div class="analytics-header">
      <button class="back-btn" aria-label="返回对话" @click="$router.push('/')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
        <span>返回对话</span>
      </button>
      <h1 class="page-title">对话分析仪表盘</h1>
      <button class="export-btn" aria-label="导出全部对话CSV" @click="exportAll">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/>
          <line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        <span>导出全部 CSV</span>
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
      <span>正在加载分析数据...</span>
    </div>

    <template v-else>
      <!-- 总览统计卡片 -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon stat-icon-accent">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value tnum">{{ overview.totalConversations || 0 }}</div>
            <div class="stat-label">总会话数</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon stat-icon-danger">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="8.5" cy="7" r="4"/>
              <line x1="20" y1="8" x2="20" y2="14"/>
              <line x1="23" y1="11" x2="17" y2="11"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value tnum">{{ overview.handoffRate || '0.00' }}</div>
            <div class="stat-label">转人工率 ({{ overview.handoffConversations || 0 }} 次)</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon stat-icon-accent">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value tnum">{{ overview.avgTurns || '0.0' }}</div>
            <div class="stat-label">平均对话轮次</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon stat-icon-love">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value tnum">{{ overview.clarificationCount || 0 }}</div>
            <div class="stat-label">澄清触发次数</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon stat-icon-success">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value tnum">{{ overview.avgConfidence || 'N/A' }}</div>
            <div class="stat-label">平均置信度</div>
          </div>
        </div>
      </div>

      <!-- 图表统计：占比 + 意图一行，会话趋势占整行 -->
      <div class="section-card">
        <h2 class="section-title">意图分布统计</h2>
        <div class="charts-grid">
          <div class="chart-panel chart-panel-ratio">
            <h3 class="chart-subtitle">转人工与澄清占比</h3>
            <div v-if="ratioChartError" class="chart-message chart-message-error">数据加载失败</div>
            <div v-else-if="ratioTotal === 0" class="chart-message">暂无占比数据</div>
            <BaseChart v-else :option="ratioChartOption" height="280px" />
          </div>

          <div class="chart-panel chart-panel-intent">
            <h3 class="chart-subtitle">意图分布</h3>
            <div v-if="intentChartError" class="chart-message chart-message-error">数据加载失败</div>
            <div v-else-if="intentDist.length === 0" class="chart-message">暂无意图分布数据</div>
            <BaseChart v-else :option="intentChartOption" :height="intentChartHeight" />
          </div>

          <div class="chart-panel chart-panel-trend">
            <h3 class="chart-subtitle">会话趋势</h3>
            <div v-if="trendChartError" class="chart-message chart-message-error">数据加载失败</div>
            <div v-else-if="trendPoints.length === 0" class="chart-message">暂无会话趋势数据</div>
            <BaseChart v-else :option="trendChartOption" height="260px" />
          </div>
        </div>
      </div>

      <!-- 会话列表 -->
      <div class="section-card">
        <h2 class="section-title">会话列表</h2>

        <!-- 筛选栏 -->
        <div class="filter-bar">
          <div class="filter-group">
            <label class="filter-label">主要意图</label>
            <select class="filter-select" v-model="query.intent" @change="applyFilters">
              <option value="">全部</option>
              <option v-for="(label, key) in intentNames" :key="key" :value="key">{{ label }}</option>
            </select>
          </div>
          <div class="filter-group">
            <label class="filter-label">转人工</label>
            <div class="filter-btn-group">
              <button class="filter-btn" :class="{ active: query.handoff === null }" @click="query.handoff = null; applyFilters()">全部</button>
              <button class="filter-btn" :class="{ active: query.handoff === true }" @click="query.handoff = true; applyFilters()">是</button>
              <button class="filter-btn" :class="{ active: query.handoff === false }" @click="query.handoff = false; applyFilters()">否</button>
            </div>
          </div>
          <div class="filter-group">
            <label class="filter-label">有澄清</label>
            <div class="filter-btn-group">
              <button class="filter-btn" :class="{ active: query.clarification === null }" @click="query.clarification = null; applyFilters()">全部</button>
              <button class="filter-btn" :class="{ active: query.clarification === true }" @click="query.clarification = true; applyFilters()">是</button>
            </div>
          </div>
        </div>

        <div v-if="sessions.length === 0 && !sessionsLoading" class="empty-hint">暂无会话数据</div>
        <div v-else class="session-table-wrap">
          <table class="session-table">
            <thead>
              <tr>
                <th>会话标题</th>
                <th class="sortable" @click="toggleSort('total_turns')">
                  轮次
                  <span class="sort-icon" v-if="query.sortBy === 'total_turns'">{{ query.sortOrder === 'asc' ? '&#9650;' : '&#9660;' }}</span>
                </th>
                <th>主要意图</th>
                <th class="sortable" @click="toggleSort('avg_confidence')">
                  置信度
                  <span class="sort-icon" v-if="query.sortBy === 'avg_confidence'">{{ query.sortOrder === 'asc' ? '&#9650;' : '&#9660;' }}</span>
                </th>
                <th class="sortable" @click="toggleSort('clarification_count')">
                  澄清
                  <span class="sort-icon" v-if="query.sortBy === 'clarification_count'">{{ query.sortOrder === 'asc' ? '&#9650;' : '&#9660;' }}</span>
                </th>
                <th>转人工</th>
                <th class="sortable" @click="toggleSort('created_at')">
                  创建时间
                  <span class="sort-icon" v-if="query.sortBy === 'created_at'">{{ query.sortOrder === 'asc' ? '&#9650;' : '&#9660;' }}</span>
                </th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in sessions" :key="s.conversation_id">
                <td class="cell-title">{{ s.title || '新对话' }}</td>
                <td>{{ s.total_turns }}</td>
                <td>
                  <span v-if="s.top_intent" class="intent-tag">{{ formatIntent(s.top_intent) }}</span>
                  <span v-else class="text-muted">-</span>
                </td>
                <td>{{ s.avg_confidence }}</td>
                <td>{{ s.clarification_count }}</td>
                <td>
                  <span v-if="s.handoff_triggered" class="badge badge-danger">是</span>
                  <span v-else class="badge badge-ok">否</span>
                </td>
                <td class="cell-time">{{ formatTime(s.created_at) }}</td>
                <td class="cell-actions">
                  <button class="action-btn" @click="viewDetail(s.conversation_id)">详情</button>
                  <button class="action-btn" @click="exportSession(s.conversation_id)">导出</button>
                </td>
              </tr>
            </tbody>
          </table>

          <!-- 分页控件 -->
          <div class="pagination" v-if="pagination.totalPages > 1">
            <button class="page-btn" :disabled="query.page <= 1" @click="goToPage(1)">&#171;</button>
            <button class="page-btn" :disabled="query.page <= 1" @click="goToPage(query.page - 1)">&#8249;</button>
            <button
              v-for="p in visiblePages"
              :key="p"
              class="page-btn"
              :class="{ active: p === query.page }"
              @click="goToPage(p)"
            >{{ p }}</button>
            <button class="page-btn" :disabled="query.page >= pagination.totalPages" @click="goToPage(query.page + 1)">&#8250;</button>
            <button class="page-btn" :disabled="query.page >= pagination.totalPages" @click="goToPage(pagination.totalPages)">&#187;</button>
            <span class="page-info">共 {{ pagination.total }} 条</span>
          </div>
        </div>
      </div>
    </template>

    <!-- 会话详情弹窗 -->
    <div v-if="showDetail" class="modal-overlay" @click.self="showDetail = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>会话详情回放</h3>
          <button class="modal-close" @click="showDetail = false">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div v-if="detailLoading" class="loading-state" style="padding: 40px 0;">
          <div class="spinner"></div>
          <span>加载中...</span>
        </div>
        <div v-else class="modal-body">
          <div class="detail-meta">
            <span><strong>会话：</strong>{{ sessionDetail.title || '新对话' }}</span>
            <span><strong>创建：</strong>{{ formatTime(sessionDetail.createdAt) }}</span>
          </div>
          <div class="replay-list">
            <div
              v-for="msg in sessionDetail.messages"
              :key="msg.id"
              class="replay-msg"
              :class="msg.role"
            >
              <div class="replay-role">{{ msg.role === 'user' ? '用户' : '客服' }}</div>
              <div class="replay-content">{{ msg.content }}</div>
              <div v-if="msg.intent || msg.confidence || msg.isHandoff" class="replay-meta">
                <span v-if="msg.intent" class="intent-tag">{{ formatIntent(msg.intent) }}</span>
                <span v-if="msg.confidence" class="meta-conf">置信度: {{ msg.confidence.toFixed(2) }}</span>
                <span v-if="msg.isHandoff" class="badge badge-danger">转人工</span>
                <span v-if="msg.isClarification" class="badge badge-warn">澄清</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  getOverview,
  getIntentDistribution,
  getSessionList,
  getSessionDetail,
  getExportAllCsvUrl,
  getExportSessionCsvUrl
} from '../api/analytics.js'
import BaseChart from './charts/BaseChart.vue'

/* ---- 全局状态 ---- */
const loading = ref(true)
const sessionsLoading = ref(false)
const overview = ref({})
const intentDist = ref([])

/* ---- 图表独立状态：任一接口失败只降级对应图表 ---- */
const intentChartError = ref(false)
const trendChartError = ref(false)
const ratioChartError = ref(false)
const trendPoints = ref([])

/* ---- 分页/排序/筛选查询状态 ---- */
const query = ref({
  page: 1,
  size: 15,
  sortBy: 'created_at',
  sortOrder: 'desc',
  intent: '',
  handoff: null,
  clarification: null
})

/* ---- 会话列表 + 分页元数据 ---- */
const sessions = ref([])
const pagination = ref({ total: 0, page: 1, size: 15, totalPages: 0 })

/* ---- 弹窗状态 ---- */
const showDetail = ref(false)
const detailLoading = ref(false)
const sessionDetail = ref({ messages: [] })

/* ---- 意图中文名映射 ---- */
const intentNames = {
  ORDER_QUERY: '订单查询',
  ORDER_CANCEL: '取消订单',
  ORDER_REFUND: '退款申请',
  PRODUCT_INFO: '产品咨询',
  PRODUCT_RECOMMEND: '产品推荐',
  COMPLAINT: '投诉建议',
  AFTER_SALE: '售后服务',
  POLICY_QUERY: '政策查询',
  CHITCHAT: '闲聊',
  REQUEST_HUMAN: '转人工',
  CLARIFICATION: '用户澄清',
  OUT_OF_SCOPE: '超出范围'
}

function formatIntent(intent) {
  if (!intent) return '-'
  return intentNames[intent] || intent
}

/* ---- 图表语义色标记：由 BaseChart 读取最新 CSS tokens 后解析 ---- */
const chartPalette = [
  'token:accent',
  'token:accent-love',
  'token:success',
  'token:warning',
  'token:danger',
  'token:text-tertiary'
]
/** 意图条形配色：语义色优先，其余用青瓷色按排名递减透明度，避免彩虹配色 */
function intentBarColor(intent, index) {
  if (intent === 'REQUEST_HUMAN') return 'token:accent-love'
  if (intent === 'COMPLAINT') return 'token:danger'
  if (intent === 'OUT_OF_SCOPE') return 'token:text-tertiary'
  if (index === 0) return 'token:accent'
  const alpha = Math.max(0.3, 0.72 - index * 0.06).toFixed(2)
  return 'token:accent:' + alpha
}

const chartFont = 'var(--font-serif)'
const chartMono = 'var(--font-mono)'

/* ---- 转人工与澄清占比（环形图） ---- */
const ratioTotal = computed(() => {
  const total = Number(overview.value.totalConversations) || 0
  const handoff = Number(overview.value.handoffConversations) || 0
  const clarification = Number(overview.value.clarificationCount) || 0
  return Math.max(total, handoff + clarification, 0)
})

const ratioItems = computed(() => {
  const total = Number(overview.value.totalConversations) || 0
  const handoff = Number(overview.value.handoffConversations) || 0
  const clarification = Number(overview.value.clarificationCount) || 0
  const other = Math.max(total - handoff - clarification, 0)

  return [
    { name: '转人工', value: handoff, itemStyle: { color: 'token:accent-love' } },
    { name: '澄清', value: clarification, itemStyle: { color: 'token:warning' } },
    { name: '其余', value: other, itemStyle: { color: 'token:text-tertiary' } }
  ]
})

const ratioChartOption = computed(() => ({
  animationDuration: 480,
  tooltip: {
    trigger: 'item',
    formatter: params => {
      return `${params.marker}${params.name}<br/><span style="font-family:${chartMono}">${params.value} 次（${params.percent}%）</span>`
    }
  },
  legend: {
    bottom: 0,
    left: 'center',
    icon: 'circle',
    itemWidth: 8,
    itemHeight: 8,
    itemGap: 16,
    textStyle: { color: 'var(--text-secondary)', fontFamily: chartFont, fontSize: 11 }
  },
  title: {
    text: String(ratioTotal.value),
    subtext: '总会话',
    left: 'center',
    top: '33%',
    textAlign: 'center',
    itemGap: 4,
    textStyle: {
      color: 'var(--text-primary)',
      fontFamily: chartMono,
      fontSize: 22,
      fontWeight: 600
    },
    subtextStyle: {
      color: 'var(--text-tertiary)',
      fontFamily: chartFont,
      fontSize: 11
    }
  },
  series: [
    {
      name: '会话占比',
      type: 'pie',
      radius: ['54%', '74%'],
      center: ['50%', '42%'],
      avoidLabelOverlap: true,
      itemStyle: {
        borderColor: 'var(--bg-sidebar)',
        borderWidth: 2,
        borderRadius: 2
      },
      label: { show: false },
      labelLine: { show: false },
      emphasis: { scale: true, scaleSize: 4 },
      data: ratioItems.value
    }
  ]
}))

/* ---- 意图分布（横向条形图） ---- */
const intentChartHeight = computed(() => {
  const height = Math.max(260, Math.min(420, intentDist.value.length * 30 + 48))
  return `${height}px`
})

const intentChartOption = computed(() => {
  const rows = [...intentDist.value].sort((a, b) => (Number(b.count) || 0) - (Number(a.count) || 0))

  return {
    animationDuration: 420,
    grid: { left: 80, right: 44, top: 8, bottom: 8 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        const item = Array.isArray(params) ? params[0] : params
        if (!item) return ''
        return `${item.marker}${item.name}<br/><span style="font-family:${chartMono}">${item.value} 次</span>`
      }
    },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: 'var(--text-tertiary)', fontFamily: chartMono, fontSize: 10 },
      splitLine: { lineStyle: { color: 'var(--rule)' } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map(item => formatIntent(item.intent)),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: 'var(--text-secondary)', fontFamily: chartFont, fontSize: 11 }
    },
    series: [
      {
        name: '意图数量',
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { borderRadius: [0, 2, 2, 0] },
        data: rows.map((item, index) => ({
          value: Number(item.count) || 0,
          itemStyle: { color: intentBarColor(item.intent, index) }
        })),
        label: {
          show: true,
          position: 'right',
          color: 'var(--text-primary)',
          fontFamily: chartMono,
          fontSize: 10
        },
        emphasis: { itemStyle: { opacity: 0.86 } }
      }
    ]
  }
})

/* ---- 会话趋势（按天聚合的折线面积图） ---- */
const trendChartOption = computed(() => ({
  animationDuration: 520,
  grid: { left: 44, right: 18, top: 24, bottom: 8 },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line', lineStyle: { color: 'var(--border-primary)' } },
    formatter: params => {
      const item = Array.isArray(params) ? params[0] : params
      if (!item) return ''
      return `${item.axisValue}<br/>${item.marker}会话数：<span style="font-family:${chartMono}">${item.value}</span>`
    }
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trendPoints.value.map(item => item.label),
    axisLine: { lineStyle: { color: 'var(--border-primary)' } },
    axisTick: { show: false },
    axisLabel: {
      color: 'var(--text-tertiary)',
      fontFamily: chartMono,
      fontSize: 10,
      hideOverlap: true
    }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: 'var(--text-tertiary)', fontFamily: chartMono, fontSize: 10 },
    splitLine: { lineStyle: { color: 'var(--rule)', type: 'dashed' } }
  },
  series: [
    {
      name: '会话数',
      type: 'line',
      smooth: 0.28,
      symbol: 'circle',
      symbolSize: 5,
      showSymbol: trendPoints.value.length <= 31,
      data: trendPoints.value.map(item => item.count),
      lineStyle: { width: 2, color: 'token:accent' },
      itemStyle: { color: 'token:accent', borderColor: 'var(--bg-sidebar)', borderWidth: 2 },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'token:accent:0.26' },
            { offset: 1, color: 'token:accent:0.02' }
          ]
        }
      },
      emphasis: { focus: 'series' }
    }
  ]
}))

/** 将 created_at 按本地日期聚合成 [{ label: 'MM-DD', count }] */
function buildTrendPoints(rows) {
  const buckets = new Map()
  rows.forEach(row => {
    if (!row || !row.created_at) return
    const date = new Date(row.created_at)
    if (Number.isNaN(date.getTime())) return
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const key = `${date.getFullYear()}-${month}-${day}`
    const bucket = buckets.get(key) || { key, label: `${month}-${day}`, count: 0 }
    bucket.count += 1
    buckets.set(key, bucket)
  })

  return Array.from(buckets.values())
    .sort((a, b) => a.key.localeCompare(b.key))
    .map(item => ({ label: item.label, count: item.count }))
}

/* ---- 时间格式化 ---- */
function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/* ---- 数据加载：概览与分布分别降级，避免单个接口失败导致白屏 ---- */
async function loadData() {
  loading.value = true
  intentChartError.value = false
  trendChartError.value = false
  ratioChartError.value = false

  try {
    const [overviewResult, intentResult] = await Promise.allSettled([
      getOverview(),
      getIntentDistribution()
    ])

    if (overviewResult.status === 'fulfilled') {
      overview.value = overviewResult.value || {}
    } else {
      ratioChartError.value = true
      console.error('加载总览数据失败:', overviewResult.reason)
    }

    if (intentResult.status === 'fulfilled') {
      intentDist.value = Array.isArray(intentResult.value) ? intentResult.value : []
    } else {
      intentChartError.value = true
      console.error('加载意图分布失败:', intentResult.reason)
    }

    await loadSessionList()
    await loadTrendData()
  } catch (e) {
    console.error('加载分析数据失败:', e)
  } finally {
    loading.value = false
  }
}

/** 趋势图单独请求较大分页；接口限制或失败时回退当前分页列表 */
async function loadTrendData() {
  trendChartError.value = false
  const requestedSize = 500
  let rows = []

  try {
    const result = await getSessionList({
      page: 1,
      size: requestedSize,
      sortBy: 'created_at',
      sortOrder: 'asc'
    })
    const content = Array.isArray(result) ? result : (result?.content || [])
    const serverSize = Number(result?.size) || 0

    // 服务端若把 pageSize 截断，则改用现有分页数据，避免趋势只统计到一小段
    rows = serverSize && serverSize < requestedSize && sessions.value.length
      ? sessions.value
      : content
  } catch (e) {
    console.error('加载会话趋势失败:', e)
    rows = sessions.value
    if (!rows.length) {
      trendChartError.value = true
      trendPoints.value = []
      return
    }
  }

  trendPoints.value = buildTrendPoints(rows)
}

/** 加载分页会话列表 */
async function loadSessionList() {
  sessionsLoading.value = true
  try {
    const params = {}
    if (query.value.page) params.page = query.value.page
    if (query.value.size) params.size = query.value.size
    if (query.value.sortBy) params.sortBy = query.value.sortBy
    if (query.value.sortOrder) params.sortOrder = query.value.sortOrder
    if (query.value.intent) params.intent = query.value.intent
    if (query.value.handoff !== null) params.handoff = query.value.handoff
    if (query.value.clarification !== null) params.clarification = query.value.clarification

    const result = await getSessionList(params)
    sessions.value = result.content || []
    pagination.value = {
      total: result.total || 0,
      page: result.page || 1,
      size: result.size || 15,
      totalPages: result.totalPages || 0
    }
  } catch (e) {
    console.error('加载会话列表失败:', e)
  } finally {
    sessionsLoading.value = false
  }
}

/* ---- 排序切换 ---- */
function toggleSort(column) {
  if (query.value.sortBy === column) {
    query.value.sortOrder = query.value.sortOrder === 'desc' ? 'asc' : 'desc'
  } else {
    query.value.sortBy = column
    query.value.sortOrder = 'desc'
  }
  query.value.page = 1
  loadSessionList()
}

/* ---- 筛选应用 ---- */
function applyFilters() {
  query.value.page = 1
  loadSessionList()
}

/* ---- 分页导航 ---- */
function goToPage(p) {
  if (p < 1 || p > pagination.value.totalPages) return
  query.value.page = p
  loadSessionList()
}

/** 可见的页码按钮（窗口分页） */
const visiblePages = computed(() => {
  const total = pagination.value.totalPages
  const current = query.value.page
  const pages = []
  const delta = 2
  const start = Math.max(1, current - delta)
  const end = Math.min(total, current + delta)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

/* ---- 查看会话详情 ---- */
async function viewDetail(conversationId) {
  showDetail.value = true
  detailLoading.value = true
  try {
    const detail = await getSessionDetail(conversationId)
    sessionDetail.value = detail || { messages: [] }
  } catch (e) {
    console.error('加载会话详情失败:', e)
    sessionDetail.value = { messages: [] }
  } finally {
    detailLoading.value = false
  }
}

/* ---- CSV 导出 ---- */
function exportAll() {
  const token = localStorage.getItem('token')
  const url = getExportAllCsvUrl()
  downloadCsv(url, 'dialog_logs_all.csv', token)
}

function exportSession(conversationId) {
  const token = localStorage.getItem('token')
  const url = getExportSessionCsvUrl(conversationId)
  downloadCsv(url, `dialog_${conversationId}.csv`, token)
}

/** 通用 CSV 下载 */
function downloadCsv(url, filename, token) {
  fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
    .then(res => {
      if (!res.ok) throw new Error('导出失败')
      return res.blob()
    })
    .then(blob => {
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = filename
      a.click()
      URL.revokeObjectURL(a.href)
    })
    .catch(e => alert('导出失败: ' + e.message))
}

onMounted(loadData)
</script>

<style scoped>
/* ===== 页面容器 ===== */
.analytics-page {
  height: 100dvh;
  overflow-y: auto;
  padding: 24px 32px;
  background: var(--bg-app);
}

/* ===== 顶部栏 ===== */
.analytics-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  color: var(--text-secondary);
  border: 1px solid var(--border-primary);
  transition: var(--transition);
}

.back-btn:hover {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--bg-hover);
}

.page-title {
  flex: 1;
  font-family: var(--font-serif);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: var(--tracking-tight);
}

.export-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-inverse);
  background: var(--accent);
  transition: var(--transition);
}

.export-btn:hover {
  background: var(--accent-hover);
}

/* ===== 加载状态 ===== */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 0;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--border-primary);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 统计卡片网格 ===== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  background: var(--bg-sidebar);
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  transition: var(--transition);
}

.stat-card:hover {
  background: var(--bg-hover);
  border-color: var(--accent-border);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-accent   { color: var(--accent); background: var(--accent-bg); }
.stat-icon-danger   { color: var(--danger); background: var(--danger-bg); }
.stat-icon-love     { color: var(--accent-love); background: var(--accent-love-bg); }
.stat-icon-success  { color: var(--success); background: var(--success-bg); }

.stat-value {
  font-family: var(--font-serif);
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-top: 2px;
}

/* ===== 通用区块卡片 ===== */
.section-card {
  background: var(--bg-sidebar);
  border: 1px solid var(--rule);
  border-radius: var(--radius);
  padding: 20px 24px;
  margin-bottom: 24px;
}

.section-title {
  font-family: var(--font-serif);
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
  letter-spacing: var(--tracking-tight);
}

.empty-hint {
  text-align: center;
  padding: 32px 0;
  font-size: 0.875rem;
  color: var(--text-tertiary);
}

/* ===== 图表网格 ===== */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chart-panel {
  min-width: 0;
  padding: 14px 14px 8px;
  background: var(--bg-input);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
}

.chart-panel-trend {
  grid-column: 1 / -1;
}

.chart-subtitle {
  margin-bottom: 8px;
  font-family: var(--font-serif);
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: var(--tracking-tight);
}

.chart-message {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  font-size: 0.8125rem;
  color: var(--text-tertiary);
  text-align: center;
}

.chart-message-error {
  color: var(--danger);
}

/* ===== 筛选栏 ===== */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--rule);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.3px;
}

.filter-select {
  padding: 5px 28px 5px 10px;
  font-size: 0.8125rem;
  color: var(--text-primary);
  background: var(--bg-input);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-xs);
  cursor: pointer;
  outline: none;
  transition: border-color 0.15s ease;
}

.filter-select:focus {
  border-color: var(--accent);
}

.filter-btn-group {
  display: flex;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-xs);
  overflow: hidden;
}

.filter-btn {
  padding: 4px 12px;
  font-size: 0.75rem;
  color: var(--text-secondary);
  background: var(--bg-input);
  border: none;
  border-right: 1px solid var(--border-primary);
  transition: background 0.15s ease, color 0.15s ease;
}

.filter-btn:last-child {
  border-right: none;
}

.filter-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.filter-btn.active {
  color: var(--text-inverse);
  background: var(--accent);
}

/* ===== 会话列表表格 ===== */
.session-table-wrap {
  overflow-x: auto;
}

.session-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}

.session-table th {
  text-align: left;
  padding: 10px 12px;
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--rule);
  white-space: nowrap;
}

.session-table td {
  padding: 10px 12px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--rule);
  white-space: nowrap;
}

.session-table tbody tr:hover {
  background: var(--bg-hover);
}

.sortable {
  cursor: pointer;
  user-select: none;
  transition: color 0.15s ease;
}

.sortable:hover {
  color: var(--accent);
}

.sort-icon {
  font-size: 0.6rem;
  margin-left: 4px;
  color: var(--accent);
}

.cell-title {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cell-time {
  font-size: 0.75rem;
  color: var(--text-secondary);
}

.cell-actions {
  display: flex;
  gap: 6px;
}

/* ===== 标签与徽章 ===== */
.intent-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  font-weight: 500;
  background: var(--accent-bg);
  color: var(--accent);
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
  font-weight: 600;
}

.badge-danger {
  background: var(--danger-bg);
  color: var(--danger);
}

.badge-ok {
  background: var(--success-bg);
  color: var(--success);
}

.badge-warn {
  background: var(--warning-bg);
  color: var(--warning);
}

.text-muted {
  color: var(--text-tertiary);
}

/* ===== 操作按钮 ===== */
.action-btn {
  padding: 4px 10px;
  border-radius: var(--radius-xs);
  font-size: 0.75rem;
  color: var(--accent);
  border: 1px solid var(--border-primary);
  transition: var(--transition);
}

.action-btn:hover {
  background: var(--accent);
  color: var(--text-inverse);
  border-color: var(--accent);
}

/* ===== 分页控件 ===== */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 16px 0 4px;
  border-top: 1px solid var(--rule);
  margin-top: 8px;
}

.page-btn {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-xs);
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.page-btn:hover:not(:disabled) {
  color: var(--accent);
  border-color: var(--accent);
  background: var(--bg-hover);
}

.page-btn.active {
  color: var(--text-inverse);
  background: var(--accent);
  border-color: var(--accent);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  margin-left: 12px;
  font-size: 0.75rem;
  color: var(--text-tertiary);
}

/* ===== 弹窗遮罩 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  padding: 24px;
}

.modal-content {
  width: 100%;
  max-width: 720px;
  max-height: 85dvh;
  background: var(--bg-sidebar);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--rule);
}

.modal-header h3 {
  font-family: var(--font-serif);
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
}

.modal-close {
  color: var(--text-secondary);
  transition: var(--transition);
  padding: 4px;
  border-radius: var(--radius-xs);
}

.modal-close:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

/* ===== 详情元信息 ===== */
.detail-meta {
  display: flex;
  gap: 24px;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--rule);
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

/* ===== 对话回放列表 ===== */
.replay-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.replay-msg {
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--rule);
}

.replay-msg.user {
  background: var(--accent-bg);
  border-color: var(--accent-border);
}

.replay-msg.assistant {
  background: var(--bg-input);
}

.replay-role {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.replay-msg.user .replay-role {
  color: var(--accent);
}

.replay-msg.assistant .replay-role {
  color: var(--text-secondary);
}

.replay-content {
  font-size: 0.875rem;
  color: var(--text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.replay-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.meta-conf {
  font-size: 0.7rem;
  color: var(--text-tertiary);
  padding: 2px 6px;
  background: var(--bg-hover);
  border-radius: var(--radius-xs);
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }

  .chart-panel-trend {
    grid-column: auto;
  }
}

@media (max-width: 768px) {
  .analytics-page {
    padding: 16px;
  }

  .stats-grid {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 10px;
  }

  .stat-card {
    padding: 14px 12px;
  }

  .analytics-header {
    flex-wrap: wrap;
  }

  .page-title {
    font-size: 1.1rem;
  }

  .filter-bar {
    gap: 12px;
  }

  .filter-group {
    flex-wrap: wrap;
  }
}
</style>
