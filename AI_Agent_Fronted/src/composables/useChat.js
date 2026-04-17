import { ref } from 'vue'
import { useConversations } from './useConversations.js'
import { chatLoveStream, chatLoveStreamWithImage, chatManusStream, chatManusStreamWithImage } from '../api/chat.js'

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

  function sendMessage(text, imageFile, imageUrl) {
    if ((!text.trim() && !imageFile) || isLoading.value) return

    let conv = activeConversation.value
    // Create conversation if none exists
    if (!conv) {
      conv = createConversation(activeAgent.value)
    }

    const convId = conv.id

    // Add user message with optional image
    addMessage(convId, {
      content: text || '(图片)',
      isUser: true,
      imageUrl: imageUrl || null
    })

    // Add AI placeholder
    addMessage(convId, { content: '', isUser: false, loading: true, thinkingSteps: [] })

    isLoading.value = true

    if (activeAgent.value === 'love') {
      sendLoveMessage(text, convId, imageFile)
    } else {
      sendManusMessage(text, convId, imageFile)
    }
  }

  function sendLoveMessage(text, convId, imageFile) {
    if (imageFile) {
      abortController = chatLoveStreamWithImage(text, convId, imageFile, {
        onChunk(data) {
          const lastMsg = getLastMsg(convId)
          if (lastMsg && !lastMsg.isUser) {
            updateLastMessage(convId, {
              content: lastMsg.content + data,
              loading: true
            })
          }
        },
        onComplete() {
          const lastMsg = getLastMsg(convId)
          if (lastMsg && !lastMsg.isUser) {
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
    } else {
      abortController = chatLoveStream(text, convId, {
        onChunk(data) {
          const lastMsg = getLastMsg(convId)
          if (lastMsg && !lastMsg.isUser) {
            updateLastMessage(convId, {
              content: lastMsg.content + data,
              loading: true
            })
          }
        },
        onComplete() {
          const lastMsg = getLastMsg(convId)
          if (lastMsg && !lastMsg.isUser) {
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
  }

  function sendManusMessage(text, convId, imageFile) {
    const streamFn = imageFile
      ? (msg, cb) => chatManusStreamWithImage(msg, imageFile, cb)
      : (msg, cb) => chatManusStream(msg, cb)

    abortController = streamFn(text, {
      onStep(rawData) {
        let event = null
        try {
          event = JSON.parse(rawData)
        } catch (e) {
          event = { type: 'thinking', content: rawData }
        }

        const lastMsg = getLastMsg(convId)
        if (!lastMsg || lastMsg.isUser) return

        if (event.type === 'tool_call' || event.type === 'tool_result' || event.type === 'thinking') {
          const steps = [...(lastMsg.thinkingSteps || []), { type: event.type, content: event.content }]
          updateLastMessage(convId, { thinkingSteps: steps, loading: true })
        } else if (event.type === 'answer') {
          updateLastMessage(convId, { content: event.content, loading: true })
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
            content: `执行出错：${err.message}`,
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

  // Helper: get last message without importing again
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
