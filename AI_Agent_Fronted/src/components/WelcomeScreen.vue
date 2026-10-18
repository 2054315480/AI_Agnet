<template>
  <div class="welcome-screen">
    <div class="welcome-inner">
      <!-- 工作台标题：衬线 + 明确的下一步，而不是"AI 助手"自我介绍 -->
      <header class="workbench-head">
        <div class="eyebrow">
          <span class="eyebrow-dot"></span>
          客服工作台
        </div>
        <h2 class="headline">今天处理哪一类咨询？</h2>
        <p class="sub">
          先查企业知识库再回答，答复标注出处；信息不足会主动追问，必要时转人工。
        </p>
      </header>

      <!-- 按业务分组的高频问题，点击直接进入对话 -->
      <div class="prompt-groups">
        <section
          v-for="(group, gi) in groups"
          :key="group.title"
          class="prompt-group"
          :style="{ '--gi': gi }"
        >
          <div class="group-title">
            <span class="group-rule"></span>
            <span>{{ group.title }}</span>
          </div>
          <button
            v-for="(q, qi) in group.items"
            :key="q"
            class="prompt-row"
            :style="{ '--qi': qi }"
            @click="$emit('quick-send', q)"
          >
            <span class="prompt-text">{{ q }}</span>
            <svg class="prompt-go" width="14" height="14" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <line x1="5" y1="12" x2="17" y2="12"/>
              <polyline points="12 7 17 12 12 17"/>
            </svg>
          </button>
        </section>
      </div>

      <footer class="footnote">
        <span class="footnote-rule"></span>
        答复均标注出处，未命中知识库时会明确告知并建议转人工
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineEmits(['quick-send'])

const groups = ref([
  {
    title: '订单与物流',
    items: ['我的订单到哪了？', '想取消一笔还没发货的订单']
  },
  {
    title: '售后与保修',
    items: ['耳机左耳没声音了，还能保修吗？', '怎么申请退款，多久到账？']
  },
  {
    title: '政策与资费',
    items: ['退换货政策是怎样的？', '你们客服电话是多少？']
  },
  {
    title: '产品与选购',
    items: ['帮我推荐一款适合运动时戴的蓝牙耳机']
  }
])
</script>

<style scoped>
.welcome-screen {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 8vh 28px 40px;
  overflow-y: auto;
}

.welcome-inner {
  width: 100%;
  max-width: 640px;
  animation: rise 420ms var(--ease-out) both;
}

/* ===== 标题区 ===== */
.workbench-head { margin-bottom: 34px; }

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.6875rem;
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
  color: var(--text-tertiary);
  margin-bottom: 14px;
}

.eyebrow-dot {
  width: 5px; height: 5px; border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-bg);
}

.headline {
  font-family: var(--font-serif);
  font-size: clamp(1.5rem, 2.4vw, 1.9rem);
  font-weight: var(--weight-semibold);
  letter-spacing: -0.015em;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.sub {
  font-size: 0.875rem;
  line-height: 1.75;
  color: var(--text-secondary);
  max-width: 46ch;
}

/* ===== 分组问题 ===== */
.prompt-groups {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.prompt-group {
  animation: rise 420ms var(--ease-out) both;
  animation-delay: calc(60ms + var(--gi) * 70ms);
}

.group-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--text-tertiary);
}

.group-rule {
  width: 16px;
  height: 1px;
  background: var(--rule);
}

.prompt-row {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 11px 12px 11px 14px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.875rem;
  text-align: left;
  transition: color var(--duration-fast) var(--ease-out),
              background var(--duration-fast) var(--ease-out),
              transform var(--duration-fast) var(--ease-out);
  animation: rise 380ms var(--ease-out) both;
  animation-delay: calc(120ms + var(--gi) * 70ms + var(--qi) * 40ms);
}

/* 左侧细线在 hover 时展开，比发光更克制 */
.prompt-row::before {
  content: "";
  position: absolute;
  left: 0; top: 50%;
  width: 0; height: 18px;
  transform: translateY(-50%);
  background: var(--accent);
  border-radius: 1px;
  transition: width var(--duration-fast) var(--ease-out);
}

.prompt-row:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  transform: translateX(1px);
}

.prompt-row:hover::before { width: 2px; }

/* 行之间用发丝线分隔，更像一份清单而不是按钮堆 */
.prompt-group .prompt-row { border-radius: 0; border-bottom: 1px solid var(--rule); }
.prompt-group .prompt-row:last-child { border-bottom: 0; }
.prompt-group .prompt-row:hover { border-radius: var(--radius-xs); }

.prompt-text { flex: 1; min-width: 0; }

.prompt-go {
  flex-shrink: 0;
  color: var(--text-tertiary);
  opacity: 0;
  transform: translateX(-4px);
  transition: all var(--duration-fast) var(--ease-out);
}

.prompt-row:hover .prompt-go {
  opacity: 1;
  transform: translateX(0);
  color: var(--accent);
}

/* ===== 页脚 ===== */
.footnote {
  margin-top: 32px;
  padding-top: 14px;
  border-top: 1px solid var(--rule);
  font-size: 0.75rem;
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.footnote-rule {
  width: 14px; height: 1px;
  background: var(--accent);
  opacity: 0.7;
}

.kbd {
  display: inline-block;
  padding: 1px 5px;
  border: 1px solid var(--border-primary);
  border-bottom-width: 2px;
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-secondary);
  background: var(--bg-step);
}

.dot { opacity: 0.5; }

@media (max-width: 768px) {
  .welcome-screen { padding: 5vh 18px 28px; }
  .workbench-head { margin-bottom: 26px; }
  .prompt-groups { gap: 18px; }
}
</style>
