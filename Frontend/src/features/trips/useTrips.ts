import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { tripsApi } from '@/features/trips/tripsApi'

export const tripKeys = {
  all: ['trips'] as const,
  list: ['trips', 'list'] as const,
  detail: (tripId: string) => ['trips', 'detail', tripId] as const,
}

export function useTrips() {
  return useQuery({
    queryKey: tripKeys.list,
    queryFn: () => tripsApi.list(),
  })
}

export function useTrip(tripId: string) {
  return useQuery({
    queryKey: tripKeys.detail(tripId),
    queryFn: () => tripsApi.get(tripId),
    enabled: Boolean(tripId),
  })
}

export function useCreateTrip() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: tripsApi.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tripKeys.all }),
  })
}

export function useUpdateTrip(tripId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: { title?: string; startedAt?: string; endedAt?: string; notes?: string }) =>
      tripsApi.update(tripId, payload),
    onSuccess: (trip) => {
      queryClient.setQueryData(tripKeys.detail(tripId), trip)
      return queryClient.invalidateQueries({ queryKey: tripKeys.all })
    },
  })
}

export function useDeleteTrip() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (tripId: string) => tripsApi.remove(tripId),
    onSuccess: (_void, tripId) => {
      queryClient.removeQueries({ queryKey: tripKeys.detail(tripId) })
      return queryClient.invalidateQueries({ queryKey: tripKeys.all })
    },
  })
}
