<template>
  <div class="chat-input-wrapper pa-4">
    <div class="chat-input-container mx-auto" style="max-width: 820px;">
      <!-- Main floating card -->
      <v-card
        variant="elevated"
        class="input-box-card rounded-xl pa-2"
        elevation="3"
      >
        <!-- Textarea -->
        <textarea
          ref="textareaRef"
          v-model="inputQuery"
          class="chat-textarea px-3 pt-2 text-body-1"
          rows="1"
          :placeholder="placeholderText"
          :disabled="disabled"
          @keydown="handleKeydown"
          @input="adjustHeight"
        />

        <!-- Bottom Controls Bar -->
        <div class="d-flex align-center justify-space-between px-2 pt-1">
          <!-- Left Controls -->
          <div class="d-flex align-center ga-2">
            <!-- RAG Toggle Chip -->
            <v-tooltip text="Toggle Document Vector Search & RAG grounding" location="top">
              <template #activator="{ props }">
                <v-chip
                  v-bind="props"
                  size="small"
                  filter
                  :variant="ragEnabled ? 'flat' : 'outlined'"
                  :color="ragEnabled ? 'primary' : 'secondary'"
                  class="font-weight-medium px-3"
                  @click="$emit('update:ragEnabled', !ragEnabled)"
                >
                  <v-icon
                    start
                    :icon="ragEnabled ? 'mdi-database-search' : 'mdi-database-off-outline'"
                    size="15"
                  />
                  {{ ragEnabled ? 'Doc RAG Active' : 'RAG Off' }}
                </v-chip>
              </template>
            </v-tooltip>

            <!-- Quick Info / Helper text -->
            <span class="text-caption text-medium-emphasis d-none d-sm-inline">
              Press Enter to send, Shift + Enter for new line
            </span>
          </div>

          <!-- Right Action Buttons -->
          <div class="d-flex align-center ga-1">
            <!-- Stop Generating button -->
            <v-btn
              v-if="isGenerating"
              icon="mdi-stop"
              size="small"
              variant="flat"
              color="error"
              class="rounded-circle"
              @click="$emit('stop')"
            />

            <!-- Send button -->
            <v-btn
              v-else
              icon="mdi-arrow-up"
              size="small"
              variant="flat"
              :color="canSend ? 'primary' : 'surface-variant'"
              :disabled="!canSend || disabled"
              class="rounded-circle elevation-1 transition-all"
              @click="submit"
            />
          </div>
        </div>
      </v-card>

      <!-- Disclaimer Footer -->
      <div class="text-center pt-2">
        <span class="text-caption text-disabled" style="font-size: 0.73rem !important;">
          ZDocSpider AI Assistant can answer questions using indexed PDF documents and ONNX vector embeddings.
        </span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, nextTick } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string
    isGenerating?: boolean
    disabled?: boolean
    ragEnabled?: boolean
    placeholder?: string
  }>(),
  {
    isGenerating: false,
    disabled: false,
    ragEnabled: true,
    placeholder: 'Ask anything about your documents...',
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:ragEnabled', value: boolean): void
  (e: 'send', query: string): void
  (e: 'stop'): void
}>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)

const inputQuery = computed({
  get: () => props.modelValue,
  set: (val: string) => emit('update:modelValue', val),
})

const canSend = computed(() => {
  return inputQuery.value.trim().length > 0 && !props.isGenerating
})

const placeholderText = computed(() => {
  if (props.isGenerating) return 'Assistant is thinking...'
  return props.placeholder
})

function adjustHeight() {
  nextTick(() => {
    if (!textareaRef.value) return
    textareaRef.value.style.height = 'auto'
    const newHeight = Math.min(textareaRef.value.scrollHeight, 180)
    textareaRef.value.style.height = `${newHeight}px`
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (canSend.value) {
      submit()
    }
  }
}

function submit() {
  const query = inputQuery.value.trim()
  if (!query || props.isGenerating) return
  emit('send', query)
  emit('update:modelValue', '')
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
      textareaRef.value.focus()
    }
  })
}
</script>

<style scoped>
.chat-input-wrapper {
  background: linear-gradient(180deg, transparent 0%, rgba(var(--v-theme-background), 0.95) 40%);
  position: sticky;
  bottom: 0;
  z-index: 10;
}

.input-box-card {
  background-color: rgb(var(--v-theme-surface)) !important;
  border: 1px solid rgba(148, 163, 184, 0.25);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12) !important;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.input-box-card:focus-within {
  border-color: rgb(var(--v-theme-primary));
  box-shadow: 0 10px 35px rgba(0, 0, 0, 0.18) !important;
}

.chat-textarea {
  width: 100%;
  max-height: 180px;
  min-height: 44px;
  resize: none;
  border: none;
  outline: none;
  background: transparent;
  color: inherit;
  font-family: inherit;
  line-height: 1.5;
  box-sizing: border-box;
}

.chat-textarea::placeholder {
  color: rgba(148, 163, 184, 0.6);
}
</style>
