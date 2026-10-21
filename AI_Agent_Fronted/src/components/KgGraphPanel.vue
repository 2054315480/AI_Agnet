<template>
  <section class="exp-card wide">
    <div class="card-head">
      <h2>知识图谱（实体链接增强）</h2>
      <span class="tag">可视化验证</span>
    </div>
    <p class="card-desc">
      输入问题，后端会做实体链接 + 一跳扩散，返回 nodes/links。下面是后端返回的真实图数据渲染结果，
      可以展开「原始响应」对照 JSON，确认不是前端写死的。
    </p>

    <div class="controls">
      <input v-model="query" class="q-input" placeholder="输入问题，如：耳机坏了想退货退款多久到账" @keyup.enter="loadGraph" />
      <button class="run-btn" :disabled="loading" @click="loadGraph">{{ loading ? '查询中…' : '生成图谱' }}</button>
      <button class="ghost-btn" @click="loadGraph('')">全局概览</button>
      <label class="chk"><input v-model="showRaw" type="checkbox" /> 显示原始响应</label>
    </div>

    <div v-if="graph" class="kg-meta">
      <span>节点 <b class="mono">{{ graph.nodeCount }}</b></span>
      <span>关系 <b class="mono">{{ graph.linkCount }}</b></span>
      <span>命中实体 <b class="mono">{{ (graph.matched || []).length }}</b></span>
      <span class="muted">{{ graph.matched.map(m => m.name + '(' + m.type + ')').join('、') || '未命中实体（展示全局高频实体）' }}</span>
    </div>

    <div v-if="graph" ref="chartRef" class="kg-chart"></div>

    <div v-if="showRaw && graph" class="raw-box">
      <div class="raw-title">后端原始响应（POST /api/admin/kg/graph）</div>
      <pre class="raw-json">{{ rawText }}</pre>
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([GraphChart, TooltipComponent, LegendComponent, CanvasRenderer])

const query = ref('耳机坏了想退货退款多久到账')
const graph = ref(null)
const loading = ref(false)
const showRaw = ref(false)
const rawText = ref('')
const chartRef = ref(null)
let chart = null

const TYPE_COLOR = {
  PRODUCT: '#5E948A', PART: '#7FB3A6', POLICY: '#C2A36B', ISSUE: '#B4544A',
  ACTION: '#5C8A5F', CHANNEL: '#6E736C', DURATION: '#A97C3B', UNKNOWN: '#8A908A'
}

function headers() {
  const t = localStorage.getItem('token')
  return { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) }
}

async function loadGraph(q) {
  const text = q === undefined ? query.value : q
  loading.value = true
  try {
    const res = await fetch('/api/admin/kg/graph', {
      method: 'POST', headers: headers(), body: JSON.stringify({ query: text, limit: 25 })
    })
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const data = await res.json()
    graph.value = data
    rawText.value = JSON.stringify(data, null, 2)
    await nextTick()
    render()
  } catch (e) {
    graph.value = null
  } finally {
    loading.value = false
  }
}

function render() {
  if (!chartRef.value || !graph.value) return
  if (!chart) chart = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  const nodes = (graph.value.nodes || []).map(n => ({
    id: n.id,
    name: n.name,
    symbolSize: Math.min(46, 14 + (n.docFreq || 1) * 0.12),
    itemStyle: {
      color: TYPE_COLOR[n.type] || TYPE_COLOR.UNKNOWN,
      borderColor: n.matched ? '#E9E6DF' : 'transparent',
      borderWidth: n.matched ? 2 : 0
    },
    category: n.type
  }))
  const links = (graph.value.links || []).map(l => ({
    source: l.source, target: l.target,
    label: { show: false, formatter: l.relation },
    lineStyle: { color: 'rgba(233,230,223,0.18)', width: 1 }
  }))
  const categories = [...new Set(nodes.map(n => n.category))].map(c => ({ name: c }))
  chart.setOption({
    tooltip: {
      formatter: p => p.dataType === 'edge'
        ? (p.data.relation || '')
        : `${p.data.name}<br/>出现 ${p.data.symbolSize ? '' : ''}`
    },
    legend: [{ data: categories.map(c => c.name), textStyle: { color: '#8A908A', fontSize: 10 }, bottom: 0 }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: { repulsion: 220, edgeLength: 90, gravity: 0.08 },
      label: { show: true, color: '#E9E6DF', fontSize: 11, position: 'right' },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 6,
      categories,
      data: nodes,
      links,
      lineStyle: { color: 'rgba(233,230,223,0.16)', curveness: 0.12 },
      emphasis: { focus: 'adjacency', lineStyle: { width: 2, color: '#5E948A' } }
    }]
  }, true)
}

let resizeObs = null
onMounted(() => {
  loadGraph()
  resizeObs = new ResizeObserver(() => chart && chart.resize())
  nextTick(() => { if (chartRef.value) resizeObs.observe(chartRef.value) })
})
onUnmounted(() => {
  if (resizeObs) resizeObs.disconnect()
  if (chart) { chart.dispose(); chart = null }
})
watch(showRaw, () => nextTick(() => chart && chart.resize()))
</script>

<style scoped>
.q-input { flex: 1; min-width: 240px; padding: 7px 10px; border: 1px solid var(--border-input); border-radius: var(--radius-sm); background: var(--bg-input); color: var(--text-primary); font-size: 0.8125rem; }
.chk { display: inline-flex; align-items: center; gap: 5px; font-size: 0.75rem; color: var(--text-tertiary); }
.kg-meta { display: flex; flex-wrap: wrap; gap: 14px; margin: 10px 0 6px; font-size: 0.75rem; color: var(--text-tertiary); align-items: baseline; }
.kg-meta b { color: var(--accent); font-family: var(--font-mono); }
.kg-chart { width: 100%; height: 420px; border: 1px solid var(--rule); border-radius: var(--radius-sm); background: var(--bg-app); }
.raw-box { margin-top: 12px; border: 1px solid var(--rule); border-radius: var(--radius-sm); overflow: hidden; }
.raw-title { padding: 7px 10px; font-size: 0.6875rem; color: var(--text-tertiary); background: var(--bg-step); border-bottom: 1px solid var(--rule); }
.raw-json { margin: 0; padding: 10px; max-height: 260px; overflow: auto; font-family: var(--font-mono); font-size: 0.6875rem; line-height: 1.6; color: var(--text-secondary); }
</style>
