<template>
  <div class="root-setup-container pa-4 pa-md-6 h-100 overflow-y-auto">
    <div class="mx-auto" style="max-width: 900px;">
      <!-- Title Bar -->
      <div class="d-flex align-center justify-space-between mb-4">
        <div>
          <h2 class="text-h5 font-weight-bold d-flex align-center ga-2">
            <v-icon icon="mdi-folder-cog-outline" color="primary" />
            Set Document Root
          </h2>
          <p class="text-body-2 text-medium-emphasis">
            Configure target directory to scan, compute checksums, and generate vector embeddings for PDF files.
          </p>
        </div>

        <v-btn
          variant="tonal"
          prepend-icon="mdi-arrow-left"
          size="small"
          class="rounded-lg"
          @click="$emit('back')"
        >
          Back to Chat
        </v-btn>
      </div>

      <!-- Root Path Configuration Card -->
      <v-card variant="outlined" class="rounded-xl pa-5 mb-5 elevation-1 border-subtle">
        <h3 class="text-subtitle-1 font-weight-bold mb-3 d-flex align-center ga-2">
          <v-icon icon="mdi-folder-search-outline" size="20" color="primary" />
          Target Directory & Scan Options
        </h3>

        <!-- Input Field -->
        <v-text-field
          v-model="rootPath"
          label="Document Root Path"
          placeholder="/path/to/documents or ./docs"
          prepend-inner-icon="mdi-folder-outline"
          variant="outlined"
          density="comfortable"
          class="mb-2 font-mono"
          hint="Specify an absolute directory path or relative path accessible to the backend server"
          persistent-hint
          :disabled="isScanning"
        />

        <!-- Preset Path Suggestions -->
        <div class="d-flex align-center ga-2 mb-4 flex-wrap">
          <span class="text-caption text-medium-emphasis">Quick Presets:</span>
          <v-chip
            v-for="preset in presetPaths"
            :key="preset"
            size="x-small"
            variant="tonal"
            color="secondary"
            class="font-mono cursor-pointer"
            @click="rootPath = preset"
          >
            {{ preset }}
          </v-chip>
        </div>

        <!-- Options -->
        <div class="d-flex align-center justify-space-between flex-wrap ga-4 pt-2 border-t">
          <div class="d-flex align-center">
            <v-switch
              v-model="deepScan"
              color="primary"
              density="compact"
              hide-details
              :disabled="isScanning"
            />
            <div class="ml-2">
              <div class="text-body-2 font-weight-medium">Deep / Recursive Scan</div>
              <div class="text-caption text-medium-emphasis">
                Recursively traverse all subfolders within the document root
              </div>
            </div>
          </div>

          <!-- Action Buttons -->
          <div class="d-flex align-center ga-2">
            <v-btn
              variant="outlined"
              color="secondary"
              prepend-icon="mdi-file-find-outline"
              :loading="isDetectingDuplicates"
              :disabled="isScanning || !rootPath.trim()"
              class="rounded-lg text-none"
              @click="handleDetectDuplicates"
            >
              Detect Duplicates
            </v-btn>

            <v-btn
              variant="flat"
              color="primary"
              prepend-icon="mdi-play-circle-outline"
              :loading="isScanning"
              :disabled="isDetectingDuplicates || !rootPath.trim()"
              class="rounded-lg text-none font-weight-bold px-4"
              @click="handleScan"
            >
              Scan & Extract Documents
            </v-btn>
          </div>
        </div>

        <!-- Scanning Progress Indicator -->
        <v-progress-linear
          v-if="isScanning || isDetectingDuplicates"
          indeterminate
          color="primary"
          rounded
          height="4"
          class="mt-4"
        />
      </v-card>

      <!-- Alert / Status Banner -->
      <v-alert
        v-if="statusMessage"
        :type="statusType"
        variant="tonal"
        class="rounded-xl mb-5"
        closable
        @click:close="statusMessage = ''"
      >
        <div class="font-weight-medium">{{ statusMessage }}</div>
        <div v-if="statusDetails" class="text-caption mt-1">{{ statusDetails }}</div>
      </v-alert>

      <!-- Scan Statistics Results -->
      <div v-if="scanResult" class="mb-5">
        <h3 class="text-subtitle-1 font-weight-bold mb-3 d-flex align-center ga-2">
          <v-icon icon="mdi-chart-box-outline" size="20" color="success" />
          Scan Execution Summary
        </h3>

        <v-row dense class="mb-4">
          <v-col cols="6" sm="3">
            <v-card variant="tonal" color="primary" class="pa-3 text-center rounded-xl">
              <div class="text-caption text-medium-emphasis">Files Found</div>
              <div class="text-h5 font-weight-bold">{{ scanResult.totalFilesFound }}</div>
            </v-card>
          </v-col>
          <v-col cols="6" sm="3">
            <v-card variant="tonal" color="success" class="pa-3 text-center rounded-xl">
              <div class="text-caption text-medium-emphasis">Processed</div>
              <div class="text-h5 font-weight-bold">{{ scanResult.totalProcessed }}</div>
            </v-card>
          </v-col>
          <v-col cols="6" sm="3">
            <v-card variant="tonal" color="error" class="pa-3 text-center rounded-xl">
              <div class="text-caption text-medium-emphasis">Failed</div>
              <div class="text-h5 font-weight-bold">{{ scanResult.totalFailed }}</div>
            </v-card>
          </v-col>
          <v-col cols="6" sm="3">
            <v-card variant="tonal" color="info" class="pa-3 text-center rounded-xl">
              <div class="text-caption text-medium-emphasis">Vectors Indexed</div>
              <div class="text-h5 font-weight-bold">{{ scanResult.totalVectorsIndexed }}</div>
            </v-card>
          </v-col>
        </v-row>

        <!-- Processed Documents Preview List -->
        <v-card
          v-if="scanResult.docs && scanResult.docs.length > 0"
          variant="outlined"
          class="rounded-xl pa-4 border-subtle"
        >
          <div class="d-flex align-center justify-space-between mb-3">
            <span class="text-subtitle-2 font-weight-bold">
              Indexed Documents ({{ scanResult.docs.length }})
            </span>
            <v-btn
              variant="text"
              color="primary"
              size="small"
              append-icon="mdi-arrow-right"
              @click="$emit('openDocList')"
            >
              Go to Document List
            </v-btn>
          </div>

          <v-list lines="two" density="compact" class="bg-transparent">
            <v-list-item
              v-for="doc in scanResult.docs"
              :key="doc.id"
              class="border rounded-lg mb-2 pa-3"
            >
              <template #prepend>
                <v-icon icon="mdi-file-pdf-box" color="error" size="30" class="mr-3" />
              </template>

              <v-list-item-title class="font-weight-bold text-body-2">
                {{ doc.fileName }}
              </v-list-item-title>

              <v-list-item-subtitle class="font-mono text-caption text-medium-emphasis">
                {{ doc.filePath }}
              </v-list-item-subtitle>

              <template #append>
                <v-btn
                  size="small"
                  variant="outlined"
                  color="primary"
                  class="rounded-lg text-none"
                  @click="previewDoc(doc)"
                >
                  Preview Text
                </v-btn>
              </template>
            </v-list-item>
          </v-list>
        </v-card>
      </div>

      <!-- Duplicate Detection Summary -->
      <div v-if="duplicateResult" class="mb-5">
        <h3 class="text-subtitle-1 font-weight-bold mb-3 d-flex align-center ga-2">
          <v-icon icon="mdi-content-duplicate" size="20" color="warning" />
          Duplicate Detection Results
        </h3>

        <v-card variant="outlined" class="rounded-xl pa-4 border-subtle">
          <div class="d-flex align-center justify-space-between mb-3">
            <div class="text-body-2">
              Found <strong>{{ (duplicateResult.totalDuplicates ?? duplicateResult.totalDuplicatesFound ?? 0) }}</strong> duplicate files out of
              <strong>{{ duplicateResult.totalFilesFound }}</strong> scanned files.
            </div>
            <v-chip
              :color="(duplicateResult.totalDuplicates ?? duplicateResult.totalDuplicatesFound ?? 0) > 0 ? 'warning' : 'success'"
              size="small"
              variant="tonal"
            >
              {{ (duplicateResult.totalDuplicates ?? duplicateResult.totalDuplicatesFound ?? 0) > 0 ? 'Duplicates Detected' : 'All Unique' }}
            </v-chip>
          </div>

          <div v-if="duplicateResult.duplicates && duplicateResult.duplicates.length > 0">
            <v-card
              v-for="(dup, idx) in duplicateResult.duplicates"
              :key="idx"
              variant="tonal"
              color="warning"
              class="pa-3 mb-2 rounded-lg"
            >
              <div class="font-weight-bold text-body-1 mb-1 d-flex align-center ga-2">
                <v-icon icon="mdi-file-pdf-box" color="error" />
                {{ getDuplicateFileName(dup) }}
              </div>
              <div class="text-caption font-mono mb-1">
                <strong>Checksum:</strong> {{ dup.checksum }}
              </div>
              <div v-if="getDuplicatePaths(dup).length > 0" class="text-caption">
                <strong>Duplicate file locations ({{ getDuplicatePaths(dup).length }}):</strong>
                <ul class="pl-4 mt-1">
                  <li v-for="(p, pIdx) in getDuplicatePaths(dup)" :key="pIdx" class="font-mono">
                    {{ p }}
                  </li>
                </ul>
              </div>
            </v-card>
          </div>
        </v-card>
      </div>

      <!-- Preview Modal Dialog -->
      <v-dialog v-model="previewDialog" max-width="700">
        <v-card class="rounded-xl pa-4">
          <v-card-title class="d-flex align-center justify-space-between">
            <div class="d-flex align-center ga-2 text-truncate">
              <v-icon icon="mdi-file-document-outline" color="primary" />
              <span class="text-truncate">{{ selectedDoc?.fileName }}</span>
            </div>
            <v-btn icon="mdi-close" variant="text" size="small" @click="previewDialog = false" />
          </v-card-title>
          <v-divider class="my-2" />
          <v-card-text>
            <div class="text-caption text-medium-emphasis mb-2 font-mono">
              {{ selectedDoc?.filePath }}
            </div>
            <div class="pa-3 rounded bg-surface-variant font-mono text-body-2 overflow-y-auto" style="max-height: 400px; white-space: pre-wrap;">
              {{ selectedDoc?.docContent }}
            </div>
          </v-card-text>
        </v-card>
      </v-dialog>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { ApiService, loadSettings, saveSettings } from '@/services/api'
