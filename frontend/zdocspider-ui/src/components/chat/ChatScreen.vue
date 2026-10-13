<template>
  <div class="chat-screen d-flex flex-column h-100">
    <!-- Top Model Header Bar -->
    <div class="chat-header d-flex align-center justify-space-between px-4 py-2 border-b">
      <!-- Model Selector / Indicator -->
      <div class="d-flex align-center ga-3">
        <v-menu location="bottom start">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              variant="tonal"
              color="primary"
              size="small"
              class="rounded-pill font-weight-bold px-3 text-none"
              append-icon="mdi-chevron-down"
            >
              <v-icon start icon="mdi-head-snowflake-outline" size="16" />
              {{ selectedModel }}
            </v-btn>
          </template>

          <v-list density="compact" class="rounded-lg elevation-4" min-width="200">
            <v-list-subheader class="text-uppercase text-caption font-weight-bold">
              Select Ollama Model
            </v-list-subheader>
            <v-list-item
              v-for="model in availableModels"
              :key="model"
              :value="model"
              :active="selectedModel === model"
              @click="selectModel(model)"
            >
              <template #prepend>
                <v-icon
                  :icon="selectedModel === model ? 'mdi-check-circle' : 'mdi-circle-outline'"
                  size="16"
                  :color="selectedModel === model ? 'primary' : 'disabled'"
                />
              </template>
              <v-list-item-title class="font-mono text-body-2">{{ model }}</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>

        <!-- Status Badges -->
        <div class="d-flex align-center ga-2 d-none d-md-flex">
          <v-tooltip :text="backendConnected ? 'Connected to Spring Boot API' : 'Backend offline (using mock engine)'" location="bottom">
            <template #activator="{ props }">
              <v-chip
                v-bind="props"
                size="x-small"
                variant="outlined"
                :color="backendConnected ? 'success' : 'warning'"
                density="compact"
              >
                <v-badge
                  dot
                  inline
                  :color="backendConnected ? 'success' : 'warning'"
                  class="mr-1"
                />
                {{ backendConnected ? 'ZDoc VectorDB' : 'Local Mock' }}
              </v-chip>
            </template>
          </v-tooltip>

          <v-tooltip :text="ollamaConnected ? 'Ollama daemon reachable' : 'Ollama offline at http://localhost:11434'" location="bottom">
            <template #activator="{ props }">
              <v-chip
                v-bind="props"
                size="x-small"
                variant="outlined"
                :color="ollamaConnected ? 'success' : 'secondary'"
                density="compact"
              >
                <v-badge
                  dot
                  inline
                  :color="ollamaConnected ? 'success' : 'secondary'"
                  class="mr-1"
                />
                {{ ollamaConnected ? 'Ollama Online' : 'Ollama Offline' }}
              </v-chip>
            </template>
          </v-tooltip>
        </div>
      </div>

      <!-- Action Tools -->
      <div class="d-flex align-center ga-1">
        <v-tooltip text="Set Document Root" location="bottom">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-folder-cog-outline"
              variant="text"
              size="small"
              density="comfortable"
              @click="$emit('openRootSetup')"
            />
          </template>
        </v-tooltip>

        <v-tooltip text="View Document List" location="bottom">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-file-document-multiple-outline"
              variant="text"
              size="small"
              density="comfortable"
              @click="$emit('openDocList')"
            />
          </template>
        </v-tooltip>

        <v-tooltip text="Clear Chat Messages" location="bottom">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              icon="mdi-delete-sweep-outline"
              variant="text"
              size="small"
              density="comfortable"
              :disabled="messages.length === 0"
              @click="confirmClearChat"
            />
          </template>
        </v-tooltip>
      </div>
    </div>

    <!-- Messages Container -->
    <div ref="scrollContainerRef" class="chat-messages-container flex-grow-1 overflow-y-auto">
      <!-- Empty State / Welcome Screen -->
      <div
        v-if="messages.length === 0"
        class="empty-state d-flex flex-column align-center justify-center h-100 px-4 py-8 text-center"
      >
        <v-avatar size="68" color="surface-variant" class="mb-4 elevation-2">
          <v-icon icon="mdi-creation" size="36" color="primary" />
        </v-avatar>

        <h1 class="text-h5 font-weight-bold mb-2">
          What would you like to explore today?
        </h1>
        <p class="text-body-2 text-medium-emphasis mb-6" style="max-width: 480px;">
          Ask questions across your ingested documents. Powered by local vector search and LLM synthesis.
        </p>

        <!-- Prompt Starters Cards Grid -->
        <div class="prompt-grid w-100" style="max-width: 760px;">
          <v-row dense>
            <v-col
              v-for="(card, i) in promptStarters"
              :key="i"
              cols="12"
              sm="6"
            >
              <v-card
                variant="outlined"
                class="prompt-card pa-3 text-left rounded-lg cursor-pointer transition-all"
                hover
                @click="sendPrompt(card.prompt)"
              >
                <div class="d-flex align-start ga-3">
                  <v-icon :icon="card.icon" size="20" color="primary" class="mt-1" />
                  <div>
                    <div class="text-body-2 font-weight-bold mb-1">{{ card.title }}</div>
                    <div class="text-caption text-medium-emphasis">{{ card.description }}</div>
                  </div>
                </div>
              </v-card>
            </v-col>
          </v-row>
        </div>
      </div>

      <!-- Messages List -->
      <div v-else class="messages-list py-4">
        <ChatMessageItem
          v-for="msg in messages"
          :key="msg.id"
          :message="msg"
        />
      </div>
    </div>

    <!-- Input Bar -->
    <ChatInput
      v-model="inputQuery"
      :is-generating="isGenerating"
      :rag-enabled="ragEnabled"
      @update:rag-enabled="val => ragEnabled = val"
      @send="handleSend"
      @stop="handleStop"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import ChatMessageItem from './ChatMessageItem.vue'
