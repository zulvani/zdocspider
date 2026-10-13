<template>
  <div class="document-list-container pa-4 pa-md-6 h-100 overflow-y-auto">
    <div class="mx-auto" style="max-width: 1100px;">
      <!-- Title & Action Bar -->
      <div class="d-flex align-center justify-space-between mb-4 flex-wrap ga-3">
        <div>
          <h2 class="text-h5 font-weight-bold d-flex align-center ga-2">
            <v-icon icon="mdi-file-document-multiple-outline" color="primary" />
            List of Docs
          </h2>
          <p class="text-body-2 text-medium-emphasis">
            Browse, search, inspect checksums, and view extracted text of all ingested documents.
          </p>
        </div>

        <div class="d-flex align-center ga-2">
          <v-btn
            variant="outlined"
            prepend-icon="mdi-folder-cog-outline"
            size="small"
            class="rounded-lg text-none"
            @click="$emit('openRootSetup')"
          >
            Scan More Docs
          </v-btn>

          <v-btn
            variant="tonal"
            prepend-icon="mdi-arrow-left"
            size="small"
            class="rounded-lg text-none"
            @click="$emit('back')"
          >
            Back to Chat
          </v-btn>
        </div>
      </div>

      <!-- Search & Filters Toolbar -->
      <v-card variant="outlined" class="rounded-xl pa-4 mb-4 elevation-1 border-subtle">
        <v-row dense align="center">
          <!-- Text Search Field -->
          <v-col cols="12" md="6">
            <v-text-field
              v-model="searchQuery"
              placeholder="Search by file name or document content..."
              prepend-inner-icon="mdi-magnify"
              variant="outlined"
              density="compact"
              hide-details
              clearable
              class="rounded-lg"
              @keyup.enter="fetchDocs"
              @click:clear="onClearSearch"
            />
          </v-col>

          <!-- Search Mode Selector -->
          <v-col cols="12" sm="6" md="4">
            <v-btn-toggle
              v-model="activeTab"
              density="compact"
              color="primary"
              mandatory
              class="rounded-lg border w-100 d-flex"
            >
              <v-btn value="all" class="flex-grow-1 text-caption text-none">
                <v-icon start icon="mdi-file-document-outline" size="16" />
                All Docs
              </v-btn>
              <v-btn value="duplicates" class="flex-grow-1 text-caption text-none">
                <v-icon start icon="mdi-content-duplicate" size="16" />
                Duplicates
              </v-btn>
              <v-btn value="vector" class="flex-grow-1 text-caption text-none">
                <v-icon start icon="mdi-vector-polyline" size="16" />
                Vector Search
              </v-btn>
            </v-btn-toggle>
          </v-col>

          <!-- Refresh Button -->
          <v-col cols="12" sm="6" md="2" class="d-flex justify-end">
            <v-btn
              variant="tonal"
              color="secondary"
              density="comfortable"
              prepend-icon="mdi-refresh"
              :loading="loading"
              class="rounded-lg text-none w-100"
              @click="refreshData"
            >
              Refresh
            </v-btn>
          </v-col>
        </v-row>
      </v-card>

      <!-- Main Content Views -->

      <!-- TAB 1: ALL DOCUMENTS -->
      <div v-if="activeTab === 'all'">
        <v-card variant="outlined" class="rounded-xl elevation-1 border-subtle overflow-hidden">
          <div class="px-4 py-3 d-flex align-center justify-space-between border-b bg-surface-variant">
            <span class="text-caption font-weight-bold text-medium-emphasis">
              Total Documents: {{ totalElements }}
            </span>
            <div class="d-flex align-center ga-2">
              <span class="text-caption text-medium-emphasis">Page size:</span>
              <v-select
                v-model="pageSize"
                :items="[5, 10, 20, 50]"
                density="compact"
                variant="plain"
                hide-details
                style="max-width: 70px;"
                @update:model-value="fetchDocs"
              />
            </div>
          </div>

          <!-- Loading Progress -->
          <v-progress-linear v-if="loading" indeterminate color="primary" />

          <!-- Empty State -->
          <div v-if="!loading && docs.length === 0" class="pa-8 text-center">
            <v-icon icon="mdi-file-question-outline" size="48" color="medium-emphasis" class="mb-2" />
            <div class="text-subtitle-1 font-weight-medium">No documents found</div>
            <div class="text-caption text-medium-emphasis mb-4">
              {{ searchQuery ? 'Try a different search query' : 'Set a document root and scan PDF files to begin' }}
            </div>
            <v-btn
              color="primary"
              variant="tonal"
              size="small"
              prepend-icon="mdi-folder-cog-outline"
              @click="$emit('openRootSetup')"
            >
              Set Document Root & Scan
            </v-btn>
          </div>

          <!-- Documents Table / Cards -->
          <div v-else class="documents-table-wrapper">
            <v-table density="comfortable" hover>
              <thead>
                <tr>
                  <th class="font-weight-bold text-caption">FILE NAME</th>
                  <th class="font-weight-bold text-caption">FILE PATH</th>
                  <th class="font-weight-bold text-caption">CHECKSUM</th>
                  <th class="font-weight-bold text-caption text-center">DUPLICATES</th>
                  <th class="font-weight-bold text-caption text-right">ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="doc in docs" :key="doc.id">
                  <!-- File Name -->
                  <td class="font-weight-medium" style="max-width: 240px;">
                    <div class="d-flex align-center ga-2 text-truncate">
                      <v-icon icon="mdi-file-pdf-box" color="error" size="22" class="flex-shrink-0" />
                      <span class="text-truncate">{{ doc.fileName }}</span>
                    </div>
                  </td>

                  <!-- File Path -->
                  <td class="font-mono text-caption text-medium-emphasis text-truncate" style="max-width: 280px;">
                    <span :title="doc.filePath">{{ doc.filePath }}</span>
                  </td>

                  <!-- Checksum -->
                  <td class="font-mono text-caption" style="max-width: 140px;">
                    <v-tooltip :text="doc.checksum" location="top">
                      <template #activator="{ props }">
                        <v-chip v-bind="props" size="x-small" variant="tonal" density="compact">
                          {{ doc.checksum ? doc.checksum.substring(0, 10) + '...' : 'none' }}
                        </v-chip>
                      </template>
                    </v-tooltip>
                  </td>

                  <!-- Duplicates Status -->
                  <td class="text-center">
                    <v-chip
                      v-if="doc.duplicatePaths && doc.duplicatePaths.length > 0"
                      size="x-small"
                      color="warning"
                      variant="flat"
                      class="cursor-pointer"
                      @click="inspectDuplicates(doc)"
                    >
                      {{ doc.duplicatePaths.length }} duplicate{{ doc.duplicatePaths.length > 1 ? 's' : '' }}
                    </v-chip>
                    <v-chip
                      v-else
                      size="x-small"
                      color="success"
                      variant="tonal"
                    >
                      Unique
                    </v-chip>
                  </td>

                  <!-- Actions -->
                  <td class="text-right">
                    <div class="d-flex align-center justify-end ga-1">
                      <v-tooltip text="Preview Extracted Content" location="top">
                        <template #activator="{ props }">
                          <v-btn
                            v-bind="props"
                            icon="mdi-eye-outline"
                            variant="text"
                            size="small"
                            density="comfortable"
                            color="primary"
                            @click="previewDoc(doc)"
                          />
                        </template>
                      </v-tooltip>

                      <v-tooltip text="Ask AI about this Doc" location="top">
                        <template #activator="{ props }">
                          <v-btn
                            v-bind="props"
                            icon="mdi-chat-question-outline"
                            variant="text"
                            size="small"
                            density="comfortable"
                            color="accent"
                            @click="askAboutDoc(doc)"
                          />
                        </template>
                      </v-tooltip>

                      <v-tooltip text="Copy Path" location="top">
                        <template #activator="{ props }">
                          <v-btn
                            v-bind="props"
                            icon="mdi-content-copy"
                            variant="text"
                            size="small"
                            density="comfortable"
                            @click="copyPath(doc.filePath)"
                          />
                        </template>
                      </v-tooltip>
                    </div>
                  </td>
                </tr>
              </tbody>
            </v-table>
          </div>

          <!-- Pagination Footer -->
          <div v-if="totalPages > 1" class="d-flex align-center justify-center pa-3 border-t">
            <v-pagination
              v-model="pageNumberDisplay"
              :length="totalPages"
              total-visible="5"
              density="comfortable"
              size="small"
              @update:model-value="onPageChange"
            />
          </div>
        </v-card>
      </div>

      <!-- TAB 2: DUPLICATES VIEW -->
      <div v-else-if="activeTab === 'duplicates'">
        <v-card variant="outlined" class="rounded-xl pa-4 elevation-1 border-subtle">
          <div class="d-flex align-center justify-space-between mb-4">
            <div>
              <h3 class="text-subtitle-1 font-weight-bold d-flex align-center ga-2">
                <v-icon icon="mdi-content-duplicate" color="warning" />
                Detected Duplicate Files
              </h3>
              <p class="text-caption text-medium-emphasis">
                Documents with matching cryptographic checksums residing in different folder locations.
              </p>
            </div>
          </div>

          <div v-if="loading" class="text-center pa-6">
            <v-progress-circular indeterminate color="primary" />
          </div>

          <div v-else-if="duplicateDocs.length === 0" class="text-center pa-8">
            <v-icon icon="mdi-check-decagram-outline" size="48" color="success" class="mb-2" />
            <div class="text-subtitle-1 font-weight-medium">No Duplicates Found!</div>
            <div class="text-caption text-medium-emphasis">
              All indexed PDF documents have unique checksums across the library.
            </div>
          </div>

          <div v-else class="d-flex flex-column ga-3">
            <v-card
              v-for="(dup, idx) in filteredDuplicateDocs"
              :key="idx"
              variant="outlined"
              class="pa-4 rounded-xl border-subtle duplicate-card elevation-1"
            >
              <!-- Card Header: Prominent File Name & Badges -->
              <div class="d-flex align-center justify-space-between mb-3 flex-wrap ga-2 border-b pb-2">
                <div class="d-flex align-center ga-2 text-truncate" style="max-width: 75%;">
                  <v-icon icon="mdi-file-pdf-box" color="error" size="26" class="flex-shrink-0" />
                  <div class="text-truncate">
                    <span class="font-weight-bold text-subtitle-1 text-truncate d-block">
                      {{ getDuplicateFileName(dup) }}
                    </span>
                    <span class="text-caption text-medium-emphasis">
                      Duplicate PDF Document
                    </span>
                  </div>
                </div>

                <div class="d-flex align-center ga-2">
                  <v-chip size="small" color="warning" variant="flat" class="font-weight-medium">
                    <v-icon start icon="mdi-content-duplicate" size="14" />
                    {{ getDuplicatePaths(dup).length + 1 }} Copies Detected
                  </v-chip>

                  <v-tooltip text="Copy File Name" location="top">
                    <template #activator="{ props }">
                      <v-btn
                        v-bind="props"
                        icon="mdi-content-copy"
                        variant="text"
                        size="small"
                        density="comfortable"
                        @click="copyPath(getDuplicateFileName(dup))"
                      />
                    </template>
                  </v-tooltip>
                </div>
              </div>

              <!-- Checksum Info -->
              <div class="d-flex align-center ga-2 mb-3 flex-wrap">
                <span class="text-caption font-weight-bold text-medium-emphasis">MD5 / SHA-256:</span>
                <v-chip
                  size="small"
                  variant="tonal"
                  color="secondary"
                  class="font-mono cursor-pointer"
                  @click="copyPath(dup.checksum || '')"
                >
                  <v-icon start icon="mdi-fingerprint" size="14" />
                  {{ dup.checksum || 'No checksum' }}
                </v-chip>
                <span class="text-caption text-disabled">(click to copy)</span>
              </div>

              <!-- Primary Path if known -->
              <div v-if="dup.filePath" class="mb-2">
                <div class="text-caption font-weight-bold text-medium-emphasis mb-1">
                  PRIMARY CANONICAL LOCATION:
                </div>
                <div class="d-flex align-center justify-space-between pa-2 rounded bg-surface-variant font-mono text-caption">
                  <span class="text-truncate">{{ dup.filePath }}</span>
                  <v-btn
                    icon="mdi-content-copy"
                    variant="text"
                    size="x-small"
                    density="compact"
                    @click="copyPath(dup.filePath)"
                  />
                </div>
              </div>

              <!-- Duplicate Paths List -->
              <div v-if="getDuplicatePaths(dup).length > 0">
                <div class="text-caption font-weight-bold text-medium-emphasis mb-1">
                  DUPLICATE FILE LOCATIONS ({{ getDuplicatePaths(dup).length }}):
                </div>
                <v-list density="compact" class="bg-surface-variant rounded-lg pa-1">
                  <v-list-item
                    v-for="(path, pIdx) in getDuplicatePaths(dup)"
                    :key="pIdx"
                    class="font-mono text-caption rounded mb-1"
                  >
                    <template #prepend>
                      <v-icon icon="mdi-folder-alert-outline" size="16" color="warning" class="mr-2" />
                    </template>
                    <span class="text-truncate">{{ path }}</span>
                    <template #append>
                      <v-btn
                        icon="mdi-content-copy"
                        variant="text"
                        size="x-small"
                        density="compact"
                        @click="copyPath(path)"
                      />
                    </template>
                  </v-list-item>
                </v-list>
              </div>

              <!-- Action Footer -->
              <div class="d-flex align-center justify-end ga-2 pt-3 mt-2 border-t">
                <v-btn
                  variant="tonal"
                  color="accent"
                  size="small"
                  prepend-icon="mdi-chat-outline"
                  class="rounded-lg text-none"
                  @click="askAboutDuplicate(dup)"
                >
                  Ask AI About This File
                </v-btn>
              </div>
            </v-card>
          </div>
        </v-card>
      </div>

      <!-- TAB 3: VECTOR SEARCH VIEW -->
      <div v-else-if="activeTab === 'vector'">
        <v-card variant="outlined" class="rounded-xl pa-4 elevation-1 border-subtle">
          <h3 class="text-subtitle-1 font-weight-bold mb-2 d-flex align-center ga-2">
            <v-icon icon="mdi-vector-polyline" color="primary" />
            Semantic Vector Search (sqlite-vec)
          </h3>
          <p class="text-caption text-medium-emphasis mb-4">
            Test cosine distance vector search using 384-dimensional ONNX transformer embeddings.
          </p>

          <div class="d-flex ga-2 mb-4">
            <v-text-field
              v-model="vectorQuery"
              placeholder="Enter a natural language semantic query..."
              variant="outlined"
              density="compact"
              hide-details
              @keyup.enter="handleVectorSearch"
            />
            <v-btn
              color="primary"
              variant="flat"
              prepend-icon="mdi-magnify"
              :loading="loadingVector"
              class="rounded-lg text-none"
              @click="handleVectorSearch"
            >
              Search Vectors
            </v-btn>
          </div>

          <div v-if="vectorResults.length > 0" class="d-flex flex-column ga-2">
            <v-card
              v-for="(vr, vIdx) in vectorResults"
              :key="vIdx"
              variant="outlined"
              class="pa-3 rounded-xl border-subtle"
            >
              <div class="d-flex align-center justify-space-between mb-1">
                <div class="font-weight-bold text-body-2 d-flex align-center ga-2">
                  <v-icon icon="mdi-file-document" color="primary" size="18" />
                  {{ vr.doc?.fileName || 'Doc ' + vr.docId }}
                </div>
                <v-chip size="x-small" color="primary" variant="tonal">
                  distance: {{ vr.distance.toFixed(4) }}
                </v-chip>
              </div>
              <div class="text-caption font-mono text-medium-emphasis mb-2">
                {{ vr.doc?.filePath }}
              </div>
              <div class="text-caption pa-2 rounded bg-surface-variant font-mono">
                {{ vr.doc?.docContent?.substring(0, 280) }}...
              </div>
            </v-card>
          </div>
        </v-card>
      </div>

      <!-- Preview Text Modal Dialog -->
      <v-dialog v-model="previewDialog" max-width="800">
        <v-card class="rounded-xl pa-4">
          <v-card-title class="d-flex align-center justify-space-between pb-2">
            <div class="d-flex align-center ga-2 text-truncate">
              <v-icon icon="mdi-file-pdf-box" color="error" />
              <span class="text-truncate font-weight-bold">{{ activeDoc?.fileName }}</span>
            </div>
            <v-btn icon="mdi-close" variant="text" size="small" @click="previewDialog = false" />
          </v-card-title>

          <v-divider />

          <v-card-text class="pt-3">
            <div class="mb-3 d-flex flex-wrap ga-2 text-caption">
              <v-chip size="small" variant="tonal" color="secondary" class="font-mono">
                Path: {{ activeDoc?.filePath }}
              </v-chip>
              <v-chip size="small" variant="tonal" color="info" class="font-mono">
                Checksum: {{ activeDoc?.checksum }}
              </v-chip>
            </div>

            <div class="text-subtitle-2 font-weight-bold mb-1">Extracted Text Content:</div>
            <div class="content-preview-box pa-3 rounded-lg bg-surface-variant font-mono text-body-2">
              {{ activeDoc?.docContent }}
            </div>
          </v-card-text>

          <v-card-actions class="d-flex justify-space-between px-4">
            <v-btn
              variant="tonal"
              color="primary"
              prepend-icon="mdi-chat-outline"
              @click="askAboutDoc(activeDoc!)"
            >
              Ask AI about this document
            </v-btn>
            <v-btn variant="text" @click="previewDialog = false">
              Close
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>

      <!-- Duplicates Inspector Modal Dialog -->
      <v-dialog v-model="duplicatesDialog" max-width="650">
        <v-card class="rounded-xl pa-4">
          <v-card-title class="d-flex align-center justify-space-between pb-2">
            <div class="d-flex align-center ga-2 text-truncate">
              <v-icon icon="mdi-alert-circle-outline" color="warning" />
              <span>Duplicate File Report</span>
            </div>
            <v-btn icon="mdi-close" variant="text" size="small" @click="duplicatesDialog = false" />
          </v-card-title>
          <v-divider />
          <v-card-text class="pt-3">
            <div class="font-weight-bold text-body-1 mb-2">
              {{ activeDoc?.fileName }}
            </div>
            <div class="text-caption font-mono mb-2">
              Primary: {{ activeDoc?.filePath }}
            </div>
            <div class="text-caption font-mono mb-4 text-medium-emphasis">
              SHA256 Checksum: {{ activeDoc?.checksum }}
            </div>

            <div class="font-weight-bold text-caption text-medium-emphasis mb-1">
              REDUNDANT PATHS:
            </div>
            <v-list density="compact" class="border rounded-lg">
              <v-list-item
                v-for="(p, i) in activeDoc?.duplicatePaths"
                :key="i"
                class="font-mono text-caption"
              >
                <template #prepend>
                  <v-icon icon="mdi-folder-alert" color="warning" size="18" class="mr-2" />
                </template>
                {{ p }}
              </v-list-item>
            </v-list>
          </v-card-text>
        </v-card>
      </v-dialog>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ApiService } from '@/services/api'
