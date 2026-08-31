import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { AuthenticatedImage } from '@/features/memories/AuthenticatedImage'
import { useConfirmFace } from '@/features/memories/useMemories'
import { usePeople } from '@/features/people/usePeople'
import { useConfirmFaceCluster, useFaceReview, useIgnoreFace, useIgnoreFaceCluster, useRejectFaceSuggestion } from '@/features/faces/useFaces'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { formatDateTime } from '@/lib/format'
import type { FaceClusterGroup, FaceReviewItem } from '@/types/api'

export function FacesReviewPage() {
  const { data: review, isPending, error } = useFaceReview()

  return (
    <PageShell
      title="Faces"
      description="Every unnamed face in one place. Confirm a look-alike, skip background people, or name a whole cluster of the same unknown person."
    >
      {isPending && <div className="h-48 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />}
      {error && <Alert>Faces could not be loaded.</Alert>}

      {review && review.unlabeledCount === 0 && (
        <EmptyState
          icon="◉"
          title="No unlabeled faces"
          description="When photos and videos are scanned, unnamed faces show up here to confirm, skip as background, or name as a group."
        />
      )}

      {review && review.unlabeledCount > 0 && (
        <div className="flex flex-col gap-6">
          <p className="text-sm text-fg-muted">
            {review.unlabeledCount} unlabeled
            {review.suggestedCount > 0 ? ` · ${review.suggestedCount} look like someone you already named` : ''}
          </p>
          {review.groups.map((group, index) => (
            <ClusterBlock key={group.clusterId ?? group.faces[0]?.id ?? index} group={group} />
          ))}
        </div>
      )}
    </PageShell>
  )
}

function ClusterBlock({ group }: { group: FaceClusterGroup }) {
  const clustered = Boolean(group.clusterId) && group.size > 1

  return (
    <section className="rounded-xl border border-line/70 bg-ink/20 p-4">
      {clustered ? (
        <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="font-display text-lg text-fg">Same unknown person in {group.size} photos</h2>
            <p className="text-sm text-fg-muted">
              {group.suggestedPersonName
                ? `Looks like ${group.suggestedPersonName}. Name the group, skip them as background, or open a photo to reject one.`
                : 'These faces match each other. Name them once, or skip them if they are background people.'}
            </p>
          </div>
          {group.clusterId && (
            <NameClusterForm clusterId={group.clusterId} suggestedName={group.suggestedPersonName ?? ''} />
          )}
        </div>
      ) : (
        <h2 className="mb-3 text-xs font-semibold tracking-wide text-fg-muted uppercase">Unknown face</h2>
      )}
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {group.faces.map((face) => (
          <li key={face.id}>
            <ReviewFaceCard face={face} />
          </li>
        ))}
      </ul>
    </section>
  )
}

function NameClusterForm({ clusterId, suggestedName }: { clusterId: string; suggestedName: string }) {
  const confirm = useConfirmFaceCluster()
  const ignoreCluster = useIgnoreFaceCluster()
  const { data: people } = usePeople()
  const [draft, setDraft] = useState(suggestedName ?? '')
  const error = confirm.error instanceof ApiRequestError ? confirm.error.message : null
  const ignoreError = ignoreCluster.error instanceof ApiRequestError ? ignoreCluster.error.message : null

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const name = draft.trim()
    if (!name) return
    confirm.mutate({ clusterId, displayName: name })
  }

  return (
    <form className="flex flex-wrap items-end gap-2" onSubmit={onSubmit}>
      <input
        list={`cluster-people-${clusterId}`}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        placeholder="Name this person"
        className="min-w-[10rem] rounded-lg border border-line bg-ink/40 px-3 py-2 text-sm text-fg"
      />
      <datalist id={`cluster-people-${clusterId}`}>
        {(people ?? []).map((person) => (
          <option key={person.id} value={person.displayName} />
        ))}
      </datalist>
      <Button type="submit" isLoading={confirm.isPending} disabled={draft.trim().length === 0}>
        Name all
      </Button>
      <Button
        type="button"
        variant="secondary"
        isLoading={ignoreCluster.isPending}
        onClick={() => ignoreCluster.mutate(clusterId)}
      >
        Don't name any
      </Button>
      {error && <span className="w-full text-xs text-danger">{error}</span>}
      {ignoreError && <span className="w-full text-xs text-danger">{ignoreError}</span>}
    </form>
  )
}

