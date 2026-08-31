import { api } from '@/lib/api'
import type { FaceDetection, FaceReview } from '@/types/api'

export const facesApi = {
  review: () => api.get<FaceReview>('/faces/review'),

  rejectSuggestion: (faceId: string) => api.post<FaceDetection>(`/faces/${faceId}/reject-suggestion`),

  ignore: (faceId: string) => api.post<FaceDetection>(`/faces/${faceId}/ignore`),

  restore: (faceId: string) => api.post<FaceDetection>(`/faces/${faceId}/restore`),

  confirmCluster: (clusterId: string, payload: { personId?: string; displayName?: string }) =>
    api.post<FaceReview>(`/faces/clusters/${clusterId}/confirm`, payload),

  ignoreCluster: (clusterId: string) => api.post<FaceReview>(`/faces/clusters/${clusterId}/ignore`),
}
