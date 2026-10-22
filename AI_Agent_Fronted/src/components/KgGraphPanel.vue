<template>
  <section class="exp-card wide">
    <div class="card-head">
      <h2>知识图谱（实体链接增强）</h2>
      <span class="tag">可视化验证</span>
    </div>
    <p class="card-desc">
      输入问题，后端会做实体链接 + 一跳扩散，返回 nodes/links。下面是后端返回的真实图数据渲染结果，
      可以展开「原始响应」对照 JSON，确认不是前端写死的。
      <br /><span class="tip">白圈高亮 = query 命中的实体；线越亮表示与命中实体直接相关；hover 节点看类型与出现次数，hover 连线看关系名。</span>
    </p>

    <div class="controls">
      <input v-model="query" class="q-input" placeholder="输入问题，如：耳机坏了想退货退款多久到账" @keyup.enter="loadGraph" />
      <button class="run-btn" :disabled="loading" @click="loadGraph">{{ loading ? '查询中…' : '生成图谱' }}</button>
      <button class="ghost-btn" @click="loadGraph('')">全局概览</button>
      <div class="view-switch">
        <button :class="{ on: view === 'graph' }" @click="view = 'graph'">关系图</button>
        <button :class="{ on: view === 'table' }" @click="view = 'table'">关系表</button>
      </div>
      <label class="chk"><input v-model="showRaw" type="checkbox" /> 显示原始响应</label>
    </div>

    <!-- 关系类型图例（带数量） -->
    <div v-if="graph && relationStats.length" class="rel-legend">
      <span class="rl-label">关系类型：</span>
      <span v-for="r in relationStats" :key="r.name" class="rl-item">
        <i class="rl-dot" :style="{ background: relColor(r.name) }"></i>
        <b class="mono">{{ r.name }}</b>
        <span class="rl-count">{{ r.count }}</span>
      </span>
    </div>

    <div v-if="errMsg" class="err-msg">{{ errMsg }}</div>

    <div v-if="graph" class="kg-meta">
      <span>节点 <b class="mono">{{ graph.nodeCount }}</b></span>
      <span>关系 <b class="mono">{{ graph.linkCount }}</b></span>
      <span>命中实体 <b class="mono">{{ (graph.matched || []).length }}</b></span>
      <span class="muted">{{ graph.matched.map(m => m.name + '(' + m.type + ')').join('、') || '未命中实体（展示全局高频实体）' }}</span>
    </div>

    <div v-if="graph && view === 'graph'" ref="chartRef" class="kg-chart"></div>

    <!-- 关系表视图：图看不清时直接看「源 → 关系 → 目标」 -->
    <div v-if="graph && view === 'table'" class="rel-table-wrap">
      <table class="rel-table">
        <thead>
          <tr><th>源实体</th><th>关系</th><th>目标实体</th><th>类型</th></tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in relationRows" :key="i">
            <td class="ent">{{ r.sourceName }}</td>
            <td><span class="rel-chip" :style="{ color: relColor(r.relation), borderColor: relColor(r.relation) }">{{ r.relation }}</span></td>
            <td class="ent">{{ r.targetName }}</td>
            <td class="muted mono">{{ r.sourceType }} → {{ r.targetType }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!relationRows.length" class="ex-empty">当前视图没有关系数据</div>
    </div>

    <div v-if="showRaw && graph" class="raw-box">
      <div class="raw-title">后端原始响应（POST /api/admin/kg/graph）</div>
      <pre class="raw-json">{{ rawText }}</pre>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { post } from '../api/http.js'

echarts.use([GraphChart, TooltipComponent, LegendComponent, CanvasRenderer])

const query = ref('耳机坏了想退货退款多久到账')
const graph = ref(null)
const loading = ref(false)
const showRaw = ref(false)
const view = ref('graph')
const errMsg = ref('')
const rawText = ref('')
const chartRef = ref(null)
let chart = null

// 节点配色：整体提高亮度，避免深色背景下发灰
const TYPE_COLOR = {
  PRODUCT: '#7FD1BF', PART: '#A8DCCF', POLICY: '#F2C46B', ISSUE: '#FF7B6B',
  ACTION: '#7FCF85', CHANNEL: '#C2C7BE', DURATION: '#FFC46B', UNKNOWN: '#B9BDB6'
}

// 关系配色：不同关系用不同颜色，一眼能区分"是什么关系"
const REL_COLOR = {
  resolved_by: '#7FD1BF',   // 问题 -> 动作：青
  has_policy: '#F2C46B',    // 产品 -> 政策：黄铜
  requires: '#FFC46B',      // 动作 -> 条件：琥珀
  applies_to: '#F2C46B',
  co_occurs: '#9AA39A'      // 共现：灰白（弱化但不能看不见）
}

function relColor(name) {
  return REL_COLOR[name] || '#CFD4CB'
}

const relationStats = computed(() => {
  const m = new Map()
  for (const l of (graph.value?.links || [])) {
    m.set(l.relation, (m.get(l.relation) || 0) + 1)
  }
  return [...m.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count)
})

const nodeMap = computed(() => {
  const m = new Map()
  for (const n of (graph.value?.nodes || [])) m.set(String(n.id), n)
  return m
})

const relationRows = computed(() => {
  const nm = nodeMap.value
  return (graph.value?.links || []).map(l => {
    const s = nm.get(String(l.source)) || {}
    const t = nm.get(String(l.target)) || {}
    return {
      sourceName: s.name || l.source,
      targetName: t.name || l.target,
      sourceType: s.type || '-',
      targetType: t.type || '-',
      relation: l.relation
    }
  }).sort((a, b) => a.relation.localeCompare(b.relation))
})

async function loadGraph(q) {
  const text = q === undefined ? query.value : q
  loading.value = true
  errMsg.value = ''
  try {
    const data = await post('/admin/kg/graph', { query: text, limit: 25 })
    graph.value = data
    rawText.value = JSON.stringify(data, null, 2)
    await nextTick()
    render()
  } catch (e) {
    graph.value = null
    errMsg.value = '加载失败: ' + (e && e.message ? e.message : e)
  } finally {
    loading.value = false
  }
}

function render() {
  if (!chartRef.value || !graph.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  }

  const matchedIds = new Set((graph.value.matched || []).map(m => String(m.id)))
  const rawNodes = graph.value.nodes || []
  const rawLinks = graph.value.links || []
  // 关系名标注：边数少时才显示，避免线条糊成一片
  const linksLabelOn = rawLinks.length <= 60

  // 边裁剪：197 条边铺满屏幕会糊成一片，只保留与命中实体相关的边 + 权重最高的若干条
  const keep = new Set()
  rawLinks.forEach((l, i) => {
    if (matchedIds.has(String(l.source)) || matchedIds.has(String(l.target))) {
      keep.add(i)
    }
  })
  // 若相关边太少，再补一些 co_occurs 关系（最多 60 条），保证图不至于太空
  if (keep.size < 20) {
    for (let i = 0; i < rawLinks.length && keep.size < 60; i++) keep.add(i)
  }
  const links = rawLinks
    .filter((l, i) => keep.has(i))
    .map(l => {
      const c = relColor(l.relation)
      const strong = l.relation !== 'co_occurs'
      return {
        source: String(l.source),
        target: String(l.target),
        relation: l.relation,
        lineStyle: { color: c, width: strong ? 2.4 : 1.6, opacity: strong ? 0.95 : 0.6, curveness: 0.08 },
        emphasis: { lineStyle: { width: 4, opacity: 1 } },
        // 关系名直接标在线上（边不多时才显示，避免糊）
        label: {
          show: linksLabelOn && strong,
          formatter: l.relation,
          fontSize: 10,
          color: c,
          textBorderColor: '#0B0D0C',
          textBorderWidth: 3
        }
      }
    })

  const nodes = rawNodes.map(n => {
    const matched = matchedIds.has(String(n.id))
    const freq = n.docFreq || 1
    return {
      id: String(n.id),
      name: n.name,
      type: n.type,
      docFreq: freq,
      symbolSize: matched ? 34 : Math.min(40, 16 + Math.sqrt(freq) * 1.6),
      // 命中的实体：白描边 + 更大；其余弱化，避免全部抢眼
      itemStyle: {
        color: TYPE_COLOR[n.type] || TYPE_COLOR.UNKNOWN,
        borderColor: matched ? '#F5F3EE' : 'rgba(11,13,12,0.85)',
        borderWidth: matched ? 2.5 : 1,
        opacity: matched ? 1 : 0.82
      },
      // 标签：深色描边保证在任意背景上都能看清；小节点默认隐藏，hover 才显示
      label: {
        show: matched || freq >= 40,
        color: '#FFFFFF',
        fontSize: matched ? 14 : 12,
        fontWeight: matched ? 600 : 400,
        textBorderColor: '#0B0D0C',
        textBorderWidth: 3,
        position: 'right',
        distance: 6
      },
      emphasis: {
        label: { show: true, fontSize: 13, fontWeight: 600 },
        itemStyle: { borderColor: '#F5F3EE', borderWidth: 2.5 }
      }
    }
  })

  // 图例颜色必须和节点一致，否则图例是 ECharts 默认色，误导
  const categories = [...new Set(nodes.map(n => n.type))].map(t => ({
    name: t,
    itemStyle: { color: TYPE_COLOR[t] || TYPE_COLOR.UNKNOWN }
  }))

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(18,23,21,0.96)',
      borderColor: 'rgba(233,230,223,0.18)',
      textStyle: { color: '#F2F0EA', fontSize: 12 },
      formatter: p => {
        if (p.dataType === 'edge') {
          return `关系：<b>${p.data.relation || '-'}</b>`
        }
        const d = p.data || {}
        return `<b>${d.name}</b><br/>类型：${d.type}<br/>出现：${d.docFreq || 0} 个知识条目`
      }
    },
    legend: [{
      data: categories.map(c => c.name),
      bottom: 4,
      textStyle: { color: '#B9BDB6', fontSize: 11 },
      itemWidth: 10,
      itemHeight: 10
    }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      // 关键：关闭布局动画，否则拖动时力导向每帧重排，表现为"抽动"
      force: {
        repulsion: 320,
        edgeLength: [90, 150],
        gravity: 0.15,
        layoutAnimation: false,
        friction: 0.2
      },
      labelLayout: { hideOverlap: true },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 7,
      categories,
      data: nodes,
      links,
      lineStyle: { width: 2, curveness: 0.08 },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3, color: '#7FB3A6', opacity: 1 },
        label: { show: true }
      },
      blur: { itemStyle: { opacity: 0.35 }, lineStyle: { opacity: 0.15 } }
    }]
  }, true)
}

