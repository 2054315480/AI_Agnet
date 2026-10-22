<template>
  <div class="trace-page">
    <header class="tp-head">
      <button class="tp-back" @click="goBack">← 返回</button>
      <div class="tp-title">
        <span class="tp-kicker">TRACE</span>
        <h1>链路详情</h1>
      </div>
      <button class="tp-copy" @click="copyLink">{{ copied ? '已复制' : '复制链接' }}</button>
    </header>

    <TraceWaterfall :trace="detail" :loading="loading" :error="error || false" @retry="load" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get } from '../api/http.js'
import TraceWaterfall from './TraceWaterfall.vue'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const loading = ref(false)
const error = ref('')
const copied = ref(false)

async function load() {
  const id = route.params.traceId
  if (!id) return
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    detail.value = await get('/admin/trace/' + encodeURIComponent(id))
  } catch (e) {
    error.value = '[' + (e?.status ?? 'ERR') + '] ' + (e?.message || String(e))
    console.error('[TracePage] 加载失败', e, 'id=', route.params.traceId)
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/console')
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    copied.value = true
    setTimeout(() => { copied.value = false }, 1500)
  } catch (e) {
    copied.value = false
  }
}

onMounted(load)
watch(() => route.params.traceId, load)
</script>

<style scoped>
.trace-page {
  height: 100dvh;
  overflow-y: auto;
  padding: 22px 28px 48px;
  background: var(--bg-app);
}

.tp-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--rule);
}

.tp-kicker {
  font-size: 0.5625rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-tertiary);
}

.tp-title h1 {
  font-family: var(--font-serif);
  font-size: 1.25rem;
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}

.tp-back,
.tp-copy {
  padding: 7px 12px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.8125rem;
  transition: all var(--duration-fast) var(--ease-out);
}

.tp-back:hover,
.tp-copy:hover {
  border-color: var(--accent-border);
  color: var(--text-primary);
  background: var(--bg-hover);
}

.tp-copy { margin-left: auto; }
</style>
