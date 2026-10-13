import type {
  Doc,
  DocScanRequest,
  DocScanResponse,
  DocDuplicateResponse,
  DocDuplicatePathResponse,
  PageResponse,
  VectorSearchResult,
  AppSettings,
  DocumentReference,
} from '@/types'

const DEFAULT_SETTINGS: AppSettings = {
  backendUrl: 'http://localhost:8080',
  ollamaUrl: 'http://localhost:11434',
  selectedModel: 'llama3.2',
  systemPrompt: 'You are a helpful and precise document assistant. Use the provided document excerpts to accurately answer the user questions. If the answer cannot be found in the documents, state it clearly.',
  enableRag: true,
  ragLimit: 4,
  defaultRootPath: './docs',
  deepScan: true,
}

export function loadSettings(): AppSettings {
  try {
    const raw = localStorage.getItem('zdocspider_settings')
    if (raw) {
      return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) }
    }
  } catch (e) {
    console.error('Failed to load settings:', e)
  }
  return { ...DEFAULT_SETTINGS }
}

export function saveSettings(settings: AppSettings): void {
  try {
    localStorage.setItem('zdocspider_settings', JSON.stringify(settings))
  } catch (e) {
    console.error('Failed to save settings:', e)
  }
}

/**
 * Fallback mock documents used when backend is offline
 */
export const MOCK_DOCS: Doc[] = [
  {
    id: 'b12a8491-7294-4b53-a5bc-4830113c01a2',
    fileName: 'API_Architecture_Spec_v2.pdf',
    docContent: 'ZDocSpider Architecture Specification. The system provides high-speed PDF ingestion, sha256 checksum verification, and vector embedding indexing via sqlite-vec extension (vec0). All embeddings are 384-dimensional generated via sentence-transformers/all-MiniLM-L6-v2 ONNX transformer model.',
    filePath: '/var/data/documents/spec/API_Architecture_Spec_v2.pdf',
    checksum: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    duplicatePaths: ['/var/data/backup/API_Architecture_Spec_v2_copy.pdf'],
    createdAt: new Date(Date.now() - 3600000 * 24).toISOString(),
  },
  {
    id: 'c83d9502-8305-4c64-b6cd-5941224d12b3',
    fileName: 'Quarterly_Financial_Report_Q3.pdf',
    docContent: 'Quarterly Financial Summary Q3 2026. Total revenue reached $4.2M with net operating margin of 28.5%. Cloud infrastructure expenses were reduced by 14% through local SQLite vector indexing optimizations. Document management overhead decreased by 45%.',
    filePath: '/var/data/documents/finance/Quarterly_Financial_Report_Q3.pdf',
    checksum: '5d41402abc4b2a76b9719d911017c592',
    duplicatePaths: [],
    createdAt: new Date(Date.now() - 3600000 * 12).toISOString(),
  },
  {
    id: 'd94e0613-9416-4d75-c7de-6052335e23c4',
    fileName: 'Employee_Handbook_2026.pdf',
    docContent: 'Company Employee Policy Handbook. Section 4: Remote work protocol and information security guidelines. All confidential PDFs must be cataloged with checksums to prevent duplicate file leaks. Document root directories must follow standard repository structures.',
    filePath: '/var/data/documents/hr/Employee_Handbook_2026.pdf',
    checksum: '7d793037a0760186574b0282f2f435e7',
    duplicatePaths: [
      '/var/data/documents/onboarding/Employee_Handbook_2026.pdf',
      '/var/data/archive/Employee_Handbook_2026.bak',
    ],
    createdAt: new Date(Date.now() - 3600000 * 48).toISOString(),
  },
]

export class ApiService {
  private static getBaseUrl(): string {
    const settings = loadSettings()
    return settings.backendUrl || 'http://localhost:8080'
  }

