<template>
  <div class="impact-page">
    <header class="ip-head">
      <button class="ip-back" @click="$router.push('/console')">← 能力控制台</button>
      <div class="ip-title">
        <span class="ip-kicker">IMPACT</span>
        <h1>优化成效</h1>
      </div>
      <span class="ip-time" v-if="generatedAt">更新于 {{ generatedAt }}</span>
    </header>

    <StateBlock :loading="loading" :error="error" :empty="!loading && !error && !items.length"
                empty-text="暂无成效数据" @retry="load">
      <!-- 概览 -->
      <section class="ip-summary">
        <div class="sum-item" v-for="s in summaryCards" :key="s.label">
          <span class="sum-k">{{ s.label }}</span>
          <strong class="sum-v tnum">{{ s.value }}</strong>
        </div>
      </section>

      <!-- 成效卡片 -->
      <section class="ip-grid">
        <article v-for="it in items" :key="it.id" class="impact-card" :class="{ improved: it.improved }">
          <header class="ic-head">
            <span class="ic-cat">{{ it.category }}</span>
            <span class="ic-badge" :class="it.improved ? 'ok' : 'flat'">
              {{ it.improved ? '已优化' : '持平' }}
            </span>
          </header>

          <h3 class="ic-title">{{ it.title }}</h3>
          <p class="ic-tech">{{ it.tech }}</p>

          <div v-if="it.available === false" class="ic-unavailable">
            尚未运行实验 —— 去
            <a href="#" @click.prevent="$router.push('/experiments')">实验中心</a>
            点一次运行，这里会显示真实实测值
          </div>
          <div v-else class="ic-compare">
            <div class="cmp-row">
              <span class="cmp-k">优化前</span>
              <div class="cmp-bar"><i class="bar before" :style="{ width: barWidth(it.before) }"></i></div>
              <span class="cmp-v tnum">{{ fmt(it.before) }}{{ it.unit ? ' ' + it.unit : '' }}</span>
            </div>
            <div class="cmp-row">
              <span class="cmp-k">优化后</span>
              <div class="cmp-bar"><i class="bar after" :style="{ width: barWidth(it.after) }"></i></div>
              <span class="cmp-v tnum strong">{{ fmt(it.after) }}{{ it.unit ? ' ' + it.unit : '' }}</span>
            </div>
          </div>

          <footer v-if="it.available !== false" class="ic-foot">
            <span class="ic-delta" :class="it.improved ? 'up' : 'flat'">
              {{ deltaText(it) }}
            </span>
            <span class="ic-src" :title="it.note">{{ it.source === 'live' ? '实时' : '实测' }}</span>
          </footer>
          <p class="ic-note">{{ it.note }}</p>
        </article>
      </section>
    </StateBlock>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { get } from '../api/http.js'
import StateBlock from './StateBlock.vue'

const loading = ref(false)
const error = ref('')
const items = ref([])
const generatedAt = ref('')

const summaryCards = computed(() => {
  const s = items.value.find(i => i.id === 'corpus')
  const h = items.value.find(i => i.id === 'hops')
  return [
    { label: '成效项', value: items.value.length },
    { label: '知识库块数', value: s ? fmt(s.after) : '—' },
    { label: '链路跳数', value: h ? h.after : 9 },
    { label: '已优化', value: items.value.filter(i => i.improved).length }
  ]
})

function fmt(v) {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  if (!isFinite(n)) return String(v)
  if (Math.abs(n) >= 1000) return n.toLocaleString('zh-CN')
  return (Math.round(n * 100) / 100).toString()
}

function barWidth(v) {
  const nums = items.value.flatMap(i => [Number(i.before) || 0, Number(i.after) || 0])
  const max = Math.max(...nums, 1)
  return Math.max(4, Math.round((Number(v) || 0) / max * 100)) + '%'
}

