<template>
  <div v-if="loading" class="state-block state-loading" role="status" aria-live="polite">
    <span class="state-spinner" aria-hidden="true"></span>
    <p class="state-text">{{ loadingText }}</p>
    <slot name="loading" />
  </div>

  <div v-else-if="error" class="state-block state-error" role="alert">
    <p class="state-text state-error-text">{{ errorText }}</p>
    <slot name="actions">
      <button v-if="retryable" class="state-action" type="button" @click="$emit('retry')">重试</button>
    </slot>
  </div>

  <div v-else-if="empty" class="state-block state-empty">
    <p class="state-text">{{ emptyText }}</p>
    <slot name="empty" />
  </div>

  <slot v-else />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  loading: { type: Boolean, default: false },
  error: { type: [Boolean, String, Object], default: false },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' },
  loadingText: { type: String, default: '正在加载...' },
  retryable: { type: Boolean, default: true }
})

defineEmits(['retry'])

const errorText = computed(() => {
  if (props.error === true) return '数据加载失败，请稍后重试'
  if (typeof props.error === 'string') return props.error
  return props.error?.message || '数据加载失败，请稍后重试'
})
</script>

<style scoped>
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 120px;
  padding: 24px 16px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 0.8125rem;
}
.state-text {
  margin: 0;
  line-height: 1.7;
  color: var(--text-tertiary);
}
.state-error-text {
  color: var(--danger);
}
.state-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--rule);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: state-spin 800ms linear infinite;
}
.state-empty {
  min-height: 96px;
}
.state-action {
  padding: 5px 14px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  background: var(--bg-hover);
  font-size: 0.75rem;
}
.state-action:hover {
  color: var(--accent);
  border-color: var(--accent-border);
}
@keyframes state-spin {
  to { transform: rotate(360deg); }
}
</style>
