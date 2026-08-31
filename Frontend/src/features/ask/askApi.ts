import { api } from '@/lib/api'
import type { AskRequest, AskResponse } from '@/types/api'

export const askApi = {
  ask: (payload: AskRequest) => api.post<AskResponse>('/ask', payload),
}
