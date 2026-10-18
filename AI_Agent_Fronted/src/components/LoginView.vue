<template>
  <div class="login-page">
    <!-- 左侧：品牌展示区 -->
    <div class="login-brand">
      <div class="brand-grid"></div>
      <div class="brand-gradient"></div>
      <div class="brand-content">
        <div class="brand-caption">
          <span class="brand-mark" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v7A2.5 2.5 0 0 1 17.5 16H10l-4.2 3.2A.6.6 0 0 1 5 18.7V16"/>
              <path d="M8 8.5h8M8 11.5h5"/>
            </svg>
          </span>
          <span>Service Desk</span>
        </div>
        <h1 class="brand-title">客服工作台</h1>
        <p class="brand-subtitle">先查知识库，再回答。每一条答复都能追溯到出处。</p>
        <div class="brand-divider"></div>
        <div class="brand-features">
          <div class="feature-row">
            <span class="feature-label">知识库</span>
            <span class="feature-sep"></span>
            <span class="feature-desc">FAQ 与语义检索，附原文页级出处</span>
          </div>
          <div class="feature-row">
            <span class="feature-label">对话</span>
            <span class="feature-sep"></span>
            <span class="feature-desc">信息不足先追问，必要时转人工</span>
          </div>
          <div class="feature-row">
            <span class="feature-label">复盘</span>
            <span class="feature-sep"></span>
            <span class="feature-desc">意图分布、转人工率、会话回放</span>
          </div>
        </div>
      </div>
      <div class="brand-footer">v4.2 · 内部使用</div>
    </div>

    <!-- 右侧：表单区 -->
    <div class="login-form-side">
      <div class="form-wrapper">
        <h2 class="form-title">{{ isLogin ? '登录' : '注册' }}</h2>
        <p class="form-subtitle">{{ isLogin ? '欢迎回来，请输入你的账号' : '创建一个新账号开始使用' }}</p>

        <form @submit.prevent="handleSubmit">
          <div class="field">
            <label class="field-label">用户名</label>
            <div class="input-wrap">
              <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <input v-model="username" type="text" placeholder="请输入用户名" required autocomplete="username" />
            </div>
          </div>

          <div class="field">
            <label class="field-label">密码</label>
            <div class="input-wrap">
              <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input v-model="password" type="password" placeholder="请输入密码（至少6位）" required autocomplete="current-password" />
            </div>
          </div>

          <button type="submit" class="submit-btn" :disabled="loading">
            <span v-if="loading" class="spinner"></span>
            <span>{{ loading ? '处理中...' : (isLogin ? '登 录' : '注 册') }}</span>
          </button>

          <transition name="fade">
            <p v-if="error" class="error-msg">{{ error }}</p>
          </transition>
        </form>

        <div class="switch-mode">
          <span>{{ isLogin ? '还没有账号？' : '已有账号？' }}</span>
          <button class="switch-btn" @click="isLogin = !isLogin; error = ''">
            {{ isLogin ? '立即注册' : '去登录' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../api/auth'

const router = useRouter()
const { login, register, fetchUser } = useAuth()
const isLogin = ref(true)
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleSubmit() {
  error.value = ''
  loading.value = true
  try {
    if (isLogin.value) {
      await login(username.value, password.value)
    } else {
      await register(username.value, password.value)
    }
    await fetchUser()
    router.push('/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  height: 100dvh;
  overflow: hidden;
}

/* ===== 左侧：品牌面板 ===== */
.login-brand {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  background: #0d1117;
  overflow: hidden;
  padding: 48px;
}

/* 结构化网格背景 */
.brand-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.025) 1px, transparent 1px);
  background-size: 48px 48px;
  pointer-events: none;
}

/* 单一渐变覆盖层 — 不是装饰性光球 */
.brand-gradient {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse 60% 50% at 50% 40%, rgba(31, 111, 235, 0.06), transparent);
  pointer-events: none;
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 360px;
}

.brand-badge {
  margin-bottom: 20px;
}

.brand-title {
  font-size: 2.25rem;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: -0.5px;
  line-height: 1.2;
  margin-bottom: 4px;
}

.brand-subtitle {
  color: rgba(255,255,255,0.45);
  font-size: 1rem;
  margin-bottom: 0;
}

.brand-divider {
  width: 32px;
  height: 2px;
  background: rgba(31, 111, 235, 0.4);
  margin: 28px 0;
  border-radius: 1px;
}

/* 功能列表：排版驱动，非 bullet points */
.brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.feature-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: rgba(255,255,255,0.7);
  white-space: nowrap;
  min-width: 60px;
}

.feature-sep {
  width: 16px;
  height: 1px;
  background: rgba(255,255,255,0.15);
  flex-shrink: 0;
  margin-top: 2px;
}

.feature-desc {
  font-size: 0.85rem;
  color: rgba(255,255,255,0.4);
}

.brand-footer {
  position: absolute;
  bottom: 28px;
  color: rgba(255,255,255,0.15);
  font-size: 0.75rem;
  z-index: 1;
}