import type { Doc, DocDuplicatePathResponse, VectorSearchResult } from '@/types'

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'openRootSetup'): void
  (e: 'askAboutDoc', doc: Doc): void
}>()

const activeTab = ref<'all' | 'duplicates' | 'vector'>('all')
const searchQuery = ref('')
const loading = ref(false)
const docs = ref<Doc[]>([])
const duplicateDocs = ref<DocDuplicatePathResponse[]>([])

const pageNumber = ref(0)
const pageSize = ref(10)
const totalElements = ref(0)
const totalPages = ref(1)

// Computed page for v-pagination (1-indexed)
const pageNumberDisplay = computed({
  get: () => pageNumber.value + 1,
  set: (val: number) => {
    pageNumber.value = val - 1
  },
})

// Vector Search
const vectorQuery = ref('')
const loadingVector = ref(false)
const vectorResults = ref<VectorSearchResult[]>([])

// Dialogs
const previewDialog = ref(false)
const duplicatesDialog = ref(false)
const activeDoc = ref<Doc | null>(null)

onMounted(() => {
  fetchDocs()
})

watch(activeTab, (tab) => {
  if (tab === 'duplicates') {
    fetchDuplicates()
  } else if (tab === 'all') {
    fetchDocs()
  }
})

async function fetchDocs() {
  loading.value = true
  try {
    const res = await ApiService.getDocs(searchQuery.value, pageNumber.value, pageSize.value)
    docs.value = res.content || []
    totalElements.value = res.totalElements || 0
    totalPages.value = res.totalPages || 1
  } catch (err) {
    console.error('Failed to fetch docs:', err)
  } finally {
    loading.value = false
  }
}

