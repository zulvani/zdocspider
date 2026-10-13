export interface Doc {
  id: string
  fileName: string
  docContent: string
  filePath: string
  checksum: string
  duplicatePaths: string[]
  createdAt?: string
}

export interface DocScanRequest {
  root_path: string
  deep: boolean
}

export interface DocScanResponse {
  message?: string
  rootPath?: string
  deep?: boolean
  totalFilesFound: number
  totalProcessed: number
  totalFailed: number
  totalVectorsIndexed: number
  docs?: Doc[]
}

export interface DocDuplicatePathResponse {
  id?: string
  file_name?: string
  fileName?: string
  filePath?: string
  checksum?: string
  paths?: string[]
  duplicatePaths?: string[]
}

export interface DocDuplicateResponse {
  message?: string
  rootPath?: string
  deep?: boolean
  totalFilesFound: number
  totalProcessed?: number
  totalDuplicates?: number
  totalDuplicatesFound?: number
  totalUniqueFiles?: number
  totalFailed?: number
  docs?: Doc[]
  duplicates?: DocDuplicatePathResponse[]
}

export interface PageResponse<T> {
  content: T[]
  pageNumber: number
  pageSize: number
  totalElements: number
  totalPages: number
  last: boolean
  first: boolean
  empty: boolean
}

export interface VectorSearchResult {
  docId: string
  distance: number
  doc?: Doc
}

export interface DocumentReference {
  docId?: string
  fileName: string
  filePath: string
  distance?: number
  snippet?: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: number
  sources?: DocumentReference[]
  isGenerating?: boolean
  error?: boolean
}

export interface ChatSession {
  id: string
  title: string
  createdAt: number
  updatedAt: number
  messages: ChatMessage[]
}

export interface AppSettings {
  backendUrl: string
  ollamaUrl: string
  selectedModel: string
  systemPrompt: string
  enableRag: boolean
  ragLimit: number
  defaultRootPath: string
  deepScan: boolean
}
