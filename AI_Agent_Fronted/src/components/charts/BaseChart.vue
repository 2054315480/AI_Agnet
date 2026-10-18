<template>
  <div ref="chartEl" class="base-chart" :style="{ height }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { readChartTokens } from './chartTokens.js'

// 按需注册，避免把 ECharts 全量图表和组件打进产物
echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  CanvasRenderer
])

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '260px' }
})

const chartEl = ref(null)
let chart = null
let resizeObserver = null
let themeObserver = null
let resizeRaf = 0
let themeRaf = 0
let lastWidth = 0
let lastHeight = 0

/** 把 #RRGGBB 或 rgb/rgba 颜色转成指定透明度，供面积渐变使用 */
function withAlpha(color, alpha) {
  const value = String(color || '').trim()
  const hex = value.match(/^#([\da-f]{3}|[\da-f]{6})$/i)

  if (hex) {
    let raw = hex[1]
    if (raw.length === 3) raw = raw.split('').map(ch => ch + ch).join('')
    const r = parseInt(raw.slice(0, 2), 16)
    const g = parseInt(raw.slice(2, 4), 16)
    const b = parseInt(raw.slice(4, 6), 16)
    return `rgba(${r}, ${g}, ${b}, ${alpha})`
  }

  const rgb = value.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/i)
  if (rgb) return `rgba(${rgb[1]}, ${rgb[2]}, ${rgb[3]}, ${alpha})`

  return value
}

/** 解析 option 里的语义色标记（token:accent、token:accent:0.24） */
function resolveTokenColor(value, tokens) {
  const matched = value.match(/^token:([a-z-]+)(?::([\d.]+))?$/)
  if (!matched) return null

  const colorMap = {
    accent: tokens.palette[0],
    'accent-love': tokens.palette[1],
    success: tokens.palette[2],
    warning: tokens.palette[3],
    danger: tokens.palette[4],
    'text-tertiary': tokens.palette[5],
    text: tokens.text,
    'text-secondary': tokens.textSecondary,
    axisLine: tokens.axisLine,
    splitLine: tokens.splitLine,
    tooltipBg: tokens.tooltipBg,
    tooltipBorder: tokens.tooltipBorder
  }

  const color = colorMap[matched[1]]
  if (!color) return null
  return matched[2] ? withAlpha(color, Number(matched[2])) : color
}

/**
 * 深度解析颜色 token 和 CSS 变量。
 * option 中保留 var(--x) / token:x 这类语义引用，主题切换后重新解析即可换色。
 */
function resolveThemeValue(value, tokens, rootStyle) {
  if (Array.isArray(value)) {
    return value.map(item => resolveThemeValue(item, tokens, rootStyle))
  }

  if (value && typeof value === 'object') {
    const resolved = {}
    Object.keys(value).forEach(key => {
      resolved[key] = resolveThemeValue(value[key], tokens, rootStyle)
    })
    return resolved
  }

  if (typeof value !== 'string') return value

  const tokenColor = resolveTokenColor(value, tokens)
  if (tokenColor) return tokenColor

  if (value.includes('var(--')) {
    return value.replace(/var\((--[^),\s]+)(?:,\s*([^)]+))?\)/g, (_, name, fallback) => {
      const cssValue = rootStyle.getPropertyValue(name).trim()
      return cssValue || (fallback ? fallback.trim() : '')
    })
  }

  return value
}

/** 注入统一的文字、提示框和调色板，确保深浅主题下有一致兜底 */
function applyBaseTheme(option, tokens) {
  const themed = { ...option }
  themed.color = themed.color || tokens.palette
  themed.textStyle = {
    color: tokens.text,
    fontFamily: tokens.fontFamily,
    ...(themed.textStyle || {})
  }

  const mergeTooltip = tooltip => ({
    confine: true,
    backgroundColor: tokens.tooltipBg,
    borderColor: tokens.tooltipBorder,
    borderWidth: 1,
    ...tooltip,
    textStyle: {
      color: tokens.text,
      fontFamily: tokens.fontFamily,
      ...(tooltip.textStyle || {})
    }
  })

  // 常规图表使用单 tooltip；若业务传入数组也保留数组结构
  themed.tooltip = Array.isArray(themed.tooltip)
    ? themed.tooltip.map(mergeTooltip)
    : mergeTooltip(themed.tooltip || {})

  return themed
}

/** 读取最新 tokens，重新构建并应用 option（notMerge 防止旧主题颜色残留） */
function renderChart() {
  if (!chart) return
  const tokens = readChartTokens()
  const rootStyle = getComputedStyle(document.documentElement)
  const resolved = resolveThemeValue(props.option, tokens, rootStyle)
  chart.setOption(applyBaseTheme(resolved, tokens), { notMerge: true })
}

/** 主题属性变化后等一帧，确保新 CSS 变量已经生效 */
function handleThemeChange() {
  if (themeRaf) cancelAnimationFrame(themeRaf)
  themeRaf = requestAnimationFrame(() => {
    themeRaf = 0
    renderChart()
  })
}

/** ResizeObserver 只做轻量 resize，并用 rAF 合并连续触发 */
function handleResize(entries) {
  const rect = entries[0]?.contentRect
  const width = rect?.width || chartEl.value?.clientWidth || 0
  const height = rect?.height || chartEl.value?.clientHeight || 0
  if (!width) return
  if (Math.abs(width - lastWidth) < 1 && Math.abs(height - lastHeight) < 1) return

  lastWidth = width
  lastHeight = height
  if (resizeRaf) cancelAnimationFrame(resizeRaf)
  resizeRaf = requestAnimationFrame(() => {
    resizeRaf = 0
    chart?.resize()
  })
}

onMounted(() => {
  if (!chartEl.value) return
  chart = echarts.init(chartEl.value, null, { renderer: 'canvas' })
  renderChart()

  resizeObserver = new ResizeObserver(handleResize)
  resizeObserver.observe(chartEl.value)

  // 只监听 <html data-theme> 属性，避免无关 DOM 变更造成重复渲染
  themeObserver = new MutationObserver(handleThemeChange)
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  })
})

watch(
  () => props.option,
  () => renderChart(),
  { deep: true }
)

onBeforeUnmount(() => {
  if (resizeRaf) cancelAnimationFrame(resizeRaf)
  if (themeRaf) cancelAnimationFrame(themeRaf)
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  chart?.dispose()
  resizeObserver = null
  themeObserver = null
  chart = null
})
</script>

<style scoped>
.base-chart {
  width: 100%;
  overflow: hidden;
}
</style>
