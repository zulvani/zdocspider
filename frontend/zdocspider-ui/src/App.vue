<template>
  <v-app>
    <!-- Left Navigation Sidebar Drawer -->
    <AppSidebar
      :current-view="currentView"
      :sessions="sessions"
      :active-session-id="activeSessionId"
      :backend-connected="backendConnected"
      :ollama-connected="ollamaConnected"
      @change-view="v => currentView = v"
      @new-chat="createNewChat"
      @select-session="selectSession"
      @delete-session="deleteSession"
      @open-settings="settingsDialog = true"
    />

    <!-- Main Content App Canvas -->
    <v-main class="main-canvas">
      <!-- VIEW 1: CHAT SCREEN (MAIN) -->
      <ChatScreen
        v-if="currentView === 'chat'"
        :messages="activeMessages"
        :backend-connected="backendConnected"
        :ollama-connected="ollamaConnected"
        :available-models="availableModels"
        :current-model="currentModel"
        @update:current-model="val => currentModel = val"
        @send="handleSend"
        @stop="handleStop"
        @clear="clearCurrentChat"
        @open-root-setup="currentView = 'root-setup'"
        @open-doc-list="currentView = 'doc-list'"
      />

      <!-- VIEW 2: SET DOCUMENT ROOT (SETUP ITEM 1) -->
      <DocumentRootSetup
        v-else-if="currentView === 'root-setup'"
        @back="currentView = 'chat'"
        @open-doc-list="currentView = 'doc-list'"
      />

      <!-- VIEW 3: LIST OF DOCS (SETUP ITEM 2) -->
      <DocumentList
        v-else-if="currentView === 'doc-list'"
        @back="currentView = 'chat'"
        @open-root-setup="currentView = 'root-setup'"
        @ask-about-doc="handleAskAboutDoc"
      />
    </v-main>

    <!-- Settings & Ollama Configuration Dialog -->
    <SettingsDialog
      v-model="settingsDialog"
      :available-models="availableModels"
      @settings-updated="onSettingsUpdated"
    />
  </v-app>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import ChatScreen from '@/components/chat/ChatScreen.vue'
import DocumentRootSetup from '@/components/setup/DocumentRootSetup.vue'
import DocumentList from '@/components/setup/DocumentList.vue'
import SettingsDialog from '@/components/settings/SettingsDialog.vue'
import { ApiService, loadSettings } from '@/services/api'
import type { ChatSession, ChatMessage, Doc, AppSettings } from '@/types'

// View navigation state
const currentView = ref<'chat' | 'root-setup' | 'doc-list'>('chat')
const settingsDialog = ref(false)

// Backend & Ollama connectivity state
const backendConnected = ref(false)
const ollamaConnected = ref(false)
const availableModels = ref<string[]>([
  'llama3.2',
  'llama3:8b',
  'mistral',
  'deepseek-r1:8b',
  'phi3',
  'qwen2.5',
])
const currentModel = ref('llama3.2')

// Chat sessions state
const sessions = ref<ChatSession[]>([])
const activeSessionId = ref<string>('')
let abortController: AbortController | null = null

const activeSession = computed(() => {
  return sessions.value.find(s => s.id === activeSessionId.value) || null
})

const activeMessages = computed(() => {
  return activeSession.value?.messages || []
})

onMounted(async () => {
  loadSavedSessions()
  const settings = loadSettings()
  currentModel.value = settings.selectedModel || 'llama3.2'

  await checkConnectivity()
})

async function checkConnectivity() {
  const settings = loadSettings()
  backendConnected.value = await ApiService.checkBackendStatus()
  ollamaConnected.value = await ApiService.checkOllamaStatus(settings.ollamaUrl)

  if (ollamaConnected.value) {
    const models = await ApiService.fetchOllamaModels(settings.ollamaUrl)
    if (models.length > 0) {
      availableModels.value = models
      if (!models.includes(currentModel.value)) {
        currentModel.value = models[0]
      }
    }
  }
}

