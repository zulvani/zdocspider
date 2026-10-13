<template>
  <div
    class="message-wrapper py-3 px-4 transition-all"
    :class="[
      message.role === 'user' ? 'user-message' : 'assistant-message',
      { 'message-error': message.error }
    ]"
  >
    <div class="message-container d-flex ga-4 mx-auto" style="max-width: 820px;">
      <!-- Avatar -->
      <div class="avatar-col flex-shrink-0 pt-1">
        <v-avatar
          :color="message.role === 'user' ? 'primary' : 'surface-variant'"
          size="34"
          class="elevation-1 border-subtle"
        >
          <v-icon
            v-if="message.role === 'user'"
            icon="mdi-account"
            size="20"
            color="white"
          />
          <v-icon
            v-else
            icon="mdi-creation"
            size="20"
            color="primary"
          />
        </v-avatar>
      </div>

      <!-- Message Content Body -->
      <div class="content-col flex-grow-1 min-w-0">
        <!-- Header -->
        <div class="d-flex align-center justify-space-between mb-1">
          <div class="d-flex align-center ga-2">
            <span class="text-caption font-weight-bold letter-spacing-wide">
              {{ message.role === 'user' ? 'You' : 'ZDocSpider Assistant' }}
            </span>
            <span class="text-caption text-medium-emphasis">
              {{ formattedTime }}
            </span>
          </div>

          <!-- Action buttons -->
          <div class="message-actions d-flex ga-1">
            <v-tooltip text="Copy to clipboard" location="top">
              <template #activator="{ props }">
                <v-btn
                  v-bind="props"
                  icon="mdi-content-copy"
                  variant="text"
                  size="x-small"
                  density="comfortable"
                  color="secondary"
                  @click="copyContent"
                />
              </template>
            </v-tooltip>
          </div>
        </div>

        <!-- Rendered Text or Markdown -->
        <div v-if="message.role === 'user'" class="user-text text-body-1">
          {{ message.content }}
        </div>

        <div v-else class="assistant-content">
          <div
            v-if="renderedMarkdown"
            class="markdown-body"
            v-html="renderedMarkdown"
          />

          <!-- Generating Typing Indicator -->
          <div v-if="message.isGenerating && !message.content" class="d-flex align-center ga-2 py-2 text-medium-emphasis">
            <v-progress-circular indeterminate size="16" width="2" color="primary" />
            <span class="text-caption">Analyzing document knowledge...</span>
          </div>

          <span v-if="message.isGenerating && message.content" class="typing-cursor"></span>

          <!-- Referenced Document Sources Cards -->
          <div v-if="message.sources && message.sources.length > 0" class="sources-section mt-4 pt-3 border-t">
            <div class="d-flex align-center justify-space-between mb-2">
              <span class="text-caption font-weight-bold text-medium-emphasis d-flex align-center ga-1">
                <v-icon icon="mdi-file-document-check-outline" size="14" color="primary" />
                Retrieved Document Sources ({{ message.sources.length }})
              </span>
              <v-btn
                variant="plain"
                size="x-small"
                density="compact"
                :append-icon="showSources ? 'mdi-chevron-up' : 'mdi-chevron-down'"
                @click="showSources = !showSources"
              >
                {{ showSources ? 'Hide' : 'Show' }}
              </v-btn>
            </div>

            <v-expand-transition>
              <div v-show="showSources" class="d-flex flex-column ga-2">
                <v-card
                  v-for="(source, idx) in message.sources"
                  :key="idx"
                  variant="outlined"
                  class="source-card pa-3 rounded-lg"
                  density="compact"
                >
                  <div class="d-flex align-center justify-space-between mb-1">
                    <div class="d-flex align-center ga-2 text-truncate">
                      <v-icon icon="mdi-file-pdf-box" color="error" size="18" />
                      <span class="text-caption font-weight-bold text-truncate">{{ source.fileName }}</span>
                    </div>
                    <v-chip
                      v-if="source.distance !== undefined"
                      size="x-small"
                      color="primary"
                      variant="tonal"
                      density="compact"
                    >
                      dist: {{ source.distance.toFixed(3) }}
                    </v-chip>
                  </div>
                  <div class="text-caption text-medium-emphasis font-mono text-truncate mb-1">
                    {{ source.filePath }}
                  </div>
                  <div v-if="source.snippet" class="source-snippet text-caption pa-2 rounded bg-surface-variant font-mono">
                    "{{ source.snippet }}"
                  </div>
                </v-card>
              </div>
            </v-expand-transition>
          </div>
        </div>
      </div>
    </div>

    <!-- Copy Snackbar Feedback -->
    <v-snackbar v-model="copied" timeout="1500" location="bottom" color="surface-variant">
      <div class="d-flex align-center ga-2 text-caption">
        <v-icon icon="mdi-check-circle" color="success" size="16" />
        Copied to clipboard
      </div>
    </v-snackbar>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue'
import { marked } from 'marked'
import type { ChatMessage } from '@/types'

const props = defineProps<{
  message: ChatMessage
}>()

const showSources = ref(true)
const copied = ref(false)

const formattedTime = computed(() => {
  const d = new Date(props.message.timestamp)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
})

const renderedMarkdown = computed(() => {
  if (!props.message.content) return ''
  try {
    return marked.parse(props.message.content) as string
  } catch (e) {
    return props.message.content
  }
})

function copyContent() {
  navigator.clipboard.writeText(props.message.content)
  copied.value = true
}
</script>

<style scoped>
.message-wrapper {
  position: relative;
}

.assistant-message {
  background-color: transparent;
}

.user-message {
  background-color: rgba(148, 163, 184, 0.04);
}

.message-actions {
  opacity: 0.2;
  transition: opacity 0.15s ease;
}

.message-wrapper:hover .message-actions {
  opacity: 1;
}

.user-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.border-subtle {
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.letter-spacing-wide {
  letter-spacing: 0.03em;
}

.source-card {
  border-color: rgba(148, 163, 184, 0.2);
  background-color: rgba(148, 163, 184, 0.02);
}

.source-snippet {
  line-height: 1.4;
  opacity: 0.9;
  font-size: 0.78rem !important;
}

.border-t {
  border-top: 1px solid rgba(148, 163, 184, 0.15);
}
</style>
