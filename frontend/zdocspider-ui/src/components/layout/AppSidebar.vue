<template>
  <v-navigation-drawer
    v-model="drawer"
    :rail="rail"
    permanent
    class="app-sidebar border-e d-flex flex-column"
    elevation="0"
    width="280"
  >
    <!-- Brand Header -->
    <div class="sidebar-header pa-3 d-flex align-center justify-space-between border-b">
      <div v-if="!rail" class="d-flex align-center ga-2 text-truncate">
        <v-avatar color="primary" size="30" class="elevation-1">
          <v-icon icon="mdi-creation" color="white" size="18" />
        </v-avatar>
        <div>
          <div class="font-weight-bold text-body-1 letter-spacing-tight">ZDocSpider</div>
          <div class="text-caption text-medium-emphasis" style="font-size: 0.7rem !important; line-height: 1;">
            Ollama Doc Intelligence
          </div>
        </div>
      </div>

      <v-avatar v-else color="primary" size="30" class="mx-auto">
        <v-icon icon="mdi-creation" color="white" size="18" />
      </v-avatar>

      <!-- Toggle Rail Collapse Button -->
      <v-btn
        v-if="!rail"
        icon="mdi-chevron-left"
        variant="text"
        size="x-small"
        density="comfortable"
        @click="rail = true"
      />
      <v-btn
        v-else
        icon="mdi-chevron-right"
        variant="text"
        size="x-small"
        density="comfortable"
        class="mt-2 mx-auto"
        @click="rail = false"
      />
    </div>

    <!-- New Chat Button -->
    <div class="pa-3">
      <v-btn
        block
        variant="tonal"
        color="primary"
        prepend-icon="mdi-plus"
        class="rounded-lg text-none font-weight-bold elevation-0"
        :class="{ 'px-0': rail }"
        @click="$emit('newChat')"
      >
        <span v-if="!rail">New Chat</span>
      </v-btn>
    </div>

    <!-- Primary Nav Items -->
    <v-list density="compact" nav class="px-2 py-0">
      <v-list-subheader v-if="!rail" class="text-caption font-weight-bold text-uppercase text-disabled">
        Views & Setup
      </v-list-subheader>

      <!-- Chat Screen -->
      <v-list-item
        :active="currentView === 'chat'"
        rounded="lg"
        color="primary"
        @click="$emit('changeView', 'chat')"
      >
        <template #prepend>
          <v-icon icon="mdi-chat-outline" size="20" />
        </template>
        <v-list-item-title class="font-weight-medium">Chat</v-list-item-title>
      </v-list-item>

      <!-- Set Document Root (Setup Item 1) -->
      <v-list-item
        :active="currentView === 'root-setup'"
        rounded="lg"
        color="primary"
        @click="$emit('changeView', 'root-setup')"
      >
        <template #prepend>
          <v-icon icon="mdi-folder-cog-outline" size="20" />
        </template>
        <v-list-item-title class="font-weight-medium">Set Document Root</v-list-item-title>
      </v-list-item>

      <!-- List of Docs (Setup Item 2) -->
      <v-list-item
        :active="currentView === 'doc-list'"
        rounded="lg"
        color="primary"
        @click="$emit('changeView', 'doc-list')"
      >
        <template #prepend>
          <v-icon icon="mdi-file-document-multiple-outline" size="20" />
        </template>
        <v-list-item-title class="font-weight-medium">List of Docs</v-list-item-title>
      </v-list-item>

      <!-- Settings Configuration Dialog -->
      <v-list-item
        rounded="lg"
        color="primary"
        @click="$emit('openSettings')"
      >
        <template #prepend>
          <v-icon icon="mdi-cog-outline" size="20" />
        </template>
        <v-list-item-title class="font-weight-medium">Settings</v-list-item-title>
      </v-list-item>
    </v-list>

    <v-divider class="my-2" />

    <!-- Chat History List -->
    <div v-if="!rail" class="flex-grow-1 overflow-y-auto px-2">
      <v-list-subheader class="text-caption font-weight-bold text-uppercase text-disabled px-2">
        Recent Chats
      </v-list-subheader>

      <v-list density="compact" nav class="pa-0">
        <v-list-item
          v-for="session in sessions"
          :key="session.id"
          :active="activeSessionId === session.id && currentView === 'chat'"
          rounded="lg"
          class="chat-history-item mb-1 pr-1"
          @click="$emit('selectSession', session.id)"
        >
          <template #prepend>
            <v-icon icon="mdi-message-outline" size="16" class="mr-2" />
          </template>

          <v-list-item-title class="text-caption font-weight-medium text-truncate">
            {{ session.title }}
          </v-list-item-title>

          <template #append>
            <v-btn
              icon="mdi-trash-can-outline"
              size="x-small"
              variant="text"
              color="secondary"
              density="compact"
              class="history-del-btn"
              @click.stop="$emit('deleteSession', session.id)"
            />
          </template>
        </v-list-item>
      </v-list>
    </div>
    <div v-else class="flex-grow-1"></div>

    <!-- Bottom Footer (Theme Switch & Status) -->
    <div class="sidebar-footer pa-3 border-t bg-surface">
      <div v-if="!rail" class="d-flex align-center justify-space-between">
        <div class="d-flex align-center ga-1">
          <v-icon
            :icon="backendConnected ? 'mdi-circle' : 'mdi-circle-outline'"
            size="10"
            :color="backendConnected ? 'success' : 'warning'"
          />
          <span class="text-caption text-medium-emphasis" style="font-size: 0.72rem !important;">
            {{ backendConnected ? 'API Online' : 'Mock Mode' }}
          </span>
        </div>

        <!-- Theme Toggle Button -->
        <v-tooltip :text="theme.global.current.value.dark ? 'Switch to Light theme' : 'Switch to Dark theme'" location="top">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              :icon="theme.global.current.value.dark ? 'mdi-weather-sunny' : 'mdi-weather-night'"
              variant="text"
              size="small"
              density="comfortable"
              @click="toggleTheme"
            />
          </template>
        </v-tooltip>
      </div>

      <div v-else class="d-flex flex-column align-center ga-2">
        <v-tooltip :text="theme.global.current.value.dark ? 'Switch to Light theme' : 'Switch to Dark theme'" location="right">
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              :icon="theme.global.current.value.dark ? 'mdi-weather-sunny' : 'mdi-weather-night'"
              variant="text"
              size="x-small"
              @click="toggleTheme"
            />
          </template>
        </v-tooltip>
      </div>
    </div>
  </v-navigation-drawer>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { useTheme } from 'vuetify'
