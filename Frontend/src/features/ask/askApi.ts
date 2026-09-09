import { api } from '@/lib/api'
import type { AskQuota, AskRequest, AskResponse } from '@/types/api'

export const askApi = {
  ask: (payload: AskRequest) => api.post<AskResponse>('/ask', payload),
  quota: () => api.get<AskQuota>('/ask/quota'),
}
