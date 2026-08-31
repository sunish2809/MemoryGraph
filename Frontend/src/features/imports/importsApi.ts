import type { ImportJob } from '@/types/api'
import { api } from '@/lib/api'

export type GoogleIntegrationStatus = {
  configured: boolean
  connected: boolean
}

export type GooglePickerSession = {
  sessionId: string
  pickerUri: string
  pollIntervalMs: number
  mediaItemsSet: boolean
}

export const importsApi = {
  whatsapp: (file: File, zone: string, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    form.append('zone', zone)
    return api.postForm<ImportJob>('/imports/whatsapp', form, onProgress)
  },

  googlePhotos: (file: File, zone: string, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    form.append('zone', zone)
    return api.postForm<ImportJob>('/imports/google-photos', form, onProgress)
  },

  googleStatus: () => api.get<GoogleIntegrationStatus>('/integrations/google'),

  googleAuthorize: () => api.get<{ authorizationUrl: string }>('/integrations/google/authorize'),

  googleCallback: (code: string, state: string) =>
    api.post<GoogleIntegrationStatus>('/integrations/google/callback', { code, state }),

  googleDisconnect: () => api.delete<GoogleIntegrationStatus>('/integrations/google'),

  createPickerSession: () =>
    api.post<GooglePickerSession>('/imports/google-photos/picker/sessions'),

  getPickerSession: (sessionId: string) =>
    api.get<GooglePickerSession>(`/imports/google-photos/picker/sessions/${sessionId}`),

  importPickerSession: (sessionId: string, zone: string) =>
    api.post<ImportJob>(`/imports/google-photos/picker/sessions/${sessionId}/import`, { zone }),

  get: (importId: string) =>
    api.get<ImportJob>(`/imports/${importId}`, { timeout: 90_000 }),

  list: () => api.get<ImportJob[]>('/imports', { timeout: 90_000 }),

  remove: (importId: string) => api.delete<void>(`/imports/${importId}`),
}