import type { Doc, DocScanResponse, DocDuplicateResponse } from '@/types'

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'openDocList'): void
}>()

const rootPath = ref('./docs')
const deepScan = ref(true)
const isScanning = ref(false)
const isDetectingDuplicates = ref(false)

const statusMessage = ref('')
const statusDetails = ref('')
const statusType = ref<'success' | 'error' | 'info'>('info')

const scanResult = ref<DocScanResponse | null>(null)
const duplicateResult = ref<DocDuplicateResponse | null>(null)

const previewDialog = ref(false)
const selectedDoc = ref<Doc | null>(null)

const presetPaths = [
  './docs',
  './sample-docs',
  '/Users/aguszulvani/Documents',
  '/var/data/documents',
]

onMounted(() => {
  const settings = loadSettings()
  if (settings.defaultRootPath) {
    rootPath.value = settings.defaultRootPath
  }
  deepScan.value = settings.deepScan ?? true
})

async function handleScan() {
  if (!rootPath.value.trim()) return

  isScanning.value = true
  statusMessage.value = ''
  duplicateResult.value = null

  // Save root path in preferences
  const settings = loadSettings()
  settings.defaultRootPath = rootPath.value.trim()
  settings.deepScan = deepScan.value
  saveSettings(settings)

  try {
    const res = await ApiService.scanDocuments(rootPath.value.trim(), deepScan.value)
    scanResult.value = res
    statusType.value = 'success'
    statusMessage.value = res.message || 'Documents scanned and indexed successfully!'
    statusDetails.value = `Processed ${res.totalProcessed} files, generated ${res.totalVectorsIndexed} vector embeddings.`
  } catch (err: any) {
    statusType.value = 'error'
    statusMessage.value = 'Document scan failed.'
    statusDetails.value = err.message || 'Please check if path exists and backend server is running.'
  } finally {
    isScanning.value = false
  }
}

