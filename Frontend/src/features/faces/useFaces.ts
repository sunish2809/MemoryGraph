import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { facesApi } from '@/features/faces/facesApi'

export const facesKeys = {
  all: ['faces'] as const,
  review: ['faces', 'review'] as const,
}

export function useFaceReview() {
  return useQuery({
    queryKey: facesKeys.review,
    queryFn: () => facesApi.review(),
  })
}

export function useRejectFaceSuggestion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (faceId: string) => facesApi.rejectSuggestion(faceId),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: facesKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
      ]),
  })
}

export function useIgnoreFace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (faceId: string) => facesApi.ignore(faceId),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: facesKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
      ]),
  })
}

export function useRestoreFace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (faceId: string) => facesApi.restore(faceId),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: facesKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
      ]),
  })
}

export function useIgnoreFaceCluster() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (clusterId: string) => facesApi.ignoreCluster(clusterId),
    onSuccess: (review) => {
      queryClient.setQueryData(facesKeys.review, review)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: facesKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
      ])
    },
  })
}

export function useConfirmFaceCluster() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      clusterId,
      personId,
      displayName,
    }: {
      clusterId: string
      personId?: string
      displayName?: string
    }) => {
      const payload: { personId?: string; displayName?: string } = {}
      if (personId) payload.personId = personId
      if (displayName) payload.displayName = displayName
      return facesApi.confirmCluster(clusterId, payload)
    },
    onSuccess: (review) => {
      queryClient.setQueryData(facesKeys.review, review)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: facesKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
        queryClient.invalidateQueries({ queryKey: ['people'] }),
      ])
    },
  })
}
