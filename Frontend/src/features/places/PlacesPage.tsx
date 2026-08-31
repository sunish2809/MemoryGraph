import { useState } from 'react'
import { Link } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { EmptyState } from '@/components/ui/EmptyState'
import { PlacesMap } from '@/features/places/PlacesMap'
import { usePlaces } from '@/features/places/usePlaces'
import type { PlaceSummary } from '@/types/api'

export function PlacesPage() {
  const { data: places, isPending, error } = usePlaces()
  const [view, setView] = useState<'map' | 'list'>('map')

  return (
    <PageShell
      title="Places"
      description="Clustered from photo GPS. Rename a cell when geocoding got it wrong, or merge two cells that are the same town."
      actions={
        <Link
          to="/trips"
          className="rounded-lg border border-line px-3 py-2 text-sm text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
        >
          Trips
        </Link>
      }
      toolbar={
        places && places.length > 0 ? (
          <div className="flex gap-1 rounded-lg border border-line/70 bg-ink/30 p-1">
            <ViewTab active={view === 'map'} onClick={() => setView('map')}>
              Map
            </ViewTab>
            <ViewTab active={view === 'list'} onClick={() => setView('list')}>
              List
            </ViewTab>
          </div>
        ) : undefined
      }
    >
      {isPending && <div className="h-48 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />}
      {error && <Alert>Places could not be loaded.</Alert>}

      {places && places.length === 0 && (
        <EmptyState
          icon="⌖"
          title="No places yet"
          description="Upload a photo that has GPS in its EXIF data and it will appear here after enrichment."
        />
      )}

      {places && places.length > 0 && view === 'map' && (
        <div className="flex flex-col gap-3">
          <PlacesMap places={places} className="h-[28rem] w-full overflow-hidden rounded-xl border border-line/70" />
          <p className="text-xs text-fg-muted">Click a marker to open that place. Switch to List to scan names.</p>
        </div>
      )}

      {places && places.length > 0 && view === 'list' && (
        <ul className="flex flex-col gap-1.5">
          {places.map((place) => (
            <li key={place.id}>
              <Link
                to={`/places/${place.id}`}
                className="flex items-center justify-between gap-4 rounded-lg border border-line/70 bg-ink/30 px-4 py-3 transition-colors hover:border-accent/40 hover:bg-surface-raised/40"
              >
                <span>
                  <span className="block text-sm font-medium text-fg">{place.displayName}</span>
                  <span className="text-xs text-fg-muted">{placeSubtitle(place)}</span>
                </span>
                <span className="text-xs tabular-nums text-fg-muted">
                  {place.memoryCount} {place.memoryCount === 1 ? 'memory' : 'memories'}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </PageShell>
  )
}

function ViewTab({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-md px-3 py-1.5 text-xs font-medium ${
        active ? 'bg-accent-muted text-fg' : 'text-fg-muted hover:text-fg'
      }`}
    >
      {children}
    </button>
  )
}

function placeSubtitle(place: PlaceSummary): string {
  if (place.nameLocked) {
    return 'Named by you'
  }
  if (place.geocoded) {
    return 'Resolved from photo GPS'
  }
  return 'Resolving place name…'
}
