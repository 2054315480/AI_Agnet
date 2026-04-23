import { ref } from 'vue'
import { useConversations } from './useConversations.js'
import { chatCustomerServiceStream } from '../api/chat.js'

export function useChat() {
  const {
    activeConversation,
    activeAgent,
    addMessage,
    updateLastMessage,
    createConversation
  } = useConversations()

  const isLoading = ref(false)
  let abortController = null

  // 当前客服会话 ID（由后端返回）
  let currentSessionId = null

  function sendMessage(text, imageFile, imageUrl) {
    if ((!text.trim() && !imageFile) || isLoading.value) return

    let conv = activeConversation.value
    if (!conv) {
      conv = createConversation(activeAgent.value)
    }

    const convId = conv.id

    addMessage(convId, {
      content: text || '(图片)',
      isUser: true,
      imageUrl: imageUrl || null
    })

    addMessage(convId, { content: '', isUser: false, loading: true, thinkingSteps: [], intentInfo: null })

    isLoading.value = true
    sendCustomerServiceMessage(text, convId)
  }

  function sendCustomerServiceMessage(text, convId) {
    abortController = chatCustomerServiceStream(text, currentSessionId, {
      onStep(rawData) {
        let event = null
        try {
          event = JSON.parse(rawData)
        } catch (e) {
          event = { type: 'answer', content: rawData }
        }

        const lastMsg = getLastMsg(convId)
        if (!lastMsg || lastMsg.isUser) return

        if (event.type === 'session_id') {
          // 保存后端返回的会话 ID
          currentSessionId = event.content
        } else if (event.type === 'intent') {
          // 意图识别结果
          try {
            const intentData = JSON.parse(event.content)
            updateLastMessage(convId, { intentInfo: intentData, loading: true })
          } catch (e) {
            // ignore parse error
          }
        } else if (event.type === 'clarification') {
          // 澄清问题
          updateLastMessage(convId, { content: event.content, loading: true, isClarification: true })
        } else if (event.type === 'handoff') {
          // 转人工提示
          updateLastMessage(convId, { content: event.content, loading: true, isHandoff: true })
        } else if (event.type === 'tool_call' || event.type === 'tool_result') {
          // 工具调用步骤
          const steps = [...(lastMsg.thinkingSteps || []), { type: event.type, content: event.content }]
          updateLastMessage(convId, { thinkingSteps: steps, loading: true })
        } else if (event.type === 'answer') {
          updateLastMessage(convId, { content: event.content, loading: true })
        } else if (event.type === 'suggested_questions') {
          try {
            const questions = JSON.parse(event.content)
            updateLastMessage(convId, { suggestedQuestions: questions, loading: true })
          } catch (e) {
            // ignore parse error
          }
        } else if (event.type === 'done') {
          updateLastMessage(convId, { loading: false, time: currentTime() })
        }
      },
      onComplete() {
        const lastMsg = getLastMsg(convId)
        if (lastMsg && !lastMsg.isUser && lastMsg.loading) {
          updateLastMessage(convId, { loading: false, time: currentTime() })
        }
        isLoading.value = false
        abortController = null
      },
      onError(err) {
        const lastMsg = getLastMsg(convId)
        if (lastMsg && !lastMsg.isUser) {
          updateLastMessage(convId, {
            content: `抱歉，发生了错误：${err.message}`,
            loading: false,
            time: currentTime()
          })
        }
        isLoading.value = false
        abortController = null
      }
    })
  }

  function stopGeneration() {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    isLoading.value = false
  }

  function getLastMsg(convId) {
    const { activeConversation } = useConversations()
    const conv = convId === activeConversation.value?.id
      ? activeConversation.value
      : null
    if (!conv || conv.messages.length === 0) return null
    return conv.messages[conv.messages.length - 1]
  }

  return {
    isLoading,
    sendMessage,
    stopGeneration
  }
}

function currentTime() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}