async function fetchDuplicates() {
  loading.value = true
  try {
    const res = await ApiService.getDuplicates()
    duplicateDocs.value = res || []
  } catch (err) {
    console.error('Failed to fetch duplicates:', err)
  } finally {
    loading.value = false
  }
}

async function handleVectorSearch() {
  if (!vectorQuery.value.trim()) return
  loadingVector.value = true
  try {
    const results = await ApiService.searchVector(vectorQuery.value.trim(), 'content', 5)
    vectorResults.value = results
  } catch (err) {
    console.error('Vector search error:', err)
  } finally {
    loadingVector.value = false
  }
}

function onClearSearch() {
  searchQuery.value = ''
  pageNumber.value = 0
  fetchDocs()
}

function refreshData() {
  if (activeTab.value === 'duplicates') {
    fetchDuplicates()
  } else {
    fetchDocs()
  }
}

function onPageChange(newPage: number) {
  pageNumber.value = newPage - 1
  fetchDocs()
}

function previewDoc(doc: Doc) {
  activeDoc.value = doc
  previewDialog.value = true
}

const filteredDuplicateDocs = computed(() => {
  const q = searchQuery.value.toLowerCase().trim()
  if (!q) return duplicateDocs.value
  return duplicateDocs.value.filter(dup => {
    const name = getDuplicateFileName(dup).toLowerCase()
    const cs = (dup.checksum || '').toLowerCase()
    const paths = getDuplicatePaths(dup).join(' ').toLowerCase()
    const primary = (dup.filePath || '').toLowerCase()
    return name.includes(q) || cs.includes(q) || paths.includes(q) || primary.includes(q)
  })
})

