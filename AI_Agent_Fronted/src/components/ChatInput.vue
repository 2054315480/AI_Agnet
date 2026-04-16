<template>
  <div class="chat-input-wrap">
    <div class="input-container">
      <textarea
        ref="inputRef"
        v-model="text"
        :placeholder="placeholder"
        :disabled="disabled"
        rows="1"
        @keydown.enter.exact="handleSend"
        @input="autoResize"
      />
      <button
        class="send-btn"
        :class="{ active: text.trim() && !disabled }"
        :disabled="disabled || !text.trim()"
        @click="handleSend"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13"/>
          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const props = defineProps({
  agent: { type: String, default: 'love' },
  placeholder: { type: String, default: '输入消息...' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['send'])
const text = ref('')
const inputRef = ref(null)

function handleSend(e) {
  if (e) e.preventDefault()
  const msg = text.value.trim()
  if (!msg || props.disabled) return
  emit('send', msg)
  text.value = ''
  nextTick(() => autoResize())
}

function autoResize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}

defineExpose({ focus: () => inputRef.value?.focus() })
</script>

<style scoped>
.chat-input-wrap {
  width: 100%;
}

.input-container {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--bg-input);
  border: 1.5px solid var(--border-input);
  border-radius: 24px;
  padding: 6px 6px 6px 18px;
  transition: border-color 0.2s, background 0.2s;
}

.input-container:focus-within {
  border-color: var(--border-input-focus);
  background: var(--bg-input-focus);
}

textarea {
  flex: 1;
  border: none;
  resize: none;
  font-size: 0.95rem;
  line-height: 1.5;
  padding: 8px 0;
  background: transparent;
  max-height: 200px;
  color: var(--text-primary);
}

textarea::placeholder {
  color: var(--text-tertiary);
}

textarea:disabled {
  opacity: 0.5;
}

.send-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--border-primary);
  color: var(--text-tertiary);
  transition: var(--transition);
}

.send-btn.active {
  background: var(--accent-primary);
  color: #ffffff;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
}

.send-btn:disabled {
  cursor: not-allowed;
}

.send-btn svg {
  width: 18px;
  height: 18px;
}

@media (max-width: 768px) {
  .input-container {
    padding: 5px 5px 5px 14px;
  }
}
</style>