  /**
   * Health check Spring Boot backend
   */
  static async checkBackendStatus(): Promise<boolean> {
    try {
      const res = await fetch(`${this.getBaseUrl()}/api/doc?page=0&size=1`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
      })
      return res.ok
    } catch {
      return false
    }
  }

  /**
   * Scan & extract documents from root directory
   */
  static async scanDocuments(rootPath: string, deep: boolean): Promise<DocScanResponse> {
    const payload: DocScanRequest = {
      root_path: rootPath,
      deep,
    }

    try {
      const res = await fetch(`${this.getBaseUrl()}/api/doc/scan`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })

      if (!res.ok) {
        const errorData = await res.json().catch(() => null)
        throw new Error(errorData?.message || `Scan failed with HTTP status ${res.status}`)
      }

      return await res.json()
    } catch (err: any) {
      console.warn('Backend call failed, using mock scan fallback:', err)
      // Provide realistic simulated response if backend is offline
      await new Promise(r => setTimeout(r, 1200))
      return {
        message: `Successfully simulated scanning ${rootPath}`,
        rootPath,
        deep,
        totalFilesFound: 3,
        totalProcessed: 3,
        totalFailed: 0,
        totalVectorsIndexed: 3,
        docs: MOCK_DOCS,
      }
    }
  }

  /**
   * Scan & detect duplicates
   */
  static async detectDuplicates(rootPath: string, deep: boolean): Promise<DocDuplicateResponse> {
    const payload: DocScanRequest = {
      root_path: rootPath,
      deep,
    }

    try {
      const res = await fetch(`${this.getBaseUrl()}/api/doc/duplicate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })

      if (!res.ok) {
        const errorData = await res.json().catch(() => null)
        throw new Error(errorData?.message || `Duplicate detection failed with HTTP status ${res.status}`)
      }

      return await res.json()
    } catch (err: any) {
      console.warn('Backend call failed, using simulated duplicates:', err)
      await new Promise(r => setTimeout(r, 800))
      return {
        message: `Detected duplicates in ${rootPath}`,
        rootPath,
        totalFilesFound: 5,
        totalDuplicates: 2,
        duplicates: [
          {
            id: MOCK_DOCS[0].id,
            fileName: MOCK_DOCS[0].fileName,
            filePath: MOCK_DOCS[0].filePath,
            checksum: MOCK_DOCS[0].checksum,
            duplicatePaths: MOCK_DOCS[0].duplicatePaths,
          },
          {
            id: MOCK_DOCS[2].id,
            fileName: MOCK_DOCS[2].fileName,
            filePath: MOCK_DOCS[2].filePath,
            checksum: MOCK_DOCS[2].checksum,
            duplicatePaths: MOCK_DOCS[2].duplicatePaths,
          },
        ],
      }
    }
  }

  /**
   * Get duplicate documents from database
   */
  static async getDuplicates(): Promise<DocDuplicatePathResponse[]> {
    try {
      const res = await fetch(`${this.getBaseUrl()}/api/doc/duplicate`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
      })

      if (!res.ok) {
        throw new Error(`Failed to fetch duplicates (status ${res.status})`)
      }

      const data = await res.json()
      if (Array.isArray(data)) {
        return data.map((item: any) => {
          const paths: string[] = item.paths || item.duplicatePaths || []
          const rawName = item.fileName || item.file_name
          const filePath = item.filePath || (paths.length > 0 ? paths[0] : '')
          const fileName = rawName || (filePath ? filePath.split(/[/\\]/).pop() : '') || 'Duplicate PDF'

          return {
            id: item.id || '',
            fileName,
            file_name: fileName,
            filePath,
            checksum: item.checksum || '',
            paths,
            duplicatePaths: paths,
          }
        })
      }
      return []
    } catch (err) {
      console.warn('Failed to fetch duplicates, returning mock:', err)
      return MOCK_DOCS
        .filter(d => d.duplicatePaths && d.duplicatePaths.length > 0)
        .map(d => ({
          id: d.id,
          fileName: d.fileName,
          file_name: d.fileName,
          filePath: d.filePath,
          checksum: d.checksum,
          paths: d.duplicatePaths,
          duplicatePaths: d.duplicatePaths,
        }))
    }
  }

  /**
   * Search documents with keyword query & pagination
   */
  static async getDocs(query = '', page = 0, size = 10): Promise<PageResponse<Doc>> {
    const params = new URLSearchParams()
    if (query && query.trim()) {
      params.set('q', query.trim())
    }
    params.set('page', String(page))
    params.set('size', String(size))

    try {
      const res = await fetch(`${this.getBaseUrl()}/api/doc?${params.toString()}`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
      })

      if (!res.ok) {
        throw new Error(`Failed to get docs (status ${res.status})`)
      }

      return await res.json()
    } catch (err) {
      console.warn('Failed to get docs from backend, returning mock:', err)
      const q = query.toLowerCase().trim()
      const filtered = q
        ? MOCK_DOCS.filter(d => d.fileName.toLowerCase().includes(q) || d.docContent.toLowerCase().includes(q))
        : MOCK_DOCS

      return {
        content: filtered,
        pageNumber: page,
        pageSize: size,
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / size) || 1,
        first: page === 0,
        last: true,
        empty: filtered.length === 0,
      }
    }
  }

  /**
   * Semantic vector search
   */
  static async searchVector(query: string, field = 'content', limit = 5): Promise<VectorSearchResult[]> {
    const params = new URLSearchParams({
      q: query,
      field,
      limit: String(limit),
    })

    try {
      const res = await fetch(`${this.getBaseUrl()}/api/doc/vector-search?${params.toString()}`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
      })

      if (!res.ok) {
        throw new Error(`Vector search failed (status ${res.status})`)
      }

      return await res.json()
    } catch (err) {
      console.warn('Backend vector search failed, using fallback:', err)
      return MOCK_DOCS.map((doc, idx) => ({
        docId: doc.id,
        distance: 0.12 + idx * 0.08,
        doc,
      }))
    }
  }

  /**
   * Ollama Health Check
   */
  static async checkOllamaStatus(ollamaUrl: string): Promise<boolean> {
    try {
      const controller = new AbortController()
      const timeoutId = setTimeout(() => controller.abort(), 2000)
      const res = await fetch(`${ollamaUrl}/api/tags`, {
        signal: controller.signal,
      })
      clearTimeout(timeoutId)
      return res.ok
    } catch {
      return false
    }
  }

  /**
   * Fetch available models from Ollama
   */
  static async fetchOllamaModels(ollamaUrl: string): Promise<string[]> {
    try {
      const res = await fetch(`${ollamaUrl}/api/tags`)
      if (res.ok) {
        const data = await res.json()
        if (data?.models && Array.isArray(data.models)) {
          return data.models.map((m: any) => m.name || m.model)
        }
      }
    } catch (err) {
      console.warn('Could not fetch Ollama models:', err)
    }
    return ['llama3.2', 'llama3:8b', 'mistral', 'qwen2.5', 'deepseek-r1:8b', 'phi3']
  }

  /**
   * Execute chat completion via Ollama API or simulated RAG
   */
  static async sendChatMessage(
    messages: { role: string; content: string }[],
    options: {
      ollamaUrl: string
      model: string
      systemPrompt: string
      enableRag: boolean
      onChunk?: (token: string) => void
    }
  ): Promise<{ text: string; sources: DocumentReference[] }> {
    const lastUserMsg = messages.filter(m => m.role === 'user').slice(-1)[0]?.content || ''
    let retrievedDocs: DocumentReference[] = []

    // 1. If RAG is enabled, query document context
    if (options.enableRag && lastUserMsg) {
      try {
        const docPage = await this.getDocs(lastUserMsg, 0, 3)
        if (docPage.content && docPage.content.length > 0) {
          retrievedDocs = docPage.content.map(d => ({
            docId: d.id,
            fileName: d.fileName,
            filePath: d.filePath,
            distance: 0.15,
            snippet: d.docContent.length > 250 ? d.docContent.substring(0, 250) + '...' : d.docContent,
          }))
        } else {
          // Try vector search
          const vResults = await this.searchVector(lastUserMsg, 'content', 3)
          retrievedDocs = vResults.map(vr => ({
            docId: vr.docId,
            fileName: vr.doc?.fileName || `Doc-${vr.docId.slice(0, 8)}`,
            filePath: vr.doc?.filePath || 'indexed-vector',
            distance: vr.distance,
            snippet: vr.doc?.docContent || 'Semantic vector indexed content match.',
          }))
        }
      } catch (e) {
        console.warn('RAG document search failed:', e)
      }
    }

    // 2. Prepare Context Prompt
    let augmentedSystemPrompt = options.systemPrompt
    if (retrievedDocs.length > 0) {
      augmentedSystemPrompt += '\n\nRelevant Document Context:\n' +
        retrievedDocs.map((d, i) => `[Source ${i + 1}] (${d.fileName}):\n${d.snippet}`).join('\n\n')
    }

    const isOllamaUp = await this.checkOllamaStatus(options.ollamaUrl)

    if (isOllamaUp) {
      try {
        const res = await fetch(`${options.ollamaUrl}/api/chat`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model: options.model,
            messages: [
              { role: 'system', content: augmentedSystemPrompt },
              ...messages,
            ],
            stream: true,
          }),
        })

        if (!res.ok) {
          throw new Error(`Ollama error: HTTP ${res.status}`)
        }

        const reader = res.body?.getReader()
        const decoder = new TextDecoder()
        let fullText = ''

        if (reader) {
          while (true) {
            const { done, value } = await reader.read()
            if (done) break
            const chunk = decoder.decode(value, { stream: true })
            const lines = chunk.split('\n').filter(Boolean)
            for (const line of lines) {
              try {
                const parsed = JSON.parse(line)
                if (parsed.message?.content) {
                  fullText += parsed.message.content
                  if (options.onChunk) {
                    options.onChunk(fullText)
                  }
                }
              } catch {}
            }
          }
        }
        return { text: fullText, sources: retrievedDocs }
      } catch (ollamaErr) {
        console.warn('Ollama streaming failed, using fallback synthesizer:', ollamaErr)
      }
    }

    // Fallback AI simulation when Ollama is offline
    const simulatedResponse = this.generateSimulatedResponse(lastUserMsg, retrievedDocs, options.model)
    if (options.onChunk) {
      // Simulate typing stream
      const chunks = simulatedResponse.match(/.{1,8}/g) || [simulatedResponse]
      let accumulated = ''
      for (const chunk of chunks) {
        accumulated += chunk
        options.onChunk(accumulated)
        await new Promise(r => setTimeout(r, 20))
      }
    }

    return { text: simulatedResponse, sources: retrievedDocs }
  }

  private static generateSimulatedResponse(query: string, sources: DocumentReference[], model: string): string {
    const qLower = query.toLowerCase()

    if (qLower.includes('duplicate') || qLower.includes('duplikat')) {
      return `### 🔍 Duplicate File Analysis\n\nBased on the document database checksum verification, multiple duplicate files were detected in the system repository.\n\n` +
        `- **API_Architecture_Spec_v2.pdf**: Duplicate found at \`/var/data/backup/API_Architecture_Spec_v2_copy.pdf\`\n` +
        `- **Employee_Handbook_2026.pdf**: Redundant copies detected in onboarding and archive folders.\n\n` +
        `> **Recommendation**: You can review all duplicate file paths directly in the **List of Docs** menu or trigger a new verification scan via **Set Document Root**.`
    }

    if (qLower.includes('root') || qLower.includes('scan') || qLower.includes('setup') || qLower.includes('folder')) {
      return `### 📁 Document Root Setup Guide\n\nTo scan and index PDF documents:\n\n1. Open **Set Document Root** from the navigation drawer.\n2. Specify your target directory (e.g. \`./docs\` or an absolute path).\n3. Toggle **Deep / Recursive Scan** to traverse sub-directories.\n4. Click **Scan & Extract Documents** to compute checksums and build ONNX 384-dimensional vector embeddings.\n\nOnce completed, all extracted text is instantly searchable and available for RAG conversations!`
    }

    if (sources.length > 0) {
      const topDoc = sources[0]
      return `### 📄 Response based on ${topDoc.fileName}\n\nAccording to the indexed document **${topDoc.fileName}**:\n\n` +
        `> ${topDoc.snippet}\n\n` +
        `**Key Findings:**\n` +
        `- The document contents match your query with high semantic relevance.\n` +
        `- Checksum and file path are registered in the local sqlite database.\n` +
        `- You can click on the source card below to inspect full extracted content.\n\n` +
        `*Model: \`${model}\` (Simulated document retrieval synthesis - connect Ollama at \`http://localhost:11434\` for live LLM streaming).*`
    }

    return `### 🤖 ZDocSpider Assistant\n\nI received your query: *"**${query}**"*\n\n` +
      `I am ready to assist with:\n` +
      `- Searching inside scanned PDF documents using semantic vector matching\n` +
      `- Detecting file duplicates across various folders\n` +
      `- Summarizing and analyzing reports, specs, and policies\n\n` +
      `💡 *Tip: Head over to **Set Document Root** to index your local PDFs, or verify that your local Ollama instance is running at \`http://localhost:11434\` for live model inference.*`
  }
}
