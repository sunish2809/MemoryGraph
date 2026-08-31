import { Link, useSearchParams } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Pagination } from '@/components/ui/Pagination'
import type { SearchQuery } from '@/features/memories/memoriesApi'
import { MemoryCard } from '@/features/memories/MemoryCard'
import { useMemorySearch } from '@/features/memories/useMemories'
import { usePerson } from '@/features/people/usePeople'
import { usePlace } from '@/features/places/usePlaces'
import { SearchBar } from '@/features/search/SearchBar'
import { VIEWER_TIME_ZONE } from '@/lib/format'
import { MEMORY_TYPE_LABELS } from '@/lib/memoryLabels'
import type { MemoryType, SearchSort } from '@/types/api'

/**
 * Types the user can actually create today. The API accepts the rest; they appear here once an
 * importer or recorder can produce them, rather than as filters that always come back empty.
 */
const FILTERABLE_TYPES: MemoryType[] = ['TEXT', 'PHOTO', 'VIDEO', 'AUDIO', 'CONVERSATION']

const PAGE_SIZE = 20

const SORT_LABELS: Record<SearchSort, string> = {
  RELEVANCE: 'Best match',
  NEWEST: 'Newest first',
  OLDEST: 'Oldest first',
}

/**
 * Search. The query lives in the URL so a search is shareable, reloadable and reachable from the
 * back button — and so the dashboard can hand off a phrase without the two pages needing shared
 * state.
 */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const query = queryFromParams(params)
  const { data, isPending, isFetching, isError } = useMemorySearch(query)
  const { data: personFilter } = usePerson(query.personId ?? '')
  const { data: placeFilter } = usePlace(query.placeId ?? '')

  const draft = params.get('q') ?? ''
  const selectedTypes = new Set(query.types ?? [])
  const hasActiveFilters = Boolean(
    query.q ||
      (query.types && query.types.length > 0) ||
      query.from ||
      query.to ||
      query.personId ||
      query.placeId,
  )

  function update(patch: Record<string, string | string[] | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(patch)) {
      next.delete(key)
      if (value === null || value === '') continue
      if (Array.isArray(value)) {
        for (const item of value) next.append(key, item)
      } else {
        next.set(key, value)
      }
    }
    next.delete('page')
    setParams(next, { replace: true })
  }

  function setPage(page: number) {
    const next = new URLSearchParams(params)
    if (page <= 0) next.delete('page')
    else next.set('page', String(page))
    setParams(next, { replace: true })
  }

  function toggleType(type: MemoryType) {
    const next = new Set(selectedTypes)
    if (next.has(type)) next.delete(type)
    else next.add(type)
    update({ type: [...next] })
  }

  const total = data?.totalItems ?? 0

  return (
    <PageShell
      title="Search"
      description="A word you remember, a type, a stretch of days — or all three."
      toolbar={
        <div className="flex flex-col gap-3">
          <SearchBar
            autoFocus
            value={draft}
            onChange={(value) => update({ q: value })}
            onSubmit={() => update({ q: draft.trim() })}
            placeholder="Try a place, a person, a word from a note…"
          />
          <div className="flex flex-col gap-3">
            {query.personId && (
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-md border border-accent/50 bg-accent-muted px-3 py-1 text-xs font-medium text-fg">
                  Person: {personFilter?.displayName ?? '…'}
                </span>
                <button
                  type="button"
                  onClick={() => update({ personId: null })}
                  className="text-xs text-fg-muted hover:text-accent"
                >
                  Clear person
                </button>
                <Link to="/people" className="text-xs text-fg-muted hover:text-accent">
                  All people
                </Link>
              </div>
            )}

            {query.placeId && (
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-md border border-support/50 bg-support/10 px-3 py-1 text-xs font-medium text-fg">
                  Place: {placeFilter?.displayName ?? '…'}
                </span>
                <button
                  type="button"
                  onClick={() => update({ placeId: null })}
                  className="text-xs text-fg-muted hover:text-accent"
                >
                  Clear place
                </button>
                <Link to="/places" className="text-xs text-fg-muted hover:text-accent">
                  All places
                </Link>
              </div>
            )}

            <div className="flex flex-wrap items-center gap-2">
              {FILTERABLE_TYPES.map((type) => {
                const active = selectedTypes.has(type)
                return (
                  <button
                    key={type}
                    type="button"
                    aria-pressed={active}
                    onClick={() => toggleType(type)}
                    className={`rounded-md border px-3 py-1 text-xs font-medium transition-colors ${
                      active
                        ? 'border-accent/60 bg-accent-muted text-fg'
                        : 'border-line text-fg-muted hover:border-accent/40 hover:text-fg'
                    }`}
                  >
                    {MEMORY_TYPE_LABELS[type]}
                  </button>
                )
              })}
            </div>

            <div className="flex flex-wrap items-end gap-3">
              <DateFilter
                label="From"
                value={query.from ?? ''}
                onChange={(value) => update({ from: value || null })}
              />
              <DateFilter
                label="To"
                value={query.to ?? ''}
                onChange={(value) => update({ to: value || null })}
              />
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-medium text-fg-muted">Order</span>
                <select
                  value={query.sort ?? 'RELEVANCE'}
                  onChange={(event) => update({ sort: event.target.value })}
                  className="rounded-lg border border-line bg-ink px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
                >
                  {(Object.keys(SORT_LABELS) as SearchSort[]).map((sort) => (
                    <option key={sort} value={sort}>
                      {SORT_LABELS[sort]}
                    </option>
                  ))}
                </select>
              </label>
              {hasActiveFilters && (
                <Button variant="ghost" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
                  Clear
                </Button>
              )}
            </div>
          </div>
        </div>
      }
      footer={
        data && data.totalPages > 1 ? (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalItems={data.totalItems}
            itemLabel="memories"
            onPageChange={setPage}
          />
        ) : undefined
      }
    >
      {isPending && <div className="h-48 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />}

      {isError && (
        <EmptyState
          icon="⚠"
          title="Search could not be run"
          description="The server did not respond as expected. Check that the backend is running and try again."
        />
      )}

      {data && data.items.length === 0 && (
        <EmptyState
          icon="⌕"
          title={hasActiveFilters ? 'Nothing matched' : 'Nothing saved yet'}
          description={
            hasActiveFilters
              ? 'Try a shorter word, drop a filter, or search for a phrase in quotes to match it exactly.'
              : 'Write a note or upload a photo, then come back here to find it.'
          }
        />
      )}

      {data && data.items.length > 0 && (
        <section className="flex flex-col gap-3" aria-live="polite">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-xs font-semibold tracking-wide text-fg-muted uppercase">
              {resultHeading(query, total)}
            </h2>
            {isFetching && !isPending && (
              <span className="text-xs text-fg-muted/70" aria-busy>
                Updating…
              </span>
            )}
          </div>
          <div className="flex flex-col gap-2">
            {data.items.map((hit) => (
              <MemoryCard key={hit.memory.id} memory={hit.memory} snippet={hit.snippet} />
            ))}
          </div>
        </section>
      )}
    </PageShell>
  )
}

