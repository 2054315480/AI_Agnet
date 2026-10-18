<template>
  <div class="chat-input-wrap">
    <!-- 图片预览区域 -->
    <div v-if="imagePreview" class="image-preview-area">
      <div class="image-preview-item">
        <img :src="imagePreview" alt="preview" />
        <button class="remove-image" @click="clearImage">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>
    <div class="input-container">
      <!-- 图片上传按钮 -->
      <button class="attach-btn" aria-label="上传图片" @click="triggerFileInput" :disabled="disabled" title="上传图片">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <circle cx="8.5" cy="8.5" r="1.5"/>
          <polyline points="21 15 16 10 5 21"/>
        </svg>
      </button>
      <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/bmp"
             @change="handleImageSelect" style="display:none" />
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
        class="send-btn" aria-label="发送消息"
        :class="{ active: (text.trim() || imageFile) && !disabled }"
        :disabled="disabled || (!text.trim() && !imageFile)"
        @click="handleSend"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13"/>
          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
        </svg>
      </button>
    </div>

    <!-- 键盘提示：只在可输入时出现，弱化存在感 -->
    <div v-if="!disabled" class="input-hint" aria-hidden="true">
      <span class="hint-key">Enter</span><span>发送</span>
      <span class="hint-dot">·</span>
      <span class="hint-key">Shift</span><span>+</span><span class="hint-key">Enter</span><span>换行</span>
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
const fileInput = ref(null)
const imageFile = ref(null)
const imagePreview = ref(null)
const imageBase64 = ref(null)

function triggerFileInput() {
  fileInput.value?.click()
}

function handleImageSelect(event) {
  const file = event.target.files[0]
  if (file && file.type.startsWith('image/')) {
    imageFile.value = file
    imagePreview.value = URL.createObjectURL(file)
    // Read as base64 for persistent message display
    const reader = new FileReader()
    reader.onload = () => { imageBase64.value = reader.result }
    reader.readAsDataURL(file)
  }
}

function clearImage() {
  if (imagePreview.value) {
    URL.revokeObjectURL(imagePreview.value)
  }
  imageFile.value = null
  imagePreview.value = null
  imageBase64.value = null
  if (fileInput.value) fileInput.value.value = ''
}

function handleSend(e) {
  if (e) e.preventDefault()
  const msg = text.value.trim()
  if ((!msg && !imageFile.value) || props.disabled) return
  const previewUrl = imageBase64.value || imagePreview.value
  emit('send', msg, imageFile.value, previewUrl)
  text.value = ''
  clearImage()
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

.image-preview-area {
  padding: 0 4px 8px 4px;
}

.image-preview-item {
  position: relative;
  display: inline-block;
}

.image-preview-item img {
  max-width: 120px;
  max-height: 80px;
  border-radius: 8px;
  border: 1px solid var(--border-input);
  object-fit: cover;
}

.remove-image {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
}

.remove-image svg {
  width: 12px;
  height: 12px;
}

.input-container {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--bg-input);
  border: 1.5px solid var(--border-input);
  border-radius: 24px;
  padding: 6px 6px 6px 6px;
  transition: border-color 0.2s, background 0.2s;
}

.input-container:focus-within {
  border-color: var(--border-input-focus);
  background: var(--bg-input-focus);
}

.attach-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--text-tertiary);
  border: none;
  cursor: pointer;
  transition: var(--transition);
}

.attach-btn:hover:not(:disabled) {
  color: var(--accent-primary);
  background: var(--bg-hover);
}

.attach-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.attach-btn svg {
  width: 20px;
  height: 20px;
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
    padding: 5px 5px 5px 4px;
  }
}

/* ================================================================
   v6 覆写：输入区像一张便签纸
   ================================================================ */
.chat-input-wrap {
  width: 100%;
  max-width: var(--chat-max-width);
  margin: 0 auto;
  padding: 0 20px 14px;
}

.input-container {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 8px 8px 8px 10px;
  border: 1px solid var(--border-input);
  border-radius: var(--radius-lg);
  background: var(--bg-input);
  transition: border-color var(--duration-fast) var(--ease-out),
              background var(--duration-fast) var(--ease-out),
              box-shadow var(--duration-fast) var(--ease-out);
}

.input-container:focus-within {
  border-color: var(--accent-border);
  background: var(--bg-input-focus);
  box-shadow: inset 0 0 0 1px var(--accent-bg);
}

.input-container textarea {
  flex: 1;
  min-height: 24px;
  max-height: 168px;
  padding: 6px 4px;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 0.9rem;
  line-height: 1.65;
  resize: none;
}

.input-container textarea::placeholder { color: var(--text-tertiary); }

/* 焦点态由外层容器统一表达，避免 textarea 内部再套一圈描边 */
.input-container textarea:focus,
.input-container textarea:focus-visible { outline: none; }

.attach-btn {
  width: 30px; height: 30px;
  border-radius: var(--radius-xs);
  color: var(--text-tertiary);
  transition: color var(--duration-fast) var(--ease-out),
              background var(--duration-fast) var(--ease-out);
}

.attach-btn:hover { color: var(--text-secondary); background: var(--bg-hover); }
.attach-btn svg { width: 17px; height: 17px; }

.send-btn {
  width: 32px; height: 32px;
  border-radius: var(--radius-xs);
  color: var(--text-tertiary);
  background: transparent;
  border: 1px solid transparent;
  transition: all var(--duration-fast) var(--ease-out);
}

.send-btn.active {
  color: var(--text-inverse);
  background: var(--accent);
  border-color: var(--accent);
}

.send-btn.active:hover { background: var(--accent-hover); border-color: var(--accent-hover); }
.send-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.send-btn svg { width: 16px; height: 16px; }

.image-preview-area { padding-bottom: 8px; }

.image-preview-item {
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  background: var(--bg-step);
}

/* 键盘提示 */
.input-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 7px;
  font-size: 0.6875rem;
  color: var(--text-tertiary);
  user-select: none;
}

.hint-key {
  display: inline-block;
  padding: 0 4px;
  border: 1px solid var(--border-secondary);
  border-bottom-width: 2px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 0.625rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

.hint-dot { opacity: 0.5; }

@media (max-width: 768px) {
  .input-hint { display: none; }
  .chat-input-wrap { padding: 0 12px 10px; }
}
</style>
