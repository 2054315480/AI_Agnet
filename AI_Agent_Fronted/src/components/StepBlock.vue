<template>
  <div class="step-block" :class="{ expanded: isOpen }">
    <button class="step-header" @click="isOpen = !isOpen">
      <svg
        class="step-chevron"
        width="14" height="14" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
      >
        <polyline points="6 9 12 15 18 9"/>
      </svg>
      <span class="step-label">{{ label }}</span>
    </button>
    <div class="step-body" v-show="isOpen">
      <div class="step-content" v-html="renderedContent"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  label: { type: String, default: '' },
  content: { type: String, default: '' },
  defaultOpen: { type: Boolean, default: false }
})

const isOpen = ref(props.defaultOpen)

const renderedContent = computed(() => {
  if (!props.content) return ''
  return marked.parse(props.content, { breaks: true, gfm: true })
})
</script>

<style scoped>
.step-block {
  border-left: 3px solid var(--accent-manus-border);
  border-radius: 0 var(--radius-xs) var(--radius-xs) 0;
  background: var(--bg-step);
  overflow: hidden;
  margin-top: 6px;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  width: 100%;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--accent-manus);
  transition: var(--transition);
}

.step-header:hover {
  background: var(--bg-hover);
}

.step-chevron {
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.step-block.expanded .step-chevron {
  transform: rotate(180deg);
}

.step-label {
  white-space: nowrap;
}

.step-body {
  padding: 0 12px 10px;
}

.step-content {
  font-size: 0.875rem;
  color: var(--text-ai-bubble);
  line-height: 1.6;
}

/* Markdown styles inside step */
.step-content :deep(p) {
  margin-bottom: 6px;
}

.step-content :deep(p:last-child) {
  margin-bottom: 0;
}

.step-content :deep(code) {
  background: var(--bg-code);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 0.8125rem;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
}

.step-content :deep(pre) {
  background: var(--bg-code);
  padding: 12px;
  border-radius: var(--radius-xs);
  overflow-x: auto;
  margin: 6px 0;
}

.step-content :deep(pre code) {
  background: none;
  padding: 0;
}
</style>
