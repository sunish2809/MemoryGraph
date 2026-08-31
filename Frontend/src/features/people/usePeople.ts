import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { peopleApi } from '@/features/people/peopleApi'

export const peopleKeys = {
  all: ['people'] as const,
  list: ['people', 'list'] as const,
  detail: (personId: string) => ['people', 'detail', personId] as const,
  graph: ['people', 'graph'] as const,
}

export function usePeople() {
  return useQuery({
    queryKey: peopleKeys.list,
    queryFn: () => peopleApi.list(),
  })
}

export function usePerson(personId: string) {
  return useQuery({
    queryKey: peopleKeys.detail(personId),
    queryFn: () => peopleApi.get(personId),
    enabled: Boolean(personId),
  })
}

export function usePeopleGraph() {
  return useQuery({
    queryKey: peopleKeys.graph,
    queryFn: () => peopleApi.graph(),
  })
}

export function useRenamePerson(personId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (displayName: string) => peopleApi.rename(personId, displayName),
    onSuccess: (person) => {
      queryClient.setQueryData(peopleKeys.detail(personId), person)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: peopleKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
      ])
    },
  })
}

export function useMergePeople(keepId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sourcePersonId: string) => peopleApi.merge(keepId, sourcePersonId),
    onSuccess: (person, sourcePersonId) => {
      queryClient.setQueryData(peopleKeys.detail(keepId), person)
      queryClient.removeQueries({ queryKey: peopleKeys.detail(sourcePersonId) })
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: peopleKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['memories'] }),
      ])
    },
  })
}
