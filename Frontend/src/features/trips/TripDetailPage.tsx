import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ScrollPage } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Panel } from '@/components/ui/Panel'
import { TextAreaField } from '@/components/ui/TextAreaField'
import { TextField } from '@/components/ui/TextField'
import { MemoryCard } from '@/features/memories/MemoryCard'
import { PlacesMap } from '@/features/places/PlacesMap'
import { useDeleteTrip, useTrip, useUpdateTrip } from '@/features/trips/useTrips'
import { ApiRequestError } from '@/lib/ApiRequestError'
import {
  dateInputToEndInstant,
  dateInputToStartInstant,
  formatDateRange,
  instantToDateInput,
} from '@/lib/format'
import type { TripDetail } from '@/types/api'

export function TripDetailPage() {
  const { tripId = '' } = useParams()
  const { data: trip, isPending, error } = useTrip(tripId)

  if (isPending) {
    return (
      <ScrollPage>
        <div className="h-64 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />
      </ScrollPage>
    )
  }

  if (error || !trip) {
    const notFound = error instanceof ApiRequestError && error.code === 'RESOURCE_NOT_FOUND'
    return (
      <ScrollPage>
        <Alert>{notFound ? 'That trip was not found.' : 'This trip could not be loaded.'}</Alert>
        <Link to="/trips" className="text-sm text-accent hover:underline">
          Back to trips
        </Link>
      </ScrollPage>
    )
  }

  return (
    <ScrollPage>
      <div>
        <p className="text-xs text-fg-muted">
          <Link to="/trips" className="hover:text-accent">
            Trips
          </Link>
        </p>
        <h1 className="mt-1 font-display text-2xl font-semibold tracking-tight">{trip.title}</h1>
        <p className="mt-1 text-sm text-fg-muted">
          {formatDateRange(trip.startedAt, trip.endedAt)}
          {` · ${trip.memoryCount} ${trip.memoryCount === 1 ? 'memory' : 'memories'}`}
        </p>
      </div>

      {trip.notes && <p className="text-sm text-fg">{trip.notes}</p>}

      {trip.places.length > 0 && (
        <PlacesMap
          places={trip.places}
          className="h-56 w-full overflow-hidden rounded-xl border border-line/70"
        />
      )}

      {trip.places.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {trip.places.map((place) => (
            <li key={place.id}>
              <Link
                to={`/places/${place.id}`}
                className="rounded-full border border-line/70 bg-ink/30 px-3 py-1 text-xs text-fg hover:border-accent/50"
              >
                {place.displayName}
              </Link>
            </li>
          ))}
        </ul>
      )}

      {trip.people.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {trip.people.map((person) => (
            <li key={person.id}>
              <Link
                to={`/people/${person.id}`}
                className="rounded-full border border-line/70 bg-ink/30 px-3 py-1 text-xs text-fg hover:border-accent/50"
              >
                {person.displayName}
              </Link>
            </li>
          ))}
        </ul>
      )}

      <EditTripForm trip={trip} />
      <DeleteTripButton tripId={trip.id} />

      {trip.memories.length === 0 ? (
        <EmptyState
          icon="◈"
          title="No memories in this window"
          description="Widen the dates if photos from this trip sit just outside the range."
        />
      ) : (
        <div className="flex flex-col gap-2">
          {trip.memories.map((memory) => (
            <MemoryCard key={memory.id} memory={memory} />
          ))}
        </div>
      )}
    </ScrollPage>
  )
}

function EditTripForm({ trip }: { trip: TripDetail }) {
  const update = useUpdateTrip(trip.id)
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState(trip.title)
  const [from, setFrom] = useState(instantToDateInput(trip.startedAt))
  const [to, setTo] = useState(instantToDateInput(trip.endedAt))
  const [notes, setNotes] = useState(trip.notes ?? '')
  const error = update.error instanceof ApiRequestError ? update.error.message : null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const startedAt = dateInputToStartInstant(from)
    const endedAt = dateInputToEndInstant(to || from)
    if (!title.trim() || !startedAt || !endedAt) return
    await update.mutateAsync({ title: title.trim(), startedAt, endedAt, notes })
    setOpen(false)
  }

  if (!open) {
    return (
      <div>
        <Button type="button" variant="secondary" onClick={() => setOpen(true)}>
          Edit dates
        </Button>
      </div>
    )
  }

  return (
    <Panel title="Edit trip">
      <form className="flex flex-col gap-3" onSubmit={onSubmit}>
        {error && <Alert>{error}</Alert>}
        <TextField label="Title" value={title} onChange={(event) => setTitle(event.target.value)} required />
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="From" type="date" value={from} onChange={(event) => setFrom(event.target.value)} required />
          <TextField label="To" type="date" value={to} onChange={(event) => setTo(event.target.value)} required />
        </div>
        <TextAreaField
          label="Notes"
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          rows={3}
        />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="submit" isLoading={update.isPending}>
            Save
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function DeleteTripButton({ tripId }: { tripId: string }) {
  const remove = useDeleteTrip()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const error = remove.error instanceof ApiRequestError ? remove.error.message : null

  async function onDelete() {
    await remove.mutateAsync(tripId)
    navigate('/trips')
  }

  return (
    <div className="flex flex-col gap-2">
      {error && <Alert>{error}</Alert>}
      {confirming ? (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm text-fg-muted">Delete this trip? Memories stay; only the trip name is removed.</span>
          <Button type="button" variant="ghost" onClick={() => setConfirming(false)}>
            Cancel
          </Button>
          <Button type="button" variant="danger" onClick={() => void onDelete()} isLoading={remove.isPending}>
            Delete trip
          </Button>
        </div>
      ) : (
        <button type="button" className="self-start text-xs text-fg-muted hover:text-danger hover:underline" onClick={() => setConfirming(true)}>
          Delete trip
        </button>
      )}
    </div>
  )
}