function DateFilter({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-medium text-fg-muted">{label}</span>
      <input
        type="date"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-xl border border-line bg-surface px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
      />
    </label>
  )
}

function queryFromParams(params: URLSearchParams): SearchQuery {
  const q = params.get('q')?.trim() ?? ''
  const types = params.getAll('type').filter(isFilterableType)
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const personId = params.get('personId')?.trim() ?? ''
  const placeId = params.get('placeId')?.trim() ?? ''
  const sort = parseSort(params.get('sort'))
  const page = Number.parseInt(params.get('page') ?? '0', 10)

  return {
    zone: VIEWER_TIME_ZONE,
    sort,
    page: Number.isFinite(page) && page > 0 ? page : 0,
    size: PAGE_SIZE,
    ...(q ? { q } : {}),
    ...(types.length > 0 ? { types } : {}),
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
    ...(personId ? { personId } : {}),
    ...(placeId ? { placeId } : {}),
  }
}

function parseSort(value: string | null): SearchSort {
  if (value === 'NEWEST' || value === 'OLDEST' || value === 'RELEVANCE') return value
  return 'RELEVANCE'
}

function isFilterableType(value: string): value is MemoryType {
  return (FILTERABLE_TYPES as string[]).includes(value)
}

function resultHeading(query: SearchQuery, total: number): string {
  const noun = total === 1 ? 'memory' : 'memories'
  if (query.q && query.personId) return `${total} ${noun} matching “${query.q}” for this person`
  if (query.q) return `${total} ${noun} matching “${query.q}”`
  if (query.personId) return `${total} ${noun} for this person`
  return `${total} ${noun}`
}
