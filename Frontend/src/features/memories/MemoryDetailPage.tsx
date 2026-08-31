import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ScrollPage } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { MemoryTypeBadge, ProcessingBadge } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { Panel } from '@/components/ui/Panel'
import { TextAreaField } from '@/components/ui/TextAreaField'
import { TextField } from '@/components/ui/TextField'
import { AuthenticatedAudio, AuthenticatedImage, AuthenticatedVideo } from '@/features/memories/AuthenticatedImage'
import { useIgnoreFace, useRejectFaceSuggestion, useRestoreFace } from '@/features/faces/useFaces'
import {
  useClearFace,
  useConfirmFace,
  useDeleteMemory,
  useMemory,
  useTagPerson,
  useUntagPerson,
  useUpdateMemory,
} from '@/features/memories/useMemories'
import { usePeople } from '@/features/people/usePeople'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { formatDateTime, formatFileSize, formatTime, instantToLocalInput, localInputToInstant } from '@/lib/format'
import type { ConversationMessage, FaceDetection, MediaAsset, Memory } from '@/types/api'

export function MemoryDetailPage() {
  const { memoryId = '' } = useParams()
  const navigate = useNavigate()
  const { data: memory, isPending, error } = useMemory(memoryId)
  const deleteMemory = useDeleteMemory()
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false)
  const [isEditing, setIsEditing] = useState(false)

  if (isPending) {
    return (
      <ScrollPage>
        <div className="h-64 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />
      </ScrollPage>
    )
  }

  if (error || !memory) {
    const notFound = error instanceof ApiRequestError && error.code === 'RESOURCE_NOT_FOUND'
    return (
      <ScrollPage>
        <Alert>{notFound ? 'That memory no longer exists.' : 'This memory could not be loaded.'}</Alert>
        <Link to="/timeline" className="text-sm text-accent hover:underline">
          Back to your timeline
        </Link>
      </ScrollPage>
    )
  }

  async function onDelete() {
    await deleteMemory.mutateAsync(memoryId)
    navigate('/timeline', { replace: true })
  }

  return (
    <ScrollPage>
      <div className="flex flex-col gap-6">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center gap-2">
              <MemoryTypeBadge type={memory.type} />
              <ProcessingBadge status={memory.processingStatus} />
            </div>
            <h1 className="font-display text-2xl font-semibold text-fg">{memory.title ?? 'Untitled memory'}</h1>
            <time dateTime={memory.occurredAt} className="text-sm text-fg-muted">
              Happened {formatDateTime(memory.occurredAt)}
            </time>
          </div>

          {isConfirmingDelete ? (
            <div className="flex items-center gap-2">
              <span className="text-sm text-fg-muted">Delete permanently?</span>
              <Button variant="ghost" onClick={() => setIsConfirmingDelete(false)}>
                Keep
              </Button>
              <Button
                onClick={onDelete}
                isLoading={deleteMemory.isPending}
                className="bg-danger shadow-none hover:bg-danger/85"
              >
                Delete
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Button variant="secondary" onClick={() => setIsEditing((open) => !open)}>
                {isEditing ? 'Cancel edit' : 'Edit'}
              </Button>
              <Button variant="secondary" onClick={() => setIsConfirmingDelete(true)}>
                Delete
              </Button>
            </div>
          )}
        </header>

        {deleteMemory.isError && <Alert>This memory could not be deleted. Please try again.</Alert>}

        {isEditing && <EditMemoryForm memory={memory} onClose={() => setIsEditing(false)} />}

        <PeopleTagger memory={memory} />

        {memory.assets.map((asset) => (
          <figure key={asset.id} className="flex flex-col gap-2">
            {asset.mimeType.startsWith('video/') ? (
              <AuthenticatedVideo
                downloadPath={asset.downloadPath}
                className="max-h-[70vh] w-full rounded-panel border border-line"
              />
            ) : asset.mimeType.startsWith('audio/') ? (
              <AuthenticatedAudio downloadPath={asset.downloadPath} />
            ) : (
              <PhotoWithFaces memory={memory} asset={asset} />
            )}
            <figcaption className="text-xs text-fg-muted">{describeAsset(asset)}</figcaption>
          </figure>
        ))}

        {memory.description && memory.type !== 'CONVERSATION' && !isEditing && (
          <Panel title="Description">
            <p className="text-sm leading-relaxed whitespace-pre-wrap text-fg">{memory.description}</p>
          </Panel>
        )}

        {memory.messages && memory.messages.length > 0 ? (
          <Panel title="Conversation">
            <ol className="flex flex-col gap-3">
              {memory.messages.map((message) => (
                <ConversationBubble key={message.id} message={message} />
              ))}
            </ol>
          </Panel>
        ) : (
          memory.content &&
          !isEditing && (
            <Panel title={contentTitle(memory.type)}>
              <p className="text-sm leading-relaxed whitespace-pre-wrap text-fg">{memory.content}</p>
            </Panel>
          )
        )}

        <Panel title="Details">
          <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Added" value={formatDateTime(memory.createdAt)} />
            <Detail label="Source" value={sourceLabel(memory.source)} />
            <Detail label="Processing" value={memory.processingStatus.toLowerCase()} />
            <Detail label="Memory ID" value={memory.id} mono />
          </dl>
        </Panel>
      </div>
    </ScrollPage>
  )
}