import ChatInput from './ChatInput.vue'
import { ApiService, loadSettings } from '@/services/api'
import type { ChatMessage } from '@/types'

const props = defineProps<{
  messages: ChatMessage[]
  backendConnected: boolean
  ollamaConnected: boolean
  availableModels: string[]
  currentModel: string
}>()

const emit = defineEmits<{
  (e: 'update:currentModel', model: string): void
  (e: 'send', query: string, rag: boolean): void
  (e: 'stop'): void
  (e: 'clear'): void
  (e: 'openRootSetup'): void
  (e: 'openDocList'): void
}>()

const inputQuery = ref('')
const isGenerating = ref(false)
const ragEnabled = ref(true)
const scrollContainerRef = ref<HTMLElement | null>(null)

const selectedModel = ref(props.currentModel || 'llama3.2')

watch(() => props.currentModel, (val) => {
  if (val) selectedModel.value = val
})

const promptStarters = [
  {
    icon: 'mdi-file-find-outline',
    title: 'Find Duplicate Documents',
    description: 'Check document database for identical files and duplicate checksums',
    prompt: 'Detect and list any duplicate PDF documents currently registered in the system.',
  },
  {
    icon: 'mdi-folder-wrench-outline',
    title: 'Set Document Root Path',
    description: 'Learn how to scan local directories and build vector embeddings',
    prompt: 'How do I set the document root path and index documents into sqlite-vec?',
  },
  {
    icon: 'mdi-vector-polyline',
    title: 'Semantic Architecture Query',
    description: 'Search documentation for ONNX embedding & sqlite-vec features',
    prompt: 'What embedding model and vector dimensionality does ZDocSpider use for semantic search?',
  },
  {
    icon: 'mdi-chart-bar',
    title: 'Financial & Policy Inquiries',
    description: 'Summarize financial reports or employee policies',
    prompt: 'Summarize the latest financial report and highlights from the document library.',
  },
]

function selectModel(model: string) {
  selectedModel.value = model
  emit('update:currentModel', model)
}

function sendPrompt(promptText: string) {
  handleSend(promptText)
}

function handleSend(query: string) {
  emit('send', query, ragEnabled.value)
  scrollToBottom()
}

function handleStop() {
  emit('stop')
}

function confirmClearChat() {
  if (confirm('Clear current chat conversation?')) {
    emit('clear')
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollContainerRef.value) {
      scrollContainerRef.value.scrollTo({
        top: scrollContainerRef.value.scrollHeight,
        behavior: 'smooth',
      })
    }
  })
}

watch(() => props.messages.length, () => {
  scrollToBottom()
})

onMounted(() => {
  const settings = loadSettings()
  ragEnabled.value = settings.enableRag
  scrollToBottom()
})
</script>

<style scoped>
.chat-screen {
  position: relative;
  background-color: rgb(var(--v-theme-background));
}

.chat-header {
  background-color: rgb(var(--v-theme-surface));
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
  min-height: 52px;
}

.prompt-card {
  border-color: rgba(148, 163, 184, 0.2);
  background-color: rgba(var(--v-theme-surface-variant), 0.4);
  transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.prompt-card:hover {
  transform: translateY(-2px);
  border-color: rgb(var(--v-theme-primary));
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.border-b {
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}
</style>
