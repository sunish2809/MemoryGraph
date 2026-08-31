import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { importsApi } from '@/features/imports/importsApi'
import { memoryKeys } from '@/features/memories/useMemories'
import { peopleKeys } from '@/features/people/usePeople'

const IMPORT_POLL_MS = 1500

export const importKeys = {
  detail: (importId: string) => ['imports', importId] as const,
  list: ['imports', 'list'] as const,
  google: ['integrations', 'google'] as const,
  picker: (sessionId: string) => ['imports', 'picker', sessionId] as const,
}

export function useWhatsAppImport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      file,
      zone,
      onProgress,
    }: {
      file: File
      zone: string
      onProgress?: (percent: number) => void
    }) => importsApi.whatsapp(file, zone, onProgress),
    onSuccess: (job) => {
      queryClient.setQueryData(importKeys.detail(job.id), job)
      void queryClient.invalidateQueries({ queryKey: importKeys.list })
    },
  })
}

export function useGooglePhotosImport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      file,
      zone,
      onProgress,
    }: {
      file: File
      zone: string
      onProgress?: (percent: number) => void
    }) => importsApi.googlePhotos(file, zone, onProgress),
    onSuccess: (job) => {
      queryClient.setQueryData(importKeys.detail(job.id), job)
      void queryClient.invalidateQueries({ queryKey: importKeys.list })
    },
  })
}

export function useGoogleIntegration() {
  return useQuery({
    queryKey: importKeys.google,
    queryFn: () => importsApi.googleStatus(),
  })
}

export function useGoogleAuthorize() {
  return useMutation({
    mutationFn: () => importsApi.googleAuthorize(),
  })
}

export function useGoogleCallback() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ code, state }: { code: string; state: string }) =>
      importsApi.googleCallback(code, state),
    onSuccess: (status) => {
      queryClient.setQueryData(importKeys.google, status)
    },
  })
}

export function useGoogleDisconnect() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => importsApi.googleDisconnect(),
    onSuccess: (status) => {
      queryClient.setQueryData(importKeys.google, status)
    },
  })
}

export function useCreatePickerSession() {
  return useMutation({
    mutationFn: () => importsApi.createPickerSession(),
  })
}

export function usePickerSession(sessionId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: importKeys.picker(sessionId ?? 'none'),
    queryFn: () => importsApi.getPickerSession(sessionId!),
    enabled: Boolean(sessionId) && enabled,
    refetchInterval: (query) => {
      if (!enabled || query.state.data?.mediaItemsSet) {
        return false
      }
      return query.state.data?.pollIntervalMs ?? 3_000
    },
  })
}

export function useImportPickerSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, zone }: { sessionId: string; zone: string }) =>
      importsApi.importPickerSession(sessionId, zone),
    onSuccess: (job) => {
      queryClient.setQueryData(importKeys.detail(job.id), job)
      void queryClient.invalidateQueries({ queryKey: importKeys.list })
    },
  })
}

export function useImportJob(importId: string | undefined) {
  return useQuery({
    queryKey: importKeys.detail(importId ?? 'none'),
    queryFn: () => importsApi.get(importId!),
    enabled: Boolean(importId),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      // Keep polling through transient errors while the job is still running (or unknown).
      if (!status || status === 'PENDING' || status === 'PROCESSING') {
        return IMPORT_POLL_MS
      }
      return false
    },
    retry: 4,
    retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
  })
}

export function useImportList(pollWhileBusy = false) {
  return useQuery({
    queryKey: importKeys.list,
    queryFn: () => importsApi.list(),
    refetchInterval: pollWhileBusy ? IMPORT_POLL_MS : false,
  })
}

export function useDeleteImport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (importId: string) => importsApi.remove(importId),
    onSuccess: async (_void, importId) => {
      queryClient.removeQueries({ queryKey: importKeys.detail(importId) })
      queryClient.setQueryData(importKeys.list, (current: Awaited<ReturnType<typeof importsApi.list>> | undefined) =>
        current?.filter((job) => job.id !== importId),
      )
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: importKeys.list }),
        queryClient.invalidateQueries({ queryKey: memoryKeys.all }),
        queryClient.invalidateQueries({ queryKey: memoryKeys.stats }),
        queryClient.invalidateQueries({ queryKey: peopleKeys.all }),
      ])
    },
  })
}

export function invalidateMemoriesAfterImport(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: memoryKeys.all })
  void queryClient.invalidateQueries({ queryKey: memoryKeys.stats })
  void queryClient.invalidateQueries({ queryKey: peopleKeys.all })
}
