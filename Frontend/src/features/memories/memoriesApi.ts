import { api } from '@/lib/api'
import type {
  FaceDetection,
  LinkedPerson,
  Memory,
  MemoryStats,
  MemorySummary,
  MemoryType,
  Page,
  SearchResult,
  SearchSort,
  Timeline,
} from '@/types/api'

export interface CreateTextMemoryPayload {
  title?: string | undefined
  description?: string | undefined
  content: string
  /** ISO-8601 instant. Omitted means "now". */
  occurredAt?: string | undefined
}

export interface UploadMemoryPayload {
  file: File
  title?: string | undefined
  description?: string | undefined
  occurredAt?: string | undefined
}

export interface UpdateMemoryPayload {
  title?: string | undefined
  description?: string | undefined
  content?: string | undefined
  occurredAt?: string | undefined
}

export interface TimelineQuery {
  /** `YYYY-MM-DD`, inclusive, interpreted in `zone`. */
  from?: string | undefined
  to?: string | undefined
  zone: string
  page?: number | undefined
  size?: number | undefined
}

export interface SearchQuery {
  q?: string | undefined
  types?: MemoryType[] | undefined
  from?: string | undefined
  to?: string | undefined
  zone: string
  sort?: SearchSort | undefined
  personId?: string | undefined
  placeId?: string | undefined
  page?: number | undefined
  size?: number | undefined
}

export const memoriesApi = {
  createText: (payload: CreateTextMemoryPayload) => api.post<Memory>('/memories/text', payload),

  upload: ({ file, title, description, occurredAt }: UploadMemoryPayload, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    if (title) form.append('title', title)
    if (description) form.append('description', description)
    if (occurredAt) form.append('occurredAt', occurredAt)

    return api.postForm<Memory>('/memories/upload', form, onProgress)
  },

  list: (page = 0, size = 20) =>
    api.get<Page<MemorySummary>>('/memories', { params: { page, size } }),

  stats: () => api.get<MemoryStats>('/memories/stats'),

  get: (memoryId: string) => api.get<Memory>(`/memories/${memoryId}`),

  update: (memoryId: string, payload: UpdateMemoryPayload) =>
    api.patch<Memory>(`/memories/${memoryId}`, payload),

  remove: (memoryId: string) => api.delete<void>(`/memories/${memoryId}`),

  tagPerson: (memoryId: string, displayName: string) =>
    api.post<LinkedPerson>(`/memories/${memoryId}/people`, { displayName }),

  untagPerson: (memoryId: string, personId: string) =>
    api.delete<void>(`/memories/${memoryId}/people/${personId}`),

  confirmFace: (memoryId: string, faceId: string, payload: { personId?: string; displayName?: string }) =>
    api.post<FaceDetection>(`/memories/${memoryId}/faces/${faceId}/confirm`, payload),

  clearFace: (memoryId: string, faceId: string) =>
    api.delete<FaceDetection>(`/memories/${memoryId}/faces/${faceId}`),

  timeline: ({ from, to, zone, page = 0, size = 50 }: TimelineQuery) =>
    api.get<Timeline>('/timeline', { params: { from, to, zone, page, size } }),

  deleteDay: (date: string, zone: string) =>
    api.delete<void>(`/timeline/days/${date}`, { params: { zone } }),

  /**
   * `type` is sent repeatedly rather than as `type[]`, which is what Spring binds to a list.
   * The abort signal lets an in-flight request die when the query changes mid-keystroke, so a slow
   * earlier page cannot replace a later one.
   */
  search: (
    { q, types, from, to, zone, sort = 'RELEVANCE', personId, placeId, page = 0, size = 20 }: SearchQuery,
    signal?: AbortSignal,
  ) =>
    api.get<Page<SearchResult>>('/search', {
      params: {
        zone,
        sort,
        page,
        size,
        ...(q ? { q } : {}),
        ...(types && types.length > 0 ? { type: types } : {}),
        ...(from ? { from } : {}),
        ...(to ? { to } : {}),
        ...(personId ? { personId } : {}),
        ...(placeId ? { placeId } : {}),
      },
      paramsSerializer: { indexes: null },
      ...(signal ? { signal } : {}),
    }),

  /**
   * Media is fetched as a blob rather than pointed at with a plain `src`, because every byte is behind
   * an authorisation check and a bearer token cannot travel in an image tag.
   */
  media: (downloadPath: string) => api.getBlob(stripApiPrefix(downloadPath)),
}

/**
 * The backend returns absolute API paths, while the client is already configured with the API base.
 * Trimming the overlap keeps the backend free to own its own URL structure.
 */
function stripApiPrefix(path: string): string {
  const base = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
  return path.startsWith(base) ? path.slice(base.length) : path
}
