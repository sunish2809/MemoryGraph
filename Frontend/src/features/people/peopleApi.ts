import { api } from '@/lib/api'
import type { PeopleGraph, PersonDetail, PersonSummary } from '@/types/api'

export const peopleApi = {
  list: () => api.get<PersonSummary[]>('/people'),
  get: (personId: string) => api.get<PersonDetail>(`/people/${personId}`),
  graph: () => api.get<PeopleGraph>('/people/graph'),
  rename: (personId: string, displayName: string) =>
    api.patch<PersonDetail>(`/people/${personId}`, { displayName }),
  merge: (keepId: string, sourcePersonId: string) =>
    api.post<PersonDetail>(`/people/${keepId}/merge`, { sourcePersonId }),
}