function ReviewFaceCard({ face }: { face: FaceReviewItem }) {
  const confirm = useConfirmFace(face.memoryId)
  const reject = useRejectFaceSuggestion()
  const ignore = useIgnoreFace()
  const [naming, setNaming] = useState(false)
  const [draft, setDraft] = useState(face.suggestedPersonName ?? '')
  const confirmError = confirm.error instanceof ApiRequestError ? confirm.error.message : null
  const rejectError = reject.error instanceof ApiRequestError ? reject.error.message : null
  const ignoreError = ignore.error instanceof ApiRequestError ? ignore.error.message : null

  return (
    <article className="flex flex-col gap-2 rounded-lg border border-line/70 bg-surface/40 p-2">
      <Link to={`/memories/${face.memoryId}`} className="relative block overflow-hidden rounded-md">
        <AuthenticatedImage
          downloadPath={face.downloadPath}
          alt={face.memoryTitle ?? 'Unnamed face'}
          className="aspect-square w-full object-cover"
        />
        <span
          className="absolute border-2 border-accent bg-accent/10"
          style={{
            left: `${face.x * 100}%`,
            top: `${face.y * 100}%`,
            width: `${face.width * 100}%`,
            height: `${face.height * 100}%`,
          }}
        />
      </Link>
      <div className="flex flex-col gap-1 px-1">
        <Link to={`/memories/${face.memoryId}`} className="truncate text-sm text-fg hover:text-accent">
          {face.memoryTitle ?? 'Untitled memory'}
        </Link>
        {face.occurredAt && (
          <time dateTime={face.occurredAt} className="text-xs text-fg-muted">
            {formatDateTime(face.occurredAt)}
          </time>
        )}
        {face.suggestedPersonName && (
          <p className="text-xs text-fg-muted">
            Looks like {face.suggestedPersonName}
            {face.confidence != null ? ` · ${Math.round(face.confidence * 100)}%` : ''}
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2 px-1 pb-1">
        {face.suggestedPersonId && (
          <>
            <Button
              type="button"
              variant="secondary"
              isLoading={confirm.isPending}
              onClick={() => {
                if (!face.suggestedPersonId) return
                confirm.mutate({ faceId: face.id, personId: face.suggestedPersonId })
              }}
            >
              Confirm {face.suggestedPersonName}
            </Button>
            <button
              type="button"
              className="text-xs text-fg-muted hover:text-danger hover:underline"
              disabled={reject.isPending}
              onClick={() => reject.mutate(face.id)}
            >
              Not them
            </button>
          </>
        )}
        {naming ? (
          <form
            className="flex flex-wrap gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              const name = draft.trim()
              if (!name) return
              confirm.mutate({ faceId: face.id, displayName: name }, { onSuccess: () => setNaming(false) })
            }}
          >
            <input
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              className="rounded-lg border border-line bg-ink/40 px-2 py-1 text-sm text-fg"
              autoFocus
            />
            <Button type="submit" isLoading={confirm.isPending}>
              Save
            </Button>
          </form>
        ) : (
          <button type="button" className="text-xs text-accent hover:underline" onClick={() => setNaming(true)}>
            Who is this?
          </button>
        )}
        <button
          type="button"
          className="text-xs text-fg-muted hover:text-danger hover:underline"
          disabled={ignore.isPending}
          onClick={() => ignore.mutate(face.id)}
        >
          Don't name
        </button>
      </div>
      {confirmError && <Alert>{confirmError}</Alert>}
      {rejectError && <Alert>{rejectError}</Alert>}
      {ignoreError && <Alert>{ignoreError}</Alert>}
    </article>
  )
}
