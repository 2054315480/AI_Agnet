<template>
  <div class="admin-faq-page">
    <div class="admin-header">
      <button class="back-btn" @click="$router.push('/')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
        <span>返回对话</span>
      </button>
      <h1>FAQ 管理</h1>
    </div>

    <!-- Upload area -->
    <div class="upload-section">
      <div
        class="upload-zone"
        :class="{ 'drag-over': isDragOver }"
        @dragover.prevent="isDragOver = true"
        @dragleave="isDragOver = false"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="17 8 12 3 7 8"/>
          <line x1="12" y1="3" x2="12" y2="15"/>
        </svg>
        <p class="upload-text">拖拽 Markdown 文件到此处，或点击上传</p>
        <p class="upload-hint">支持 .md 格式，FAQ 文件使用 ## 标题分隔问答</p>
        <input
          ref="fileInput"
          type="file"
          accept=".md"
          style="display:none"
          @change="handleFileSelect"
        />
      </div>
    </div>

    <!-- Status messages -->
    <div v-if="uploadStatus" class="status-msg" :class="uploadStatus.type">
      {{ uploadStatus.message }}
    </div>

    <!-- File list -->
    <div class="file-list-section">
      <div class="section-header">
        <h2>FAQ 文件列表</h2>
        <button class="reload-btn" @click="handleReload" :disabled="reloading">
          <svg class="spin-icon" :class="{ spinning: reloading }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="23 4 23 10 17 10"/>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
          <span>{{ reloading ? '重载中...' : '重载知识库' }}</span>
        </button>
      </div>

      <div v-if="loading" class="loading-state">加载中...</div>
      <div v-else-if="faqFiles.length === 0" class="empty-state">暂无 FAQ 文件</div>
      <div v-else class="file-list">
        <div v-for="file in faqFiles" :key="file.filename" class="file-item">
          <div class="file-info">
            <div class="file-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
                <polyline points="10 9 9 9 8 9"/>
              </svg>
            </div>
            <div class="file-details">
              <span class="file-name">{{ file.filename }}</span>
              <span class="file-meta">{{ file.entryCount }} 条问答 &middot; {{ formatSize(file.size) }}</span>
            </div>
          </div>
          <button class="delete-btn" @click="handleDelete(file.filename)" :disabled="deleting === file.filename">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { uploadFaqFile, getFaqList, deleteFaqFile, reloadKnowledge } from '../api/admin.js'

const faqFiles = ref([])
const loading = ref(false)
const isDragOver = ref(false)
const uploadStatus = ref(null)
const reloading = ref(false)
const deleting = ref(null)
const fileInput = ref(null)

onMounted(() => {
  loadFaqList()
})

async function loadFaqList() {
  loading.value = true
  try {
    faqFiles.value = await getFaqList()
  } catch (e) {
    showStatus('error', '加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

function triggerFileInput() {
  fileInput.value?.click()
}

async function handleFileSelect(e) {
  const file = e.target.files[0]
  if (file) await upload(file)
  e.target.value = ''
}

async function handleDrop(e) {
  isDragOver.value = false
  const file = e.dataTransfer.files[0]
  if (file) await upload(file)
}

async function upload(file) {
  if (!file.name.endsWith('.md')) {
    showStatus('error', '仅支持 .md 格式文件')
    return
  }
  try {
    const result = await uploadFaqFile(file)
    if (result.error) {
      showStatus('error', result.error)
    } else {
      showStatus('success', `上传成功: ${result.filename} (${result.entryCount} 条问答)`)
      await loadFaqList()
    }
  } catch (e) {
    showStatus('error', '上传失败: ' + e.message)
  }
}

async function handleDelete(filename) {
  if (!confirm(`确定删除 ${filename}？`)) return
  deleting.value = filename
  try {
    await deleteFaqFile(filename)
    showStatus('success', `已删除: ${filename}`)
    await loadFaqList()
  } catch (e) {
    showStatus('error', '删除失败: ' + e.message)
  } finally {
    deleting.value = null
  }
}

async function handleReload() {
  reloading.value = true
  try {
    const result = await reloadKnowledge()
    showStatus('success', '知识库已重新加载')
  } catch (e) {
    showStatus('error', '重载失败: ' + e.message)
  } finally {
    reloading.value = false
  }
}

function showStatus(type, message) {
  uploadStatus.value = { type, message }
  setTimeout(() => { uploadStatus.value = null }, 5000)
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}
</script>

<style scoped>
.admin-faq-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 24px;
  min-height: 100vh;
  background: var(--bg-chat);
}

.admin-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.admin-header h1 {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  color: var(--text-secondary);
  transition: var(--transition);
}

.back-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

/* Upload */
.upload-section {
  margin-bottom: 24px;
}

.upload-zone {
  border: 2px dashed var(--border-primary);
  border-radius: var(--radius-md);
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: var(--transition);
  color: var(--text-secondary);
}

.upload-zone:hover,
.upload-zone.drag-over {
  border-color: var(--accent-primary);
  background: rgba(31, 111, 235, 0.05);
}

.upload-text {
  font-size: 0.9375rem;
  margin-top: 12px;
  font-weight: 500;
}

.upload-hint {
  font-size: 0.8125rem;
  color: var(--text-tertiary);
  margin-top: 4px;
}

/* Status */
.status-msg {
  padding: 10px 16px;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  margin-bottom: 16px;
}

.status-msg.success {
  background: rgba(46, 160, 67, 0.1);
  color: #2ea043;
  border: 1px solid rgba(46, 160, 67, 0.2);
}

.status-msg.error {
  background: rgba(248, 81, 73, 0.1);
  color: #f85149;
  border: 1px solid rgba(248, 81, 73, 0.2);
}

/* File list */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-header h2 {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
}

.reload-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--accent-primary);
  border: 1px solid var(--accent-primary);
  transition: var(--transition);
}

.reload-btn:hover:not(:disabled) {
  background: rgba(31, 111, 235, 0.1);
}

.reload-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.spin-icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-secondary);
  background: var(--bg-sidebar);
  transition: var(--transition);
}

.file-item:hover {
  border-color: var(--border-primary);
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-icon {
  color: var(--text-tertiary);
}

.file-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.file-name {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
}

.file-meta {
  font-size: 0.75rem;
  color: var(--text-tertiary);
}

.delete-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  transition: var(--transition);
}

.delete-btn:hover:not(:disabled) {
  color: var(--danger);
  background: var(--danger-bg);
}

.delete-btn:disabled {
  opacity: 0.4;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 32px;
  color: var(--text-tertiary);
  font-size: 0.875rem;
}
</style>
