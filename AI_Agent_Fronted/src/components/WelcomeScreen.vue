<template>
  <div class="welcome-screen">
    <div class="welcome-content">
      <!-- Agent icon -->
      <div class="welcome-icon icon-cs">
        <!-- Customer service icon -->
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
      </div>

      <h2 class="welcome-title">{{ agentData.greeting }}</h2>
      <p class="welcome-desc">{{ agentData.description }}</p>

      <div class="quick-questions">
        <button
          v-for="q in agentData.questions"
          :key="q"
          class="quick-btn btn-cs"
          @click="$emit('quick-send', q)"
        >
          {{ q }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  agent: { type: String, default: 'customer_service' }
})

defineEmits(['quick-send'])

const agentData = computed(() => {
  return {
    greeting: '你好，我是智能客服',
    description: '为您提供订单查询、产品咨询、售后服务等专业客服支持。有问必答，服务至上。',
    questions: [
      '查询一下我的订单状态',
      '这款产品的保修政策是什么？',
      '我想申请退款',
      '帮我推荐一款合适的产品'
    ]
  }
})
</script>

<style scoped>
.welcome-screen {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.welcome-content {
  text-align: center;
  max-width: 520px;
}

.welcome-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
}

.welcome-icon.icon-love {
  background: var(--accent-love-bg);
  color: var(--accent-love);
  border: 1px solid var(--accent-love-border);
}

.welcome-icon.icon-manus {
  background: var(--accent-manus-bg);
  color: var(--accent-manus);
  border: 1px solid var(--accent-manus-border);
}

.welcome-icon.icon-cs {
  background: rgba(31, 111, 235, 0.1);
  color: var(--accent-primary);
  border: 1px solid rgba(31, 111, 235, 0.2);
}

.welcome-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.welcome-desc {
  font-size: 0.9375rem;
  color: var(--text-secondary);
  margin-bottom: 32px;
  line-height: 1.5;
}

.quick-questions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}

.quick-btn {
  padding: 12px 20px;
  border-radius: var(--radius);
  border: 1px solid var(--border-primary);
  background: var(--bg-hover);
  color: var(--text-primary);
  font-size: 0.875rem;
  cursor: pointer;
  transition: var(--transition);
  width: 100%;
  max-width: 360px;
  text-align: left;
}

.quick-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.quick-btn.btn-love:hover {
  border-color: var(--accent-love-border);
  background: var(--accent-love-bg);
}

.quick-btn.btn-manus:hover {
  border-color: var(--accent-manus-border);
  background: var(--accent-manus-bg);
}

.quick-btn.btn-cs:hover {
  border-color: rgba(31, 111, 235, 0.3);
  background: rgba(31, 111, 235, 0.08);
}

@media (max-width: 768px) {
  .welcome-content {
    padding: 0;
  }

  .quick-btn {
    max-width: 100%;
  }
}
</style>
