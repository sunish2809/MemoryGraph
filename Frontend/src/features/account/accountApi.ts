import { api } from '@/lib/api'
import type { PrivacyStatus } from '@/types/api'

export const accountApi = {
  privacy: () => api.get<PrivacyStatus>('/account/privacy'),
  exportArchive: () => api.getBlob('/account/export', { timeout: 0 }),
  deleteAccount: (password: string) =>
    api.delete<void>('/account', { data: { password, confirmation: 'DELETE' } }),
}