/* ===== 右侧：表单面板 ===== */
.login-form-side {
  width: 460px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-app);
  border-left: 1px solid var(--border-secondary);
  padding: 48px;
}

.form-wrapper {
  width: 100%;
  max-width: 340px;
}

.form-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.form-subtitle {
  color: var(--text-tertiary);
  font-size: 0.85rem;
  margin-bottom: 32px;
}

/* ===== 表单字段 ===== */
.field {
  margin-bottom: 20px;
}

.field-label {
  display: block;
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  color: var(--text-tertiary);
  pointer-events: none;
  transition: color 0.15s ease;
}

.input-wrap input {
  width: 100%;
  padding: 12px 14px 12px 44px;
  border: 1px solid var(--border-input);
  border-radius: var(--radius-sm);
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 14px;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.input-wrap input::placeholder {
  color: var(--text-tertiary);
}

.input-wrap input:focus {
  border-color: var(--border-input-focus);
  background: var(--bg-input-focus);
  box-shadow: 0 0 0 3px rgba(31, 111, 235, 0.08);
}

.input-wrap:has(input:focus) .input-icon {
  color: var(--accent-primary);
}

/* ===== 提交按钮 ===== */
.submit-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--accent-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: background 0.15s ease, transform 0.15s ease;
}

.submit-btn:hover:not(:disabled) {
  background: var(--accent-hover);
  transform: translateY(-1px);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 错误提示 ===== */
.error-msg {
  color: var(--danger);
  font-size: 0.8rem;
  text-align: center;
  margin-top: 14px;
  background: var(--danger-bg);
  padding: 8px 14px;
  border-radius: var(--radius-xs);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ===== 切换登录/注册 ===== */
.switch-mode {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid var(--border-secondary);
  text-align: center;
  font-size: 0.85rem;
  color: var(--text-tertiary);
}

.switch-btn {
  color: var(--accent-primary);
  font-weight: 600;
  font-size: 0.85rem;
  margin-left: 4px;
}

.switch-btn:hover {
  color: var(--accent-hover);
  text-decoration: underline;
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .login-brand {
    display: none;
  }
  .login-form-side {
    width: 100%;
    border-left: none;
  }
}

@media (max-width: 480px) {
  .login-form-side {
    padding: 24px;
  }
}

/* ================================================================
   v6 覆写：左栏改成"书封"质感，右栏改成表单卡片
   ================================================================ */
.login-brand {
  background: linear-gradient(170deg, #1C2422 0%, #121715 55%, #0E1211 100%);
  color: #E9E6DF;
}

.brand-gradient { opacity: 0.5; }
.brand-grid { opacity: 0.35; }

.brand-caption {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 26px;
  font-size: 0.625rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(233,230,223,0.5);
}

.brand-mark {
  width: 30px; height: 30px;
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid rgba(233,230,223,0.18);
  border-radius: 8px;
  color: #8FC0B3;
  background: rgba(233,230,223,0.04);
}

.brand-title {
  font-family: var(--font-serif);
  font-size: clamp(1.9rem, 2.6vw, 2.5rem);
  font-weight: var(--weight-semibold);
  letter-spacing: -0.01em;
  color: #F3F0E9;
  margin-bottom: 12px;
}

.brand-subtitle {
  color: rgba(233,230,223,0.62);
  font-size: 0.9rem;
  line-height: 1.75;
  max-width: 34ch;
}

.brand-divider {
  height: 1px;
  background: rgba(233,230,223,0.12);
  margin: 26px 0;
}

.feature-row { align-items: baseline; }
.feature-label {
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  color: #C2A36B;
  min-width: 48px;
}
.feature-sep { background: rgba(233,230,223,0.14); }
.feature-desc { color: rgba(233,230,223,0.55); font-size: 0.8125rem; }
.brand-footer { color: rgba(233,230,223,0.3); letter-spacing: 0.12em; font-size: 0.625rem; }

.form-wrapper {
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-lg);
  background: var(--bg-sidebar);
  padding: 34px 32px;
  box-shadow: var(--shadow-md);
}

.form-title {
  font-family: var(--font-serif);
  font-size: 1.5rem;
  letter-spacing: -0.01em;
}

.form-subtitle { color: var(--text-secondary); font-size: 0.8125rem; }

.input-wrap {
  border: 1px solid var(--border-input);
  border-radius: var(--radius-sm);
  background: var(--bg-input);
  transition: border-color var(--duration-fast) var(--ease-out);
}

.input-wrap:focus-within { border-color: var(--accent-border); }
.input-wrap input { background: transparent; color: var(--text-primary); }
.input-icon { color: var(--text-tertiary); }

.submit-btn {
  background: var(--accent);
  color: var(--text-inverse);
  border-radius: var(--radius-sm);
  letter-spacing: 0.04em;
  transition: background var(--duration-fast) var(--ease-out);
}

.submit-btn:hover:not(:disabled) { background: var(--accent-hover); }

.switch-btn { color: var(--accent); }
</style>