function EditMemoryForm({ memory, onClose }: { memory: Memory; onClose: () => void }) {
  const update = useUpdateMemory(memory.id)
  const [title, setTitle] = useState(memory.title ?? '')
  const [description, setDescription] = useState(memory.description ?? '')
  const [content, setContent] = useState(memory.content ?? '')
  const [occurredAt, setOccurredAt] = useState(instantToLocalInput(new Date(memory.occurredAt)))
  const isNote = memory.type === 'TEXT'
  const hasCaption = memory.type !== 'TEXT' && memory.type !== 'CONVERSATION'
  const error = update.error instanceof ApiRequestError ? update.error.message : null

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const when = localInputToInstant(occurredAt)
    await update.mutateAsync({
      title: title.trim(),
      occurredAt: when,
      ...(isNote ? { content } : {}),
      ...(hasCaption ? { description } : {}),
    })
    onClose()
  }

  const canSave = (!isNote || content.trim().length > 0) && Boolean(localInputToInstant(occurredAt))

  return (
    <Panel title="Edit memory">
      <form className="flex flex-col gap-4" onSubmit={onSubmit}>
        {error && <Alert>{error}</Alert>}
        <TextField label="Title" value={title} onChange={(event) => setTitle(event.target.value)} />
        <TextField
          label="When did this happen?"
          type="datetime-local"
          value={occurredAt}
          onChange={(event) => setOccurredAt(event.target.value)}
          required
        />
        {isNote && (
          <TextAreaField
            label="Note"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            rows={6}
            required
          />
        )}
        {hasCaption && (
          <TextAreaField
            label="Caption"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={3}
            hint="Shown under the photo. Search uses this too."
          />
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={update.isPending}>
            Cancel
          </Button>
          <Button type="submit" isLoading={update.isPending} disabled={!canSave}>
            Save
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function PeopleTagger({ memory }: { memory: Memory }) {
  const tag = useTagPerson(memory.id)
  const untag = useUntagPerson(memory.id)
  const { data: allPeople } = usePeople()
  const [draft, setDraft] = useState('')
  const people = memory.people ?? []

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const name = draft.trim()
    if (!name) return
    tag.mutate(name, { onSuccess: () => setDraft('') })
  }

  return (
    <Panel title="People">
      <div className="mb-3 flex flex-wrap gap-2">
        {people.length === 0 && <p className="text-sm text-fg-muted">No one tagged on this memory yet.</p>}
        {people.map((person) => (
          <span
            key={person.id}
            className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface-raised/50 px-2.5 py-1 text-sm text-fg"
          >
            <Link to={`/people/${person.id}`} className="hover:text-accent">
              {person.displayName}
            </Link>
            <button
              type="button"
              className="text-fg-muted hover:text-danger"
              aria-label={`Remove ${person.displayName}`}
              onClick={() => untag.mutate(person.id)}
            >
              ×
            </button>
          </span>
        ))}
      </div>
      <form className="flex flex-wrap gap-2" onSubmit={onSubmit}>
        <input
          list={`people-${memory.id}`}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Tag person (e.g. Raj, Aditya)"
          className="min-w-[12rem] flex-1 rounded-lg border border-line bg-ink/40 px-3 py-2 text-sm text-fg placeholder:text-fg-muted focus:border-accent focus:outline-none"
        />
        <datalist id={`people-${memory.id}`}>
          {(allPeople ?? []).map((person) => (
            <option key={person.id} value={person.displayName} />
          ))}
        </datalist>
        <Button type="submit" isLoading={tag.isPending} disabled={draft.trim().length === 0}>
          Tag
        </Button>
      </form>
      <p className="mt-2 text-xs text-fg-muted">
        This tags the whole memory. To name a face, click the box or review all unlabeled faces in{' '}
        <Link to="/faces" className="text-accent hover:underline">
          Faces
        </Link>
        .
      </p>
      {tag.isError && <Alert>Could not tag that person.</Alert>}
    </Panel>
  )
}

function PhotoWithFaces({ memory, asset }: { memory: Memory; asset: MediaAsset }) {
  const faces = (memory.faces ?? []).filter((face) => face.assetId === asset.id)
  const confirm = useConfirmFace(memory.id)
  const clearFace = useClearFace(memory.id)
  const rejectSuggestion = useRejectFaceSuggestion()
  const ignoreFace = useIgnoreFace()
  const restoreFace = useRestoreFace()
  const [namingFaceId, setNamingFaceId] = useState<string | null>(null)
  const [nameDraft, setNameDraft] = useState('')
  const namedAs = confirm.data?.personName
  const stillTagged = namedAs && memory.faces.some((face) => face.personName === namedAs)
  const alsoSuggested = confirm.data?.alsoSuggested ?? 0
  const confirmError = confirm.error instanceof ApiRequestError ? confirm.error.message : null
  const clearError = clearFace.error instanceof ApiRequestError ? clearFace.error.message : null
  const rejectError = rejectSuggestion.error instanceof ApiRequestError ? rejectSuggestion.error.message : null
  const ignoreError = ignoreFace.error instanceof ApiRequestError ? ignoreFace.error.message : null
  const restoreError = restoreFace.error instanceof ApiRequestError ? restoreFace.error.message : null

  return (
    <div className="flex flex-col gap-3">
      <div className="relative overflow-hidden rounded-panel border border-line">
        <AuthenticatedImage
          downloadPath={asset.downloadPath}
          alt={memory.title ?? asset.fileName}
          className="block h-auto w-full max-h-[70vh]"
        />
        {faces.map((face) => (
          <button
            key={face.id}
            type="button"
            title={faceLabel(face)}
            onClick={() => {
              if (face.ignored) return
              setNamingFaceId(face.id)
              setNameDraft(face.personName ?? face.suggestedPersonName ?? '')
            }}
            className={`absolute border-2 text-left transition-colors ${
              face.ignored ? 'border-fg-muted/50 bg-ink/20 hover:bg-ink/30' : 'bg-accent/10 hover:bg-accent/20'
            }`}
            style={{
              left: `${face.x * 100}%`,
              top: `${face.y * 100}%`,
              width: `${face.width * 100}%`,
              height: `${face.height * 100}%`,
              borderColor: face.personName
                ? 'var(--color-support)'
                : face.ignored
                  ? 'color-mix(in srgb, var(--color-fg-muted) 70%, transparent)'
                  : 'color-mix(in srgb, var(--color-accent) 90%, transparent)',
            }}
          >
            <span className="absolute inset-x-0 bottom-0 truncate bg-ink/75 px-1 py-0.5 text-[10px] leading-tight text-fg">
              {faceLabel(face)}
            </span>
          </button>
        ))}
      </div>

      {faces.length > 0 && (
        <ul className="flex flex-col gap-2">
          {faces.map((face) => (
            <li
              key={face.id}
              className={`flex flex-wrap items-center gap-2 rounded-lg border px-3 py-2 text-sm ${
                face.ignored ? 'border-line/40 bg-ink/10 text-fg-muted' : 'border-line/70 bg-ink/25'
              }`}
            >
              <span className={face.ignored ? 'text-fg-muted' : 'text-fg'}>{faceLabel(face)}</span>
              {face.personId ? (
                <>
                  <Link to={`/people/${face.personId}`} className="text-xs text-accent hover:underline">
                    Open person
                  </Link>
                  <button
                    type="button"
                    className="text-xs text-fg-muted hover:text-danger hover:underline"
                    disabled={clearFace.isPending}
                    onClick={() => clearFace.mutate(face.id)}
                  >
                    Clear name
                  </button>
                </>
              ) : face.ignored ? (
                <button
                  type="button"
                  className="text-xs text-accent hover:underline"
                  disabled={restoreFace.isPending}
                  onClick={() => restoreFace.mutate(face.id)}
                >
                  Show in Faces again
                </button>
              ) : (
                <>
                  {face.suggestedPersonId && face.suggestedPersonName && (
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
                        disabled={rejectSuggestion.isPending}
                        onClick={() => rejectSuggestion.mutate(face.id)}
                      >
                        Not them
                      </button>
                    </>
                  )}
                  {namingFaceId === face.id ? (
                    <form
                      className="flex flex-wrap gap-2"
                      onSubmit={(event) => {
                        event.preventDefault()
                        const name = nameDraft.trim()
                        if (!name) return
                        confirm.mutate(
                          { faceId: face.id, displayName: name },
                          { onSuccess: () => setNamingFaceId(null) },
                        )
                      }}
                    >
                      <input
                        value={nameDraft}
                        onChange={(e) => setNameDraft(e.target.value)}
                        placeholder="Name this face"
                        className="rounded-lg border border-line bg-ink/40 px-2 py-1 text-sm text-fg"
                        autoFocus
                      />
                      <Button type="submit" isLoading={confirm.isPending}>
                        Save
                      </Button>
                    </form>
                  ) : (
                    <button
                      type="button"
                      className="text-xs text-accent hover:underline"
                      onClick={() => {
                        setNamingFaceId(face.id)
                        setNameDraft('')
                      }}
                    >
                      Who is this?
                    </button>
                  )}
                  <button
                    type="button"
                    className="text-xs text-fg-muted hover:text-danger hover:underline"
                    disabled={ignoreFace.isPending}
                    onClick={() => ignoreFace.mutate(face.id)}
                  >
                    Don't name
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {stillTagged && (
        <p className="text-sm text-support">
          Tagged as {namedAs}
          {alsoSuggested > 0
            ? ` · ${alsoSuggested} other ${alsoSuggested === 1 ? 'face looks' : 'faces look'} like them too — open those photos to confirm.`
            : ' · other photos of them will show “Looks like…” once they match.'}
        </p>
      )}
      {confirmError && <Alert>{confirmError}</Alert>}
      {clearError && <Alert>{clearError}</Alert>}
      {rejectError && <Alert>{rejectError}</Alert>}
      {ignoreError && <Alert>{ignoreError}</Alert>}
      {restoreError && <Alert>{restoreError}</Alert>}
    </div>
  )
}

function faceLabel(face: FaceDetection): string {
  if (face.personName) return face.personName
  if (face.ignored) return 'Skipped'
  if (face.suggestedPersonName) {
    const pct =
      face.confidence != null ? ` · ${Math.round(face.confidence * 100)}%` : ''
    return `Looks like ${face.suggestedPersonName}${pct}`
  }
  return 'Unknown face'
}

function ConversationBubble({ message }: { message: ConversationMessage }) {
  return (
    <li className="rounded-xl border border-line bg-surface-raised/40 px-3.5 py-2.5">
      <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
        <span className="text-sm font-medium text-fg">{message.senderName}</span>
        <time dateTime={message.sentAt} className="text-xs text-fg-muted">
          {formatTime(message.sentAt)}
        </time>
      </div>
      <p className="text-sm leading-relaxed whitespace-pre-wrap text-fg">{message.body}</p>
    </li>
  )
}

function contentTitle(type: Memory['type']): string {
  switch (type) {
    case 'CONVERSATION':
      return 'Conversation'
    case 'TEXT':
      return 'Note'
    default:
      return 'Content'
  }
}

function sourceLabel(source: Memory['source']): string {
  switch (source) {
    case 'UPLOAD':
      return 'Uploaded file'
    case 'IMPORT':
      return 'Imported'
    case 'MANUAL':
      return 'Written by you'
    default:
      return source
  }
}

function Detail({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs tracking-wide text-fg-muted uppercase">{label}</dt>
      <dd className={`text-fg ${mono ? 'font-mono text-xs break-all' : ''}`}>{value}</dd>
    </div>
  )
}

function describeAsset(asset: MediaAsset): string {
  if (asset.fileName.startsWith('face-frame-')) {
    return 'Still from this video · used to find faces'
  }
  const parts = [asset.fileName, formatFileSize(asset.sizeBytes)]
  if (asset.widthPx && asset.heightPx) {
    parts.push(`${asset.widthPx} × ${asset.heightPx}`)
  }
  if (asset.latitude != null && asset.longitude != null) {
    parts.push(`${asset.latitude.toFixed(4)}, ${asset.longitude.toFixed(4)}`)
  }
  return parts.join(' · ')
}