function getDuplicateFileName(dup: DocDuplicatePathResponse): string {
  if (dup.fileName && dup.fileName.trim()) return dup.fileName
  if (dup.file_name && dup.file_name.trim()) return dup.file_name
  const allPaths = getDuplicatePaths(dup)
  if (allPaths.length > 0) {
    const fromPath = allPaths[0].split(/[/\\]/).pop()
    if (fromPath) return fromPath
  }
  return 'Unnamed Document'
}

function getDuplicatePaths(dup: DocDuplicatePathResponse): string[] {
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

function askAboutDuplicate(dup: DocDuplicatePathResponse) {
  const fileName = getDuplicateFileName(dup)
  emit('askAboutDoc', {
    id: dup.id || '',
    fileName: fileName,
    filePath: dup.filePath || getDuplicatePaths(dup)[0] || '',
    checksum: dup.checksum || '',
    docContent: `Duplicate file ${fileName} detected at locations: ${getDuplicatePaths(dup).join(', ')}. Checksum: ${dup.checksum}`,
    duplicatePaths: getDuplicatePaths(dup),
  })
}

function inspectDuplicates(doc: Doc) {
  activeDoc.value = doc
  duplicatesDialog.value = true
}

function askAboutDoc(doc: Doc) {
  previewDialog.value = false
  emit('askAboutDoc', doc)
}

function copyPath(path: string) {
  navigator.clipboard.writeText(path)
}
</script>

<style scoped>
.document-list-container {
  background-color: rgb(var(--v-theme-background));
}

.border-subtle {
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.border-b {
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}

.border-t {
  border-top: 1px solid rgba(148, 163, 184, 0.15);
}

.content-preview-box {
  max-height: 450px;
  overflow-y: auto;
  white-space: pre-wrap;
  line-height: 1.5;
}
</style>