async function handleDetectDuplicates() {
  if (!rootPath.value.trim()) return

  isDetectingDuplicates.value = true
  statusMessage.value = ''
  scanResult.value = null

  try {
    const res = await ApiService.detectDuplicates(rootPath.value.trim(), deepScan.value)
    duplicateResult.value = res
    statusType.value = 'info'
    statusMessage.value = res.message || 'Duplicate file scan completed.'
    statusDetails.value = `Found ${res.totalDuplicates} duplicates out of ${res.totalFilesFound} scanned files.`
  } catch (err: any) {
    statusType.value = 'error'
    statusMessage.value = 'Duplicate detection failed.'
    statusDetails.value = err.message || 'Unknown error occurred.'
  } finally {
    isDetectingDuplicates.value = false
  }
}

function previewDoc(doc: Doc) {
  selectedDoc.value = doc
  previewDialog.value = true
}

function getDuplicateFileName(dup: any): string {
  if (dup.fileName && dup.fileName.trim()) return dup.fileName
  if (dup.file_name && dup.file_name.trim()) return dup.file_name
  const allPaths = getDuplicatePaths(dup)
  if (allPaths.length > 0) {
    const fromPath = allPaths[0].split(/[/\\]/).pop()
    if (fromPath) return fromPath
  }
  return 'Unnamed Document'
}

function getDuplicatePaths(dup: any): string[] {
  if (dup.paths && Array.isArray(dup.paths) && dup.paths.length > 0) {
    return dup.paths
  }
  if (dup.duplicatePaths && Array.isArray(dup.duplicatePaths) && dup.duplicatePaths.length > 0) {
    return dup.duplicatePaths
  }
  if (dup.filePath) {
    return [dup.filePath]
  }
  return []
}
</script>

<style scoped>
.root-setup-container {
  background-color: rgb(var(--v-theme-background));
}

.border-subtle {
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.border-t {
  border-top: 1px solid rgba(148, 163, 184, 0.15);
}
</style>
