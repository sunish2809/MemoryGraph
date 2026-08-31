import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Panel } from '@/components/ui/Panel'
import { TextField } from '@/components/ui/TextField'
import { PlacesMap } from '@/features/places/PlacesMap'
import { useCreateTrip, useTrips } from '@/features/trips/useTrips'
import { ApiRequestError } from '@/lib/ApiRequestError'
import {
  dateInputToEndInstant,
  dateInputToStartInstant,
  formatDateRange,
} from '@/lib/format'
import type { TripSuggestion, TripSummary } from '@/types/api'

export function TripsPage() {
  const { data, isPending, error } = useTrips()
  const trips = data?.trips ?? []
  const suggestions = data?.suggestions ?? []

  return (
    <PageShell
      title="Trips"
      description="A stretch of days, with the people and places that showed up in that window. Suggestions come from GPS photos with a gap of more than a day and a half."
    >
      {isPending && <div className="h-48 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />}
      {error && <Alert>Trips could not be loaded.</Alert>}

      {data && (
        <div className="flex flex-col gap-6">
          {suggestions.length > 0 && (
            <section className="flex flex-col gap-3">
              <h2 className="text-xs font-semibold tracking-wide text-fg-muted uppercase">Looks like a trip</h2>
              {suggestions.map((suggestion) => (
                <SuggestionCard key={`${suggestion.startedAt}-${suggestion.endedAt}`} suggestion={suggestion} />
              ))}
            </section>
          )}

          <CreateTripForm />

          {trips.length === 0 && suggestions.length === 0 ? (
            <EmptyState
              icon="✈"
              title="No trips yet"
              description="When GPS photos cluster across a few days, they show up here to name. You can also save a stretch of days yourself."
            />
          ) : trips.length > 0 ? (
            <ul className="flex flex-col gap-1.5">
              {trips.map((trip) => (
                <li key={trip.id}>
                  <TripRow trip={trip} />
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      )}
    </PageShell>
  )
}

function TripRow({ trip }: { trip: TripSummary }) {
  return (
    <Link
      to={`/trips/${trip.id}`}
      className="flex items-center justify-between gap-4 rounded-lg border border-line/70 bg-ink/30 px-4 py-3 transition-colors hover:border-accent/40 hover:bg-surface-raised/40"
    >
      <span>
        <span className="block text-sm font-medium text-fg">{trip.title}</span>
        <span className="text-xs text-fg-muted">
          {formatDateRange(trip.startedAt, trip.endedAt)}
          {trip.primaryPlaceName ? ` · ${trip.primaryPlaceName}` : ''}
        </span>
      </span>
      <span className="text-xs tabular-nums text-fg-muted">
        {trip.memoryCount} {trip.memoryCount === 1 ? 'memory' : 'memories'}
        {trip.placeCount > 0 ? ` · ${trip.placeCount} ${trip.placeCount === 1 ? 'place' : 'places'}` : ''}
      </span>
    </Link>
  )
}

function SuggestionCard({ suggestion }: { suggestion: TripSuggestion }) {
  const create = useCreateTrip()
  const navigate = useNavigate()
  const error = create.error instanceof ApiRequestError ? create.error.message : null

  async function save() {
    const trip = await create.mutateAsync({
      title: suggestion.title,
      startedAt: suggestion.startedAt,
      endedAt: suggestion.endedAt,
    })
    navigate(`/trips/${trip.id}`)
  }

  return (
    <Panel title={suggestion.title}>
      <p className="mb-3 text-sm text-fg-muted">
        {formatDateRange(suggestion.startedAt, suggestion.endedAt)}
        {` · ${suggestion.memoryCount} ${suggestion.memoryCount === 1 ? 'memory' : 'memories'}`}
        {suggestion.placeCount > 0
          ? ` · ${suggestion.placeCount} ${suggestion.placeCount === 1 ? 'place' : 'places'}`
          : ''}
        {suggestion.personCount > 0
          ? ` · ${suggestion.personCount} ${suggestion.personCount === 1 ? 'person' : 'people'}`
          : ''}
      </p>
      {suggestion.places.length > 0 && (
        <PlacesMap
          places={suggestion.places}
          className="mb-4 h-40 w-full overflow-hidden rounded-lg border border-line/70"
        />
      )}
      {error && <Alert>{error}</Alert>}
      <Button type="button" onClick={() => void save()} isLoading={create.isPending}>
        Save trip
      </Button>
    </Panel>
  )
}

function CreateTripForm() {
  const create = useCreateTrip()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const error = create.error instanceof ApiRequestError ? create.error.message : null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const startedAt = dateInputToStartInstant(from)
    const endedAt = dateInputToEndInstant(to || from)
    if (!title.trim() || !startedAt || !endedAt) return
    const trip = await create.mutateAsync({ title: title.trim(), startedAt, endedAt })
    navigate(`/trips/${trip.id}`)
  }

  if (!open) {
    return (
      <div>
        <Button type="button" variant="secondary" onClick={() => setOpen(true)}>
          Name a stretch of days
        </Button>
      </div>
    )
  }

  return (
    <Panel title="New trip">
      <form className="flex flex-col gap-3" onSubmit={onSubmit}>
        {error && <Alert>{error}</Alert>}
        <TextField label="Title" value={title} onChange={(event) => setTitle(event.target.value)} required />
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="From" type="date" value={from} onChange={(event) => setFrom(event.target.value)} required />
          <TextField label="To" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="submit" isLoading={create.isPending} disabled={!title.trim() || !from}>
            Save trip
          </Button>
        </div>
      </form>
    </Panel>
  )
}