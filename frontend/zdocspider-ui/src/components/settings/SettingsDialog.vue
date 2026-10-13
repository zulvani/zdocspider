<template>
  <v-dialog v-model="isOpen" max-width="640">
    <v-card class="rounded-xl pa-4">
      <v-card-title class="d-flex align-center justify-space-between pb-2">
        <div class="d-flex align-center ga-2">
          <v-icon icon="mdi-cog-outline" color="primary" />
          <span class="font-weight-bold">Settings & Configuration</span>
        </div>
        <v-btn icon="mdi-close" variant="text" size="small" @click="isOpen = false" />
      </v-card-title>

      <v-divider />

      <v-card-text class="pt-4">
        <!-- Backend API Server -->
        <div class="mb-4">
          <div class="text-subtitle-2 font-weight-bold mb-1">Spring Boot Backend API</div>
          <v-text-field
            v-model="settings.backendUrl"
            label="Backend URL"
            placeholder="http://localhost:8080"
            variant="outlined"
            density="compact"
            class="font-mono mb-2"
            hint="ZDocSpider Spring Boot server URL"
            persistent-hint
          />
          <div class="d-flex align-center ga-2">
            <v-chip
              size="x-small"
              :color="backendStatus ? 'success' : 'warning'"
              variant="flat"
            >
              {{ backendStatus ? 'Connected' : 'Offline / Mock Engine' }}
            </v-chip>
            <v-btn
              size="x-small"
              variant="tonal"
              :loading="testingBackend"
              @click="testBackend"
            >
              Test Backend
            </v-btn>
          </div>
        </div>

        <v-divider class="my-4" />

        <!-- Ollama Service -->
        <div class="mb-4">
          <div class="text-subtitle-2 font-weight-bold mb-1">Ollama LLM Service</div>
          <v-text-field
            v-model="settings.ollamaUrl"
            label="Ollama API URL"
            placeholder="http://localhost:11434"
            variant="outlined"
            density="compact"
            class="font-mono mb-2"
            hint="Local Ollama service endpoint"
            persistent-hint
          />

          <div class="d-flex align-center ga-2 mb-3">
            <v-chip
              size="x-small"
              :color="ollamaStatus ? 'success' : 'secondary'"
              variant="flat"
            >
              {{ ollamaStatus ? 'Connected' : 'Offline' }}
            </v-chip>
            <v-btn
              size="x-small"
              variant="tonal"
              :loading="testingOllama"
              @click="testOllama"
            >
              Test Ollama
            </v-btn>
          </div>

          <!-- Model Selection -->
          <v-select
            v-model="settings.selectedModel"
            :items="availableModels"
            label="Default Model"
            variant="outlined"
            density="compact"
            class="font-mono mb-2"
            hint="Model used for conversational inference"
            persistent-hint
          />
        </div>

        <v-divider class="my-4" />

        <!-- RAG & Embedding Settings -->
        <div class="mb-4">
          <div class="text-subtitle-2 font-weight-bold mb-1">Retrieval-Augmented Generation (RAG)</div>
          <v-switch
            v-model="settings.enableRag"
            label="Enable Document Grounding"
            color="primary"
            density="compact"
            hide-details
            class="mb-2"
          />

          <div class="d-flex align-center justify-space-between mb-1">
            <span class="text-caption text-medium-emphasis">Top-K Documents to Retrieve:</span>
            <span class="text-caption font-weight-bold">{{ settings.ragLimit }}</span>
          </div>
          <v-slider
            v-model="settings.ragLimit"
            :min="1"
            :max="10"
            :step="1"
            color="primary"
            density="compact"
            hide-details
          />
        </div>

        <v-divider class="my-4" />

        <!-- System Prompt -->
        <div>
          <div class="text-subtitle-2 font-weight-bold mb-1">System Instructions</div>
          <v-textarea
            v-model="settings.systemPrompt"
            rows="3"
            variant="outlined"
            density="compact"
            class="text-body-2"
            placeholder="Custom instructions for assistant behavior..."
          />
        </div>
      </v-card-text>

      <v-card-actions class="d-flex justify-space-between px-4">
        <v-btn variant="text" color="secondary" @click="resetDefaults">
          Reset Defaults
        </v-btn>
        <v-btn variant="flat" color="primary" class="px-4 font-weight-bold" @click="saveAndClose">
          Save Settings
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import { ApiService, loadSettings, saveSettings } from '@/services/api'
import type { AppSettings } from '@/types'

const props = defineProps<{
  modelValue: boolean
  availableModels: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'settingsUpdated', settings: AppSettings): void
}>()

const isOpen = ref(props.modelValue)
watch(() => props.modelValue, (val) => {
  isOpen.value = val
  if (val) {
    loadCurrent()
  }
})
watch(isOpen, (val) => {
  emit('update:modelValue', val)
})

const settings = ref<AppSettings>(loadSettings())
const backendStatus = ref(false)
const ollamaStatus = ref(false)
const testingBackend = ref(false)
const testingOllama = ref(false)

function loadCurrent() {
  settings.value = loadSettings()
  testBackend()
  testOllama()
}

async function testBackend() {
  testingBackend.value = true
  backendStatus.value = await ApiService.checkBackendStatus()
  testingBackend.value = false
}

async function testOllama() {
  testingOllama.value = true
  ollamaStatus.value = await ApiService.checkOllamaStatus(settings.value.ollamaUrl)
  testingOllama.value = false
}

function resetDefaults() {
  localStorage.removeItem('zdocspider_settings')
  settings.value = loadSettings()
}

function saveAndClose() {
  saveSettings(settings.value)
  emit('settingsUpdated', settings.value)
  isOpen.value = false
}
</script>