import type { ChatSession } from '@/types'

defineProps<{
  currentView: 'chat' | 'root-setup' | 'doc-list'
  sessions: ChatSession[]
  activeSessionId: string
  backendConnected: boolean
  ollamaConnected: boolean
}>()

defineEmits<{
  (e: 'changeView', view: 'chat' | 'root-setup' | 'doc-list'): void
  (e: 'newChat'): void
  (e: 'selectSession', id: string): void
  (e: 'deleteSession', id: string): void
  (e: 'openSettings'): void
}>()

const theme = useTheme()
const drawer = ref(true)
const rail = ref(false)

function toggleTheme() {
  const nextTheme = theme.global.current.value.dark ? 'light' : 'dark'
  theme.global.name.value = nextTheme
  try {
    localStorage.setItem('zdocspider_theme', nextTheme)
  } catch (e) {
    // ignore
  }
}
</script>

<style scoped>
.app-sidebar {
  background-color: rgb(var(--v-theme-surface)) !important;
}

.sidebar-header {
  min-height: 52px;
}

.letter-spacing-tight {
  letter-spacing: -0.02em;
}

.chat-history-item .history-del-btn {
  opacity: 0;
  transition: opacity 0.15s ease;
}

.chat-history-item:hover .history-del-btn {
  opacity: 1;
}

.border-b {
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}

.border-e {
  border-right: 1px solid rgba(148, 163, 184, 0.15);
}

.border-t {
  border-top: 1px solid rgba(148, 163, 184, 0.15);
}
</style>
