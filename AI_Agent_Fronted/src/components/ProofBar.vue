<template>
  <div class="proof-bar" v-if="info">
    <span class="pb-item">
      <span class="pb-k">构建</span>
      <span class="pb-v mono">{{ info.gitCommit }}</span>
    </span>
    <span class="pb-sep"></span>
    <span class="pb-item">
      <span class="pb-k">运行</span>
      <span class="pb-v">{{ info.uptimeHuman }}</span>
    </span>
    <span class="pb-sep"></span>
    <span class="pb-item">
      <span class="pb-k">累计请求</span>
      <span class="pb-v mono tnum">{{ info.counters.chatRequests }}</span>
    </span>
    <span class="pb-sep"></span>
    <span class="pb-item">
      <span class="pb-k">LLM 调用</span>
      <span class="pb-v mono tnum">{{ info.counters.llmCalls }}</span>
    </span>
    <span class="pb-sep"></span>
    <span class="pb-item">
      <span class="pb-k">会话 / 消息</span>
      <span class="pb-v mono tnum">{{ info.tables.conversations }} / {{ info.tables.messages }}</span>
    </span>
    <span class="pb-sep"></span>
    <span class="pb-item">
      <span class="pb-k">向量 / 文档</span>
      <span class="pb-v mono tnum">{{ info.tables.csVectorStore }}</span>
    </span>
    <span class="pb-live" :title="'数据每 10 秒刷新，来自真实运行的实例'">
      <span class="pb-dot"></span>LIVE
    </span>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { get } from '../api/http.js'

const info = ref(null)
let timer = null

async function load() {
  try {
    info.value = await get('/system/info')
  } catch (e) {
    // 静默失败，不影响主界面
  }
}

onMounted(() => {
  load()
  timer = setInterval(() => {
    if (document.visibilityState === 'visible') load()
  }, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.proof-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 30px;
  padding: 0 14px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--rule);
  background: var(--bg-step);
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  overflow-x: auto;
  white-space: nowrap;
}

.pb-item { display: inline-flex; align-items: baseline; gap: 5px; }
.pb-k { color: var(--text-tertiary); opacity: 0.8; }
.pb-v { color: var(--text-secondary); }
.mono { font-family: var(--font-mono); }
.tnum { font-variant-numeric: tabular-nums; }
.pb-sep { width: 1px; height: 10px; background: var(--rule); flex-shrink: 0; }
.pb-live {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 0.5625rem;
  letter-spacing: 0.1em;
}
.pb-dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: var(--accent);
  animation: blink 1.6s ease-in-out infinite;
}
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:.3} }
</style>
