<template>
  <div v-if="visible" class="batch-test-overlay" @click.self="close">
    <div class="batch-test-modal">
      <div class="modal-header">
        <h3>RAG 知识库批量测试</h3>
        <button class="close-btn" aria-label="关闭" @click="close">&times;</button>
      </div>

      <!-- File upload area -->
      <div v-if="!results && !loading" class="upload-area">
        <div class="upload-hint">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="12" y1="18" x2="12" y2="12"/>
            <line x1="9" y1="15" x2="15" y2="15"/>
          </svg>
          <p>上传知识库文件进行检索测试</p>
          <p class="sub-hint">支持 .md / .docx / .pdf 格式，内容格式与知识库一致（## 类目 + ### N. 问题 + 答案）</p>
        </div>
        <input
          ref="fileInput"
          type="file"
          accept=".md,.docx,.pdf"
          @change="handleFileSelect"
          class="file-input"
        />
        <button class="upload-btn" @click="$refs.fileInput.click()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          选择文件
        </button>
        <p v-if="selectedFile" class="file-name">已选择: {{ selectedFile.name }}</p>
        <button v-if="selectedFile" class="start-btn" @click="startTest">
          开始批量测试
        </button>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="loading-area">
        <div class="spinner"></div>
        <p class="loading-text">正在批量检索 {{ totalQuestions }} 条问题...</p>
        <p class="loading-sub">已测试 {{ testedCount }} / {{ totalQuestions }}</p>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
      </div>

      <!-- Results -->
      <div v-if="results" class="results-area">
        <div class="results-summary">
          <div class="summary-item">
            <span class="summary-value">{{ results.totalQuestions }}</span>
            <span class="summary-label">总问题数</span>
          </div>
          <div class="summary-item success">
            <span class="summary-value">{{ results.matchedCount || 0 }}</span>
            <span class="summary-label">命中</span>
          </div>
          <div class="summary-item fail">
            <span class="summary-value">{{ results.totalQuestions - (results.matchedCount || 0) }}</span>
            <span class="summary-label">未命中</span>
          </div>
          <div class="summary-item">
            <span class="summary-value">{{ results.avgTopRelevance ? (results.avgTopRelevance * 100).toFixed(1) : 0 }}%</span>
            <span class="summary-label">平均相关度</span>
          </div>
          <div class="summary-item">
            <span class="summary-value">{{ results.avgElapsedMs ? results.avgElapsedMs.toFixed(0) : 0 }}ms</span>
            <span class="summary-label">平均耗时</span>
          </div>
        </div>

        <div class="results-list">
          <div
            v-for="(r, idx) in results.results"
            :key="idx"
            class="result-item"
            :class="{ matched: r.matched, unmatched: !r.matched }"
          >
            <div class="result-header">
              <span class="result-index">#{{ idx + 1 }}</span>
              <span class="result-badge" :class="r.matched ? 'badge-ok' : 'badge-fail'">
                {{ r.matched ? '命中' : '未命中' }}
              </span>
              <span class="result-relevance">相关度: {{ (r.topRelevance * 100).toFixed(1) }}%</span>
              <span class="result-time">{{ r.elapsedMs }}ms</span>
            </div>
            <div class="result-question">
              <strong>Q:</strong> {{ r.question }}
            </div>
            <div v-if="r.expectedKbId" class="result-kbid">
              预期条目: <code>{{ r.expectedKbId }}</code>
              <span v-if="r.matched" class="hit-tag">已命中</span>
            </div>
            <div v-if="r.searchHits && r.searchHits.length > 0" class="result-hits">
              <div class="hits-label">实际召回 Top{{ r.searchHits.length }}:</div>
              <div
                v-for="(hit, hIdx) in r.searchHits"
                :key="hIdx"
                class="hit-item"
                :class="{ 'hit-best': hIdx === 0 }"
              >
                <span class="hit-idx">#{{ hIdx + 1 }}</span>
                <span class="hit-kbid" v-if="hit.kbId"><code>{{ hit.kbId }}</code></span>
                <span class="hit-relevance">{{ (hit.relevance * 100).toFixed(1) }}%</span>
                <span class="hit-source" v-if="hit.source">{{ hit.source }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="results-actions">
          <button class="action-btn secondary" @click="reset">重新测试</button>
          <button class="action-btn download" @click="downloadResults">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            下载结果 CSV
          </button>
          <button class="action-btn" @click="close">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuth } from '../api/auth.js'

const visible = ref(false)
const loading = ref(false)
const selectedFile = ref(null)
const results = ref(null)
const totalQuestions = ref(0)
const testedCount = ref(0)
const fileInput = ref(null)

const { getHeaders } = useAuth()

const progressPercent = ref(0)

function open() {
  visible.value = true
  results.value = null
  selectedFile.value = null
  loading.value = false
}

function close() {
  visible.value = false
}

function reset() {
  results.value = null
  selectedFile.value = null
  loading.value = false
  totalQuestions.value = 0
  testedCount.value = 0
  progressPercent.value = 0
}

function handleFileSelect(e) {
  const file = e.target.files[0]
  if (file) {
    selectedFile.value = file
    results.value = null
  }
}

