<template>
  <div class="theme-toggle" role="group" aria-label="主题模式">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="theme-option"
      :class="{ active: mode === option.value }"
      :aria-pressed="mode === option.value"
      :title="option.title"
      @click="setMode(option.value)"
    >
      <!-- 显示器：跟随系统 -->
      <svg
        v-if="option.value === 'system'"
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <rect x="3" y="4" width="18" height="13" rx="2" />
        <line x1="8" y1="21" x2="16" y2="21" />
        <line x1="12" y1="17" x2="12" y2="21" />
      </svg>

      <!-- 太阳：浅色 -->
      <svg
        v-else-if="option.value === 'light'"
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="4" />
        <line x1="12" y1="2" x2="12" y2="4" />
        <line x1="12" y1="20" x2="12" y2="22" />
        <line x1="4.93" y1="4.93" x2="6.34" y2="6.34" />
        <line x1="17.66" y1="17.66" x2="19.07" y2="19.07" />
        <line x1="2" y1="12" x2="4" y2="12" />
        <line x1="20" y1="12" x2="22" y2="12" />
        <line x1="4.93" y1="19.07" x2="6.34" y2="17.66" />
        <line x1="17.66" y1="6.34" x2="19.07" y2="4.93" />
      </svg>

      <!-- 月亮：深色 -->
      <svg
        v-else
        width="14"
        height="14"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
      </svg>

      <span class="theme-option-label">{{ option.label }}</span>
    </button>
  </div>
</template>

<script setup>
import { useTheme } from '../composables/useTheme.js'

const { mode, setMode } = useTheme()

const options = [
  { value: 'system', label: '跟随系统', title: '跟随系统外观' },
  { value: 'light', label: '浅色', title: '使用浅色主题' },
  { value: 'dark', label: '深色', title: '使用深色主题' }
]
</script>

<style scoped>
.theme-toggle {
  display: flex;
  align-items: center;
  gap: 2px;
  width: 100%;
  max-width: 236px;
  height: 30px;
  padding: 2px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  background: transparent;
}

.theme-option {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  height: 24px;
  padding: 0 3px;
  border-radius: calc(var(--radius-sm) - 1px);
  color: var(--text-tertiary);
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.theme-option:hover {
  background: var(--bg-hover);
  color: var(--text-secondary);
}

.theme-option.active {
  background: var(--bg-active);
  color: var(--text-primary);
}

.theme-option svg {
  flex-shrink: 0;
}

.theme-option-label {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>