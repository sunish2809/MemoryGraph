import { api } from '@/lib/api'
import type { TripDetail, TripsPage } from '@/types/api'

export const tripsApi = {
  list: () => api.get<TripsPage>('/trips'),
  get: (tripId: string) => api.get<TripDetail>(`/trips/${tripId}`),
  create: (payload: { title: string; startedAt: string; endedAt: string; notes?: string }) =>
    api.post<TripDetail>('/trips', payload),
  update: (
    tripId: string,
    payload: { title?: string; startedAt?: string; endedAt?: string; notes?: string },
  ) => api.patch<TripDetail>(`/trips/${tripId}`, payload),
  remove: (tripId: string) => api.delete(`/trips/${tripId}`),
}