async function startTest() {
  if (!selectedFile.value) return
  loading.value = true
  results.value = null

  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('domain', 'test')

  try {
    const res = await fetch('/api/customer-service/batch-test', {
      method: 'POST',
      headers: { 'Authorization': getHeaders()['Authorization'] },
      body: formData
    })

    if (!res.ok) {
      const err = await res.json()
      throw new Error(err.message || err.error || '请求失败')
    }

    const data = await res.json()
    results.value = data
  } catch (e) {
    alert('批量测试失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

function downloadResults() {
  if (!results.value) return
  const rows = [['序号', '问题', '预期答案', '预期条目ID', '是否命中', '最高相关度', '耗时ms', '命中条目ID列表']]
  results.value.results.forEach((r, i) => {
    const hitIds = (r.searchHits || []).map(h => h.kbId || '').join(';')
    rows.push([
      i + 1,
      r.question,
      r.expectedAnswer || '',
      r.expectedKbId || '',
      r.matched ? '是' : '否',
      (r.topRelevance * 100).toFixed(1) + '%',
      r.elapsedMs,
      hitIds
    ])
  })
  const bom = '﻿'
  const csv = bom + rows.map(r => r.map(c => '"' + String(c).replace(/"/g, '""') + '"').join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `batch_test_${results.value.fileName || 'result'}_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

defineExpose({ open, close })
</script>

<style scoped>
.batch-test-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  padding: 20px;
}
.batch-test-modal {
  background: var(--bg-sidebar);
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 900px;
  max-height: 85dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--rule);
}
.modal-header h3 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: var(--tracking-tight);
}
.close-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.3rem;
  color: var(--text-secondary);
  transition: var(--transition);
}
.close-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

/* Upload */
.upload-area {
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.upload-hint {
  text-align: center;
  color: var(--text-secondary);
}
.upload-hint svg { margin-bottom: 12px; color: var(--text-tertiary); }
.upload-hint p { margin: 4px 0; font-size: 0.95rem; }
.sub-hint { font-size: 0.8rem !important; color: var(--text-tertiary) !important; }
.file-input { display: none; }
.upload-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border-radius: var(--radius-sm);
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--accent);
  border: 1px dashed var(--accent-border);
  background: var(--accent-bg);
  transition: var(--transition);
}
.upload-btn:hover { background: var(--accent-hover); border-color: var(--accent-hover); color: var(--text-inverse); }
.file-name { font-size: 0.85rem; color: var(--text-secondary); }
.start-btn {
  padding: 10px 32px;
  border-radius: var(--radius-sm);
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-inverse);
  background: var(--accent);
  transition: var(--transition);
}
.start-btn:hover { background: var(--accent-hover); }

/* Loading */
.loading-area {
  padding: 48px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--rule);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.loading-text { font-size: 1rem; font-weight: 500; color: var(--text-primary); margin: 0; }
.loading-sub { font-size: 0.85rem; color: var(--text-tertiary); margin: 0; }
.progress-bar {
  width: 300px;
  height: 6px;
  background: var(--bg-hover);
  border-radius: var(--radius-xs);
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: var(--radius-xs);
  transition: width 0.3s;
}

/* Results */
.results-area {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.results-summary {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  border-bottom: 1px solid var(--rule);
  flex-wrap: wrap;
}
.summary-item {
  flex: 1;
  min-width: 80px;
  text-align: center;
  padding: 10px;
  border-radius: var(--radius-sm);
  background: var(--bg-hover);
}
.summary-item.success { background: var(--success-bg); }
.summary-item.fail { background: var(--danger-bg); }
.summary-value {
  display: block;
  font-family: var(--font-serif);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
.summary-item.success .summary-value { color: var(--success); }
.summary-item.fail .summary-value { color: var(--danger); }
.summary-label { font-size: 0.75rem; color: var(--text-tertiary); letter-spacing: 0.02em; }

.results-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
}
.result-item {
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 12px 16px;
  margin-bottom: 10px;
  transition: var(--transition);
}
.result-item.matched { border-left: 3px solid var(--success); }
.result-item.unmatched { border-left: 3px solid var(--warning); }
.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.result-index { font-weight: 600; color: var(--text-primary); }
.result-badge {
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  font-weight: 600;
}
.badge-ok { background: var(--success-bg); color: var(--success); }
.badge-fail { background: var(--warning-bg); color: var(--warning); }
.result-relevance { font-size: 0.8rem; color: var(--text-secondary); }
.result-time { font-size: 0.8rem; color: var(--text-tertiary); margin-left: auto; }
.result-question { font-size: 0.9rem; color: var(--text-primary); margin: 4px 0; line-height: 1.4; }
.result-kbid { font-size: 0.8rem; color: var(--text-secondary); margin: 2px 0; }
.result-kbid code { background: var(--bg-hover); padding: 1px 6px; border-radius: var(--radius-xs); font-size: 0.8rem; }
.hit-tag { color: var(--success); font-size: 0.75rem; margin-left: 6px; }
.result-hits { margin-top: 8px; }
.hits-label { font-size: 0.8rem; color: var(--text-tertiary); margin-bottom: 4px; }
.hit-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--radius-xs);
  font-size: 0.8rem;
}
.hit-item.hit-best { background: var(--accent-bg); }
.hit-idx { font-weight: 600; color: var(--text-tertiary); }
.hit-kbid code { font-size: 0.75rem; }
.hit-relevance { color: var(--accent); font-weight: 500; }
.hit-source { color: var(--text-tertiary); margin-left: auto; }

.results-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid var(--rule);
}
.action-btn {
  padding: 8px 20px;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text-inverse);
  background: var(--accent);
  transition: var(--transition);
}
.action-btn:hover { opacity: 0.9; }
.action-btn.secondary {
  color: var(--text-secondary);
  background: var(--bg-hover);
}
.action-btn.secondary:hover { background: var(--bg-active); }
</style>
