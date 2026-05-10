<template>
  <div class="welcome-screen">
    <div class="welcome-content">
      <!-- 品牌区域：大标题 + 副标题，排版层次驱动 -->
      <div class="hero">
        <h2 class="hero-title">你好，我是<span class="hero-accent">智能客服</span></h2>
        <p class="hero-desc">意图识别 · 知识检索 · 智能澄清 — 有问必答，服务至上</p>
      </div>

      <!-- 能力列表：左对齐，label + description 结构 -->
      <div class="capabilities">
        <div class="cap-item">
          <div class="cap-indicator" style="--cap-color: var(--accent-primary)"></div>
          <div class="cap-body">
            <span class="cap-label">意图识别</span>
            <span class="cap-desc">精准理解用户需求，自动分类 12 种意图类型</span>
          </div>
        </div>
        <div class="cap-item">
          <div class="cap-indicator" style="--cap-color: #3fb8c9"></div>
          <div class="cap-body">
            <span class="cap-label">知识库检索</span>
            <span class="cap-desc">FAQ 直达 + 语义向量两级检索，附来源标注</span>
          </div>
        </div>
        <div class="cap-item">
          <div class="cap-indicator" style="--cap-color: #2ea043"></div>
          <div class="cap-body">
            <span class="cap-label">智能澄清</span>
            <span class="cap-desc">意图模糊时主动追问，引导用户精准表达</span>
          </div>
        </div>
      </div>

      <!-- 分隔线 -->
      <div class="divider"></div>

      <!-- 快捷入口：右箭头引导的 CTA 按钮 -->
      <div class="quick-actions">
        <div class="quick-header">快速开始</div>
        <button
          v-for="q in questions"
          :key="q"
          class="quick-btn"
          @click="$emit('quick-send', q)"
        >
          <span class="quick-text">{{ q }}</span>
          <svg class="quick-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="5" y1="12" x2="19" y2="12"/>
            <polyline points="12 5 19 12 12 19"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineEmits(['quick-send'])

const questions = ref([
  '查询一下我的订单状态',
  '这款产品的保修政策是什么？',
  '我想申请退款',
  '帮我推荐一款合适的产品'
])
</script>

<style scoped>
.welcome-screen {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  overflow-y: auto;
}

.welcome-content {
  max-width: 480px;
  width: 100%;
  animation: contentEnter 0.4s ease;
}

@keyframes contentEnter {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ===== Hero 区域 ===== */
.hero {
  margin-bottom: 32px;
}

.hero-title {
  font-size: 1.75rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1.3;
  margin-bottom: 8px;
  letter-spacing: -0.3px;
}

.hero-accent {
  color: var(--accent-primary);
}

.hero-desc {
  font-size: 0.9375rem;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* ===== 能力列表 ===== */
.capabilities {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-bottom: 24px;
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius);
  overflow: hidden;
}

.cap-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  background: var(--bg-sidebar);
  transition: background 0.15s ease;
}

/* 列表项之间用细线分隔 */
.cap-item + .cap-item {
  border-top: 1px solid var(--border-secondary);
}

.cap-item:hover {
  background: var(--bg-hover);
}

.cap-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cap-color);
  margin-top: 8px;
  flex-shrink: 0;
}

.cap-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cap-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
}

.cap-desc {
  font-size: 0.8125rem;
  color: var(--text-tertiary);
  line-height: 1.5;
}

/* ===== 分隔线 ===== */
.divider {
  height: 1px;
  background: var(--border-secondary);
  margin-bottom: 24px;
}

/* ===== 快捷入口 ===== */
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quick-header {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.quick-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-secondary);
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.875rem;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.quick-btn:hover {
  border-color: var(--accent-primary);
  background: rgba(31, 111, 235, 0.04);
  color: var(--text-primary);
}

.quick-btn:hover .quick-arrow {
  color: var(--accent-primary);
  transform: translateX(2px);
}

.quick-text {
  flex: 1;
  min-width: 0;
}

.quick-arrow {
  flex-shrink: 0;
  color: var(--text-tertiary);
  opacity: 0.5;
  transition: all 0.15s ease;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .hero-title {
    font-size: 1.4rem;
  }

  .hero-desc {
    font-size: 0.875rem;
  }

  .cap-item {
    padding: 12px 14px;
  }
}

@media (max-width: 480px) {
  .welcome-screen {
    padding: 24px 16px;
  }

  .hero-title {
    font-size: 1.25rem;
  }

  .hero {
    margin-bottom: 24px;
  }

  .capabilities {
    margin-bottom: 20px;
  }
}
</style>
