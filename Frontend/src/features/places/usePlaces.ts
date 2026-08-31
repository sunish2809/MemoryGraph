import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { placesApi } from '@/features/places/placesApi'

export const placeKeys = {
  all: ['places'] as const,
  list: ['places', 'list'] as const,
  detail: (placeId: string) => ['places', 'detail', placeId] as const,
}

export function usePlaces() {
  return useQuery({
    queryKey: placeKeys.list,
    queryFn: () => placesApi.list(),
  })
}

export function usePlace(placeId: string) {
  return useQuery({
    queryKey: placeKeys.detail(placeId),
    queryFn: () => placesApi.get(placeId),
    enabled: Boolean(placeId),
  })
}

export function useRenamePlace(placeId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (displayName: string) => placesApi.rename(placeId, displayName),
    onSuccess: (place) => {
      queryClient.setQueryData(placeKeys.detail(placeId), place)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: placeKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['trips'] }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
      ])
    },
  })
}

export function useMergePlaces(keepId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sourcePlaceId: string) => placesApi.merge(keepId, sourcePlaceId),
    onSuccess: (place, sourcePlaceId) => {
      queryClient.setQueryData(placeKeys.detail(keepId), place)
      queryClient.removeQueries({ queryKey: placeKeys.detail(sourcePlaceId) })
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: placeKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['trips'] }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
      ])
    },
  })
}