function loadSavedSessions() {
  try {
    const raw = localStorage.getItem('zdocspider_chat_sessions')
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) {
        sessions.value = parsed
        activeSessionId.value = parsed[0].id
        return
      }
    }
  } catch (e) {
    console.error('Error loading chat history:', e)
  }

  // Create default starter session
  createNewChat()
}

function saveSessions() {
  try {
    localStorage.setItem('zdocspider_chat_sessions', JSON.stringify(sessions.value))
  } catch (e) {
    console.error('Error saving chat history:', e)
  }
}

function createNewChat() {
  const newSession: ChatSession = {
    id: 'chat_' + Date.now(),
    title: 'New conversation',
    createdAt: Date.now(),
    updatedAt: Date.now(),
    messages: [],
  }

  sessions.value.unshift(newSession)
  activeSessionId.value = newSession.id
  currentView.value = 'chat'
  saveSessions()
}

function selectSession(id: string) {
  activeSessionId.value = id
  currentView.value = 'chat'
}

function deleteSession(id: string) {
  sessions.value = sessions.value.filter(s => s.id !== id)
  if (sessions.value.length === 0) {
    createNewChat()
  } else if (activeSessionId.value === id) {
    activeSessionId.value = sessions.value[0].id
  }
  saveSessions()
}

function clearCurrentChat() {
  if (activeSession.value) {
    activeSession.value.messages = []
    activeSession.value.title = 'New conversation'
    saveSessions()
  }
}

async function handleSend(query: string, rag: boolean) {
  if (!activeSession.value) {
    createNewChat()
  }

  const session = activeSession.value!

  // Update session title on first message
  if (session.messages.length === 0) {
    session.title = query.length > 32 ? query.substring(0, 32) + '...' : query
  }

  // Add User Message
  const userMsg: ChatMessage = {
    id: 'msg_' + Date.now(),
    role: 'user',
    content: query,
    timestamp: Date.now(),
  }
  session.messages.push(userMsg)

  // Add Assistant Message placeholder
  const assistantMsgId = 'msg_' + (Date.now() + 1)
  const assistantMsg: ChatMessage = {
    id: assistantMsgId,
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    isGenerating: true,
  }
  session.messages.push(assistantMsg)
  saveSessions()

  const settings = loadSettings()

  try {
    const history = session.messages
      .filter(m => m.id !== assistantMsgId)
      .map(m => ({ role: m.role, content: m.content }))

    const result = await ApiService.sendChatMessage(history, {
      ollamaUrl: settings.ollamaUrl,
      model: currentModel.value,
      systemPrompt: settings.systemPrompt,
      enableRag: rag,
      onChunk: (text) => {
        assistantMsg.content = text
      },
    })

    assistantMsg.content = result.text
    assistantMsg.sources = result.sources
    assistantMsg.isGenerating = false
  } catch (err: any) {
    assistantMsg.content = `Error communicating with model: ${err?.message || 'Unknown error'}`
    assistantMsg.error = true
    assistantMsg.isGenerating = false
  } finally {
    session.updatedAt = Date.now()
    saveSessions()
  }
}

function handleStop() {
  if (abortController) {
    abortController.abort()
  }
  if (activeSession.value) {
    const lastMsg = activeSession.value.messages.slice(-1)[0]
    if (lastMsg && lastMsg.isGenerating) {
      lastMsg.isGenerating = false
    }
  }
}

function handleAskAboutDoc(doc: Doc) {
  currentView.value = 'chat'
  const prompt = `Please summarize the document "${doc.fileName}" and list key details from its extracted content.`
  handleSend(prompt, true)
}

function onSettingsUpdated(updatedSettings: AppSettings) {
  currentModel.value = updatedSettings.selectedModel
  checkConnectivity()
}
</script>

<style scoped>
.main-canvas {
  height: 100vh;
  overflow: hidden;
  background-color: rgb(var(--v-theme-background));
}
</style>
