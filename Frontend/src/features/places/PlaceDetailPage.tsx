import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'

import { ScrollPage } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Panel } from '@/components/ui/Panel'
import { TextField } from '@/components/ui/TextField'
import { MemoryCard } from '@/features/memories/MemoryCard'
import { PlacesMap } from '@/features/places/PlacesMap'
import { useMergePlaces, usePlace, usePlaces, useRenamePlace } from '@/features/places/usePlaces'
import { ApiRequestError } from '@/lib/ApiRequestError'
import type { PlaceDetail } from '@/types/api'

export function PlaceDetailPage() {
  const { placeId = '' } = useParams()
  const { data: place, isPending, error } = usePlace(placeId)

  if (isPending) {
    return (
      <ScrollPage>
        <div className="h-64 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />
      </ScrollPage>
    )
  }

  if (error || !place) {
    const notFound = error instanceof ApiRequestError && error.code === 'RESOURCE_NOT_FOUND'
    return (
      <ScrollPage>
        <Alert>{notFound ? 'That place was not found.' : 'This place could not be loaded.'}</Alert>
        <Link to="/places" className="text-sm text-accent hover:underline">
          Back to places
        </Link>
      </ScrollPage>
    )
  }

  return (
    <ScrollPage>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs text-fg-muted">
            <Link to="/places" className="hover:text-accent">
              Places
            </Link>
          </p>
          <h1 className="mt-1 font-display text-2xl font-semibold tracking-tight">{place.displayName}</h1>
          <p className="mt-1 text-sm text-fg-muted">
            {place.memoryCount} {place.memoryCount === 1 ? 'memory' : 'memories'}
            {` · ${place.latitude.toFixed(2)}°, ${place.longitude.toFixed(2)}°`}
            {place.nameLocked ? ' · named by you' : place.geocoded ? '' : ' · place name still resolving'}
          </p>
        </div>
        <Link
          to={`/search?placeId=${encodeURIComponent(place.id)}`}
          className="rounded-lg border border-line px-3 py-2 text-sm text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
        >
          Search this place
        </Link>
      </div>

      <PlacesMap places={[place]} selectedId={place.id} className="h-56 w-full overflow-hidden rounded-xl border border-line/70" />

      <RenamePlaceForm place={place} />
      <MergePlaceForm place={place} />

      {place.memories.length === 0 ? (
        <EmptyState icon="◈" title="No linked memories" description="Nothing is linked to this place yet." />
      ) : (
        <div className="flex flex-col gap-2">
          {place.memories.map((memory) => (
            <MemoryCard key={memory.id} memory={memory} />
          ))}
        </div>
      )}
    </ScrollPage>
  )
}

function RenamePlaceForm({ place }: { place: PlaceDetail }) {
  const rename = useRenamePlace(place.id)
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(place.displayName)
  const error = rename.error instanceof ApiRequestError ? rename.error.message : null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const name = draft.trim()
    if (!name || name === place.displayName) {
      setOpen(false)
      return
    }
    await rename.mutateAsync(name)
    setOpen(false)
  }

  if (!open) {
    return (
      <div>
        <Button type="button" variant="secondary" onClick={() => setOpen(true)}>
          Rename
        </Button>
      </div>
    )
  }

  return (
    <Panel title="Rename">
      <form className="flex flex-col gap-3" onSubmit={onSubmit}>
        {error && <Alert>{error}</Alert>}
        <TextField
          label="Name"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          autoFocus
          required
          hint="Stops reverse-geocoding from overwriting this label. Merge nearby GPS cells if they are the same town."
        />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => setOpen(false)} disabled={rename.isPending}>
            Cancel
          </Button>
          <Button type="submit" isLoading={rename.isPending} disabled={draft.trim().length === 0}>
            Save name
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function MergePlaceForm({ place }: { place: PlaceDetail }) {
  const { data: allPlaces } = usePlaces()
  const merge = useMergePlaces(place.id)
  const [sourceId, setSourceId] = useState('')
  const [confirming, setConfirming] = useState(false)
  const others = (allPlaces ?? []).filter((other) => other.id !== place.id)
  const source = others.find((other) => other.id === sourceId)
  const error = merge.error instanceof ApiRequestError ? merge.error.message : null

  if (others.length === 0) return null

  async function onMerge() {
    if (!sourceId) return
    await merge.mutateAsync(sourceId)
    setSourceId('')
    setConfirming(false)
  }

  return (
    <Panel title="Merge a duplicate">
      <p className="mb-3 text-sm text-fg-muted">
        GPS splits a town into ~1 km cells, so you may see both “Gangtok” and a coordinate next door.
        Merge the extra cell into this one.
      </p>
      {error && <Alert>{error}</Alert>}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <label className="flex min-w-0 flex-1 flex-col gap-1.5 text-sm font-medium text-fg">
          Duplicate to merge in
          <select
            value={sourceId}
            onChange={(event) => {
              setSourceId(event.target.value)
              setConfirming(false)
            }}
            className="rounded-xl border border-line bg-surface px-3.5 py-2.5 text-sm font-normal text-fg focus:border-accent focus:outline-none"
          >
            <option value="">Choose a place…</option>
            {others.map((other) => (
              <option key={other.id} value={other.id}>
                {other.displayName} ({other.memoryCount})
              </option>
            ))}
          </select>
        </label>
        {confirming && source ? (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm text-fg-muted">
              Merge {source.displayName} into {place.displayName}?
            </span>
            <Button type="button" variant="ghost" onClick={() => setConfirming(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={onMerge} isLoading={merge.isPending}>
              Merge
            </Button>
          </div>
        ) : (
          <Button type="button" variant="secondary" disabled={!sourceId} onClick={() => setConfirming(true)}>
            Merge into {place.displayName}
          </Button>
        )}
      </div>
    </Panel>
  )
}
