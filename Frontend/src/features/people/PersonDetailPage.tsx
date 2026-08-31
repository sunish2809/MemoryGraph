import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'

import { ScrollPage } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Panel } from '@/components/ui/Panel'
import { TextField } from '@/components/ui/TextField'
import { AuthenticatedImage } from '@/features/memories/AuthenticatedImage'
import { MemoryCard } from '@/features/memories/MemoryCard'
import { useMergePeople, usePeople, usePerson, useRenamePerson } from '@/features/people/usePeople'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { formatDateTime } from '@/lib/format'
import type { MemorySummary, PersonDetail } from '@/types/api'

export function PersonDetailPage() {
  const { personId = '' } = useParams()
  const { data: person, isPending, error } = usePerson(personId)

  if (isPending) {
    return (
      <ScrollPage>
        <div className="h-64 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />
      </ScrollPage>
    )
  }

  if (error || !person) {
    const notFound = error instanceof ApiRequestError && error.code === 'RESOURCE_NOT_FOUND'
    return (
      <ScrollPage>
        <Alert>{notFound ? 'That person was not found.' : 'This person could not be loaded.'}</Alert>
        <Link to="/people" className="text-sm text-accent hover:underline">
          Back to people
        </Link>
      </ScrollPage>
    )
  }

  const branches = connectionBranches(person)
  const photos = person.photos ?? []
  const otherMemories = person.memories ?? []

  return (
    <ScrollPage>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs text-fg-muted">
            <Link to="/people" className="hover:text-accent">
              People
            </Link>
          </p>
          <h1 className="mt-1 font-display text-2xl font-semibold tracking-tight">{person.displayName}</h1>
          <p className="mt-1 text-sm text-fg-muted">
            {person.memoryCount} {person.memoryCount === 1 ? 'memory' : 'memories'} connected
          </p>
        </div>
        <Link
          to={`/search?personId=${encodeURIComponent(person.id)}`}
          className="rounded-lg border border-line px-3 py-2 text-sm text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
        >
          Search their memories
        </Link>
      </div>

      <RenamePersonForm person={person} />
      <MergePeopleForm person={person} />

      {branches.length > 0 && (
        <section className="rounded-xl border border-line/60 bg-ink/20 px-4 py-4">
          <p className="mb-3 font-display text-lg text-fg">{person.displayName}</p>
          <ul className="flex flex-col gap-1.5 font-mono text-sm text-fg-muted">
            {branches.map((branch) => (
              <li key={branch.label} className="flex gap-2">
                <span className="text-fg-muted/50" aria-hidden>
                  {branch.rail}
                </span>
                <span>
                  <span className="tabular-nums text-fg">{branch.count}</span> {branch.label}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {photos.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="text-xs font-semibold tracking-wide text-fg-muted uppercase">Photos</h2>
          <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
            {photos.map((photo) => (
              <li key={photo.id}>
                <PersonPhotoTile photo={photo} personName={person.displayName} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {otherMemories.length > 0 && (
        <div className="flex flex-col gap-2">
          <h2 className="text-xs font-semibold tracking-wide text-fg-muted uppercase">
            {photos.length > 0 ? 'Chats and notes' : 'Recent'}
          </h2>
          {otherMemories.map((memory) => (
            <MemoryCard key={memory.id} memory={memory} />
          ))}
        </div>
      )}

      {photos.length === 0 && otherMemories.length === 0 && (
        <EmptyState
          icon="◈"
          title="No linked memories"
          description="This person is recorded but not linked to any memory yet."
        />
      )}
    </ScrollPage>
  )
}

function PersonPhotoTile({ photo, personName }: { photo: MemorySummary; personName: string }) {
  const thumbnail = photo.assets[0]
  return (
    <Link
      to={`/memories/${photo.id}`}
      className="group relative block overflow-hidden rounded-lg border border-line/70 bg-ink/40"
    >
      {thumbnail ? (
        <AuthenticatedImage
          downloadPath={thumbnail.downloadPath}
          alt={photo.title ?? personName}
          className="aspect-square w-full object-cover transition-transform group-hover:scale-[1.03]"
        />
      ) : (
        <div className="flex aspect-square items-center justify-center text-fg-muted">·</div>
      )}
      <span className="absolute inset-x-0 bottom-0 truncate bg-ink/75 px-2 py-1 text-[11px] text-fg">
        {photo.title ?? formatDateTime(photo.occurredAt)}
      </span>
    </Link>
  )
}

function RenamePersonForm({ person }: { person: PersonDetail }) {
  const rename = useRenamePerson(person.id)
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(person.displayName)
  const error = rename.error instanceof ApiRequestError ? rename.error.message : null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const name = draft.trim()
    if (!name || name === person.displayName) {
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

function MergePeopleForm({ person }: { person: PersonDetail }) {
  const { data: allPeople } = usePeople()
  const merge = useMergePeople(person.id)
  const [sourceId, setSourceId] = useState('')
  const [confirming, setConfirming] = useState(false)
  const others = (allPeople ?? []).filter((other) => other.id !== person.id)
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
        WhatsApp names and face tags often create both “Raj” and “Raj Sharma”. Merge the extra person
        into this one.
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
            <option value="">Choose a person…</option>
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
              Merge {source.displayName} into {person.displayName}?
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
            Merge into {person.displayName}
          </Button>
        )}
      </div>
    </Panel>
  )
}

function connectionBranches(person: PersonDetail) {
  const c = person.connected
  const rows = [
    { count: c.conversations, label: c.conversations === 1 ? 'conversation' : 'conversations' },
    { count: c.photos, label: c.photos === 1 ? 'photo' : 'photos' },
    { count: c.videos, label: c.videos === 1 ? 'video' : 'videos' },
    { count: c.audio, label: c.audio === 1 ? 'audio' : 'audio' },
    { count: c.documents, label: c.documents === 1 ? 'document' : 'documents' },
    { count: c.text, label: c.text === 1 ? 'note' : 'notes' },
    { count: c.events, label: c.events === 1 ? 'event' : 'events' },
    { count: c.places, label: c.places === 1 ? 'location' : 'locations' },
  ].filter((row) => row.count > 0)

  return rows.map((row, index) => ({
    ...row,
    rail: index === rows.length - 1 ? '└──' : '├──',
  }))
}