let resizeObs = null
let resizeTimer = null
onMounted(() => {
  loadGraph()
  // 防抖：滚动时容器尺寸变化会高频触发 resize，导致画面抖动
  resizeObs = new ResizeObserver(() => {
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => { if (chart) chart.resize() }, 180)
  })
  nextTick(() => { if (chartRef.value) resizeObs.observe(chartRef.value) })
})
onUnmounted(() => {
  if (resizeTimer) clearTimeout(resizeTimer)
  if (resizeObs) resizeObs.disconnect()
  if (chart) { chart.dispose(); chart = null }
})
watch(showRaw, () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => { if (chart) chart.resize() }, 220)
})

// 视图切换：离开关系图时销毁实例，切回来时重建（容器是 v-if，DOM 会被移除）
watch(view, async (v) => {
  if (v === 'graph') {
    await nextTick()
    chart = null
    render()
  } else if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.tip { color: var(--text-tertiary); font-size: 0.6875rem; }
.err-msg { margin: 10px 0; padding: 8px 12px; border: 1px solid var(--danger-border); background: var(--danger-bg); color: var(--danger); border-radius: var(--radius-sm); font-size: 0.75rem; }
.view-switch { display: inline-flex; border: 1px solid var(--border-secondary); border-radius: var(--radius-sm); overflow: hidden; }
.view-switch button { padding: 6px 12px; font-size: 0.75rem; color: var(--text-tertiary); }
.view-switch button.on { background: var(--bg-active); color: var(--text-primary); }
.rel-legend { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; margin: 8px 0 6px; font-size: 0.6875rem; color: var(--text-secondary); }
.rl-label { color: var(--text-tertiary); }
.rl-item { display: inline-flex; align-items: center; gap: 5px; }
.rl-dot { width: 14px; height: 3px; border-radius: 2px; display: inline-block; }
.rl-count { color: var(--text-tertiary); font-family: var(--font-mono); font-size: 0.625rem; }
.rel-table-wrap { border: 1px solid var(--rule); border-radius: var(--radius-sm); overflow: auto; max-height: 520px; }
.rel-table { width: 100%; border-collapse: collapse; font-size: 0.75rem; }
.rel-table th { position: sticky; top: 0; background: var(--bg-sidebar); text-align: left; padding: 8px 10px; color: var(--text-tertiary); font-weight: 500; border-bottom: 1px solid var(--rule); }
.rel-table td { padding: 6px 10px; border-bottom: 1px solid var(--rule); color: var(--text-secondary); }
.rel-table td.ent { color: var(--text-primary); }
.rel-chip { padding: 1px 8px; border: 1px solid; border-radius: var(--radius-full); font-family: var(--font-mono); font-size: 0.625rem; }
.q-input { flex: 1; min-width: 240px; padding: 7px 10px; border: 1px solid var(--border-input); border-radius: var(--radius-sm); background: var(--bg-input); color: var(--text-primary); font-size: 0.8125rem; }
.chk { display: inline-flex; align-items: center; gap: 5px; font-size: 0.75rem; color: var(--text-tertiary); }
.kg-meta { display: flex; flex-wrap: wrap; gap: 14px; margin: 10px 0 6px; font-size: 0.75rem; color: var(--text-tertiary); align-items: baseline; }
.kg-meta b { color: var(--accent); font-family: var(--font-mono); }
.kg-chart { width: 100%; height: 520px; border: 1px solid var(--rule); border-radius: var(--radius-sm); background: var(--bg-app); }
.raw-box { margin-top: 12px; border: 1px solid var(--rule); border-radius: var(--radius-sm); overflow: hidden; }
.raw-title { padding: 7px 10px; font-size: 0.6875rem; color: var(--text-tertiary); background: var(--bg-step); border-bottom: 1px solid var(--rule); }
.raw-json { margin: 0; padding: 10px; max-height: 260px; overflow: auto; font-family: var(--font-mono); font-size: 0.6875rem; line-height: 1.6; color: var(--text-secondary); }
</style>
