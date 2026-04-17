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
      <button class="attach-btn" @click="triggerFileInput" :disabled="disabled" title="上传图片">
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
        class="send-btn"
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
</style>
