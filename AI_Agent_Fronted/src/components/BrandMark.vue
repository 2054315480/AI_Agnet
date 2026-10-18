<template>
  <span
    class="brand-mark"
    :class="{ solid, light: tone === 'light' }"
    :style="{ width: size + 'px', height: size + 'px', borderRadius: radius + 'px' }"
    aria-hidden="true"
  >
    <svg
      :width="inner"
      :height="inner"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.7"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v7A2.5 2.5 0 0 1 17.5 16H10l-4.2 3.2A.6.6 0 0 1 5 18.7V16"/>
      <path d="M8 8.5h8M8 11.5h5"/>
    </svg>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 外框尺寸（px） */
  size: { type: Number, default: 30 },
  /** 实心墨底：用于深色背景或需要独立成块的位置 */
  solid: { type: Boolean, default: false },
  /** light：固定浅色描边，用于始终深色的面板 */
  tone: { type: String, default: 'default' }
})

const inner = computed(() => Math.round(props.size * 0.56))
const radius = computed(() => Math.round(props.size * 0.27))
</script>

<style scoped>
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid var(--border-primary);
  border-radius: 8px;
  color: var(--accent);
  background: var(--bg-step);
  box-sizing: border-box;
}

/* 实心墨底：和浏览器图标 / 深色面板一致 */
.brand-mark.solid {
  background: #121715;
  border-color: rgba(233, 230, 223, 0.16);
  color: #7FB3A6;
}

.brand-mark.light {
  border-color: rgba(233, 230, 223, 0.18);
  background: rgba(233, 230, 223, 0.04);
  color: #8FC0B3;
}
</style>
