import { api } from '@/lib/api'
import type { PlaceDetail, PlaceSummary } from '@/types/api'

export const placesApi = {
  list: () => api.get<PlaceSummary[]>('/places'),
  get: (placeId: string) => api.get<PlaceDetail>(`/places/${placeId}`),
  rename: (placeId: string, displayName: string) =>
    api.patch<PlaceDetail>(`/places/${placeId}`, { displayName }),
  merge: (keepId: string, sourcePlaceId: string) =>
    api.post<PlaceDetail>(`/places/${keepId}/merge`, { sourcePlaceId }),
}
