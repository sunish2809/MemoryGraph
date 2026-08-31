import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  memoriesApi,
  type CreateTextMemoryPayload,
  type SearchQuery,
  type TimelineQuery,
  type UpdateMemoryPayload,
  type UploadMemoryPayload,
} from '@/features/memories/memoriesApi'
import { peopleKeys } from '@/features/people/usePeople'
import type { Memory } from '@/types/api'

/** How often to re-check a memory whose enrichment is still running. */
const PROCESSING_POLL_INTERVAL_MS = 1500

export const memoryKeys = {
  all: ['memories'] as const,
  list: (page: number, size: number) => ['memories', 'list', page, size] as const,
  detail: (memoryId: string) => ['memories', 'detail', memoryId] as const,
  timeline: (query: TimelineQuery) => ['memories', 'timeline', query] as const,
  search: (query: SearchQuery) => ['memories', 'search', query] as const,
  stats: ['memories', 'stats'] as const,
}

export function useMemoryList(page = 0, size = 20) {
  return useQuery({
    queryKey: memoryKeys.list(page, size),
    queryFn: () => memoriesApi.list(page, size),
  })
}

export function useMemoryStats() {
  return useQuery({
    queryKey: memoryKeys.stats,
    queryFn: () => memoriesApi.stats(),
  })
}

export function useTimeline(query: TimelineQuery) {
  return useQuery({
    queryKey: memoryKeys.timeline(query),
    queryFn: () => memoriesApi.timeline(query),
  })
}

/**
 * Prefix matching is designed for typing, so every keystroke is a new query. Keeping the previous
 * page on screen avoids a flash of emptiness between those, and the abort signal drops the request
 * that is no longer wanted if a later keystroke overtakes it.
 */
export function useMemorySearch(query: SearchQuery) {
  return useQuery({
    queryKey: memoryKeys.search(query),
    queryFn: ({ signal }) => memoriesApi.search(query, signal),
    placeholderData: keepPreviousData,
  })
}

/**
 * Enrichment happens after the upload responds, so a freshly uploaded photo arrives without its
 * dimensions. Polling only while the status is unfinished lets the detail view fill in by itself and
 * then go quiet, rather than the user having to reload.
 */
export function useMemory(memoryId: string) {
  return useQuery({
    queryKey: memoryKeys.detail(memoryId),
    queryFn: () => memoriesApi.get(memoryId),
    refetchInterval: (query) => (isStillProcessing(query.state.data) ? PROCESSING_POLL_INTERVAL_MS : false),
  })
}

export function useCreateTextMemory() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: CreateTextMemoryPayload) => memoriesApi.createText(payload),
    onSuccess: () => invalidateMemories(queryClient),
  })
}

export function useUploadMemory() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      payload,
      onProgress,
    }: {
      payload: UploadMemoryPayload
      onProgress?: (percent: number) => void
    }) => memoriesApi.upload(payload, onProgress),
    onSuccess: () => invalidateMemories(queryClient),
  })
}

export function useDeleteMemory() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (memoryId: string) => memoriesApi.remove(memoryId),
    onSuccess: (_result, memoryId) => {
      queryClient.removeQueries({ queryKey: memoryKeys.detail(memoryId) })
      return invalidateMemories(queryClient)
    },
  })
}

export function useUpdateMemory(memoryId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: UpdateMemoryPayload) => memoriesApi.update(memoryId, payload),
    onSuccess: (memory) => {
      queryClient.setQueryData(memoryKeys.detail(memoryId), memory)
      return invalidateMemories(queryClient)
    },
  })
}

export function useDeleteTimelineDay() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ date, zone }: { date: string; zone: string }) => memoriesApi.deleteDay(date, zone),
    onSuccess: () => invalidateMemories(queryClient),
  })
}

export function useTagPerson(memoryId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (displayName: string) => memoriesApi.tagPerson(memoryId, displayName),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: memoryKeys.detail(memoryId) }),
  })
}

export function useUntagPerson(memoryId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (personId: string) => memoriesApi.untagPerson(memoryId, personId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memoryKeys.detail(memoryId) })
      return invalidateMemories(queryClient)
    },
  })
}

export function useConfirmFace(memoryId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      faceId,
      personId,
      displayName,
    }: {
      faceId: string
      personId?: string
      displayName?: string
    }) => {
      const payload: { personId?: string; displayName?: string } = {}
      if (personId) payload.personId = personId
      if (displayName) payload.displayName = displayName
      return memoriesApi.confirmFace(memoryId, faceId, payload)
    },
    onSuccess: (face) => {
      queryClient.setQueryData<Memory>(memoryKeys.detail(memoryId), (current) => {
        if (!current) return current
        const people =
          face.personId && face.personName && !current.people.some((person) => person.id === face.personId)
            ? [...current.people, { id: face.personId, displayName: face.personName }]
            : current.people
        return {
          ...current,
          people,
          faces: current.faces.map((existing) => (existing.id === face.id ? { ...existing, ...face } : existing)),
        }
      })
      queryClient.invalidateQueries({ queryKey: memoryKeys.detail(memoryId) })
      return invalidateMemories(queryClient)
    },
  })
}

export function useClearFace(memoryId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (faceId: string) => memoriesApi.clearFace(memoryId, faceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memoryKeys.detail(memoryId) })
      return invalidateMemories(queryClient)
    },
  })
}

function isStillProcessing(memory: Memory | undefined): boolean {
  return memory?.processingStatus === 'PENDING' || memory?.processingStatus === 'PROCESSING'
}

/**
 * A new or deleted memory changes counts, the list, the timeline and search, so all of them are
 * refetched rather than patched. Cheap at this scale, and it cannot drift from the server's view.
 */
function invalidateMemories(queryClient: ReturnType<typeof useQueryClient>) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: memoryKeys.all }),
    queryClient.invalidateQueries({ queryKey: peopleKeys.all }),
    queryClient.invalidateQueries({ queryKey: ['faces'] }),
  ])
}