function deltaText(it) {
  const before = Number(it.before) || 0
  const after = Number(it.after) || 0
  // 从 0 起步的项（如"从无到有"）用百分比没有意义，直接说新增多少
  if (before === 0 && after > 0) return '新增 ' + fmt(after) + (it.unit || '')
  if (before === 0 && after === 0) return '—'
  const pct = Number(it.deltaPct)
  const sign = pct > 0 ? '+' : ''
  const dir = it.higherIsBetter ? '↑' : '↓'
  return (it.improved ? dir + ' ' : '') + sign + fmt(pct) + '%'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await get('/admin/impact')
    items.value = data?.items || []
    generatedAt.value = data?.generatedAt ? new Date(data.generatedAt).toLocaleString('zh-CN', { hour12: false }) : ''
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.impact-page {
  height: 100dvh;
  overflow-y: auto;
  padding: 22px 28px 56px;
  background: var(--bg-app);
}

.ip-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--rule);
}

.ip-kicker {
  font-size: 0.5625rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-tertiary);
}

.ip-title h1 {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.ip-back {
  padding: 7px 12px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.8125rem;
}

.ip-back:hover { border-color: var(--accent-border); color: var(--text-primary); }
.ip-time { margin-left: auto; font-size: 0.6875rem; color: var(--text-tertiary); }

.ip-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.sum-item {
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius);
  padding: 12px 14px;
  background: var(--bg-sidebar);
}

.sum-k { display: block; font-size: 0.6875rem; color: var(--text-tertiary); margin-bottom: 4px; }
.sum-v { font-family: var(--font-serif); font-size: 1.5rem; color: var(--text-primary); }

.ip-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
}

.impact-card {
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius);
  padding: 14px 16px 12px;
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.impact-card.improved { border-color: var(--accent-border); }

.ic-head { display: flex; align-items: center; gap: 8px; }
.ic-cat {
  font-size: 0.625rem; letter-spacing: 0.08em; text-transform: uppercase;
  color: var(--text-tertiary);
}
.ic-badge {
  margin-left: auto; font-size: 0.625rem; padding: 1px 7px; border-radius: 999px;
  border: 1px solid var(--border-secondary); color: var(--text-tertiary);
}
.ic-badge.ok { border-color: var(--accent-border); color: var(--accent); }

.ic-title { font-family: var(--font-serif); font-size: 1rem; color: var(--text-primary); }
.ic-tech { font-size: 0.75rem; line-height: 1.6; color: var(--text-tertiary); }

.ic-compare { display: flex; flex-direction: column; gap: 6px; margin-top: 2px; }
.cmp-row { display: flex; align-items: center; gap: 8px; }
.cmp-k { width: 44px; font-size: 0.6875rem; color: var(--text-tertiary); }
.cmp-bar {
  flex: 1; height: 8px; border-radius: 999px; background: var(--bg-hover); overflow: hidden;
}
.bar { display: block; height: 100%; border-radius: 999px; }
.bar.before { background: var(--text-tertiary); opacity: 0.55; }
.bar.after { background: var(--accent); }
.cmp-v { width: 84px; text-align: right; font-size: 0.75rem; color: var(--text-secondary); }
.cmp-v.strong { color: var(--text-primary); font-weight: var(--weight-semibold); }

.ic-foot { display: flex; align-items: center; gap: 8px; margin-top: 2px; }
.ic-delta { font-family: var(--font-mono); font-size: 0.8125rem; color: var(--text-tertiary); }
.ic-delta.up { color: var(--success); }
.ic-src {
  margin-left: auto; font-size: 0.625rem; padding: 1px 6px; border-radius: 3px;
  border: 1px solid var(--border-secondary); color: var(--text-tertiary);
}
.ic-note { font-size: 0.6875rem; line-height: 1.6; color: var(--text-tertiary); }
.ic-unavailable {
  font-size: 0.75rem; color: var(--text-tertiary); padding: 10px 0;
  border-top: 1px dashed var(--rule); border-bottom: 1px dashed var(--rule);
}
.ic-unavailable a { color: var(--accent); text-decoration: underline; }

@media (max-width: 900px) {
  .ip-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .impact-page { padding: 16px 14px 40px; }
}
</style>
