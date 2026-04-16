<template>
  <div class="welcome-screen">
    <div class="welcome-content">
      <!-- Agent icon -->
      <div class="welcome-icon" :class="`icon-${agent}`">
        <!-- Heart for love -->
        <svg v-if="agent === 'love'" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
        </svg>
        <!-- Terminal for manus -->
        <svg v-else width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="4 17 10 11 4 5"/>
          <line x1="12" y1="19" x2="20" y2="19"/>
        </svg>
      </div>

      <h2 class="welcome-title">{{ agentData.greeting }}</h2>
      <p class="welcome-desc">{{ agentData.description }}</p>

      <div class="quick-questions">
        <button
          v-for="q in agentData.questions"
          :key="q"
          class="quick-btn"
          :class="`btn-${agent}`"
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
  agent: { type: String, default: 'love' }
})

defineEmits(['quick-send'])

const agentData = computed(() => {
  if (props.agent === 'love') {
    return {
      greeting: '你好，我是 AI 恋爱大师',
      description: '专业的情感咨询师，为你解答恋爱中的困惑，提供贴心建议。',
      questions: [
        '如何提高恋爱中的沟通技巧？',
        '怎样判断对方是否喜欢我？',
        '异地恋怎么维持感情？'
      ]
    }
  }
  return {
    greeting: '你好，我是 HeManus 超级智能体',
    description: '拥有工具调用能力的全能 AI 助手，帮你搜索、分析、生成报告。',
    questions: [
      '帮我搜索一下今天的科技新闻',
      '帮我下载一个网页的内容并总结',
      '帮我生成一份 PDF 报告'
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

@media (max-width: 768px) {
  .welcome-content {
    padding: 0;
  }

  .quick-btn {
    max-width: 100%;
  }
}
</style>
