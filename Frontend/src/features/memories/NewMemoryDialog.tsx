import { useState, type ChangeEvent, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { TextAreaField } from '@/components/ui/TextAreaField'
import { TextField } from '@/components/ui/TextField'
import { useCreateTextMemory, useUploadMemory } from '@/features/memories/useMemories'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { formatFileSize, instantToLocalInput, localInputToInstant } from '@/lib/format'

type Mode = 'note' | 'media'

interface NewMemoryDialogProps {
  isOpen: boolean
  onClose: () => void
}

/** Matches the backend's allowlist, so the file picker offers only what the server will accept. */
const ACCEPTED_MEDIA_TYPES =
  'image/jpeg,image/png,image/gif,image/webp,image/heic,image/heif,.heic,.heif,video/mp4,video/quicktime,video/webm,audio/mpeg,audio/mp4,audio/wav,audio/x-wav,.mp3,.m4a,.wav,.mp4,.mov,.webm'

export function NewMemoryDialog({ isOpen, onClose }: NewMemoryDialogProps) {
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('note')

  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [occurredAt, setOccurredAt] = useState('')
  const [file, setFile] = useState<File>()
  const [progress, setProgress] = useState<number>()
  const [error, setError] = useState<string>()

  const createNote = useCreateTextMemory()
  const upload = useUploadMemory()
  const isSaving = createNote.isPending || upload.isPending

  function reset() {
    setTitle('')
    setContent('')
    setOccurredAt('')
    setFile(undefined)
    setProgress(undefined)
    setError(undefined)
  }

  function close() {
    reset()
    onClose()
  }

  function onFileSelected(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0]
    setFile(selected)
    setError(undefined)
    // A file's own name is usually a better first title than an empty box.
    if (selected && !title) {
      setTitle(selected.name.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' '))
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(undefined)

    try {
      const memory =
        mode === 'note'
          ? await createNote.mutateAsync({
              title: title.trim() || undefined,
              content: content.trim(),
              occurredAt: localInputToInstant(occurredAt),
            })
          : await upload.mutateAsync({
              payload: {
                file: file!,
                title: title.trim() || undefined,
                occurredAt: localInputToInstant(occurredAt),
              },
              onProgress: setProgress,
            })

      close()
      navigate(`/memories/${memory.id}`)
    } catch (caught) {
      setProgress(undefined)
      setError(
        caught instanceof ApiRequestError
          ? caught.message
          : 'Something went wrong while saving. Please try again.',
      )
    }
  }

  const canSubmit = mode === 'note' ? content.trim().length > 0 : Boolean(file)

  return (
    <Modal title="Add a memory" isOpen={isOpen} onClose={close}>
      <form onSubmit={onSubmit} className="flex flex-col gap-5">
        <div role="tablist" aria-label="Kind of memory" className="flex gap-1 rounded-xl bg-surface-raised p-1">
          <ModeTab mode="note" label="Write a note" current={mode} onSelect={setMode} />
          <ModeTab mode="media" label="Upload media" current={mode} onSelect={setMode} />
        </div>

        {error && <Alert tone="danger">{error}</Alert>}

        {mode === 'note' ? (
          <TextAreaField
            label="What happened?"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            rows={6}
            placeholder="Walked up to the ridge before sunrise. The whole valley was under cloud."
            required
            autoFocus
          />
        ) : (
          <FilePicker file={file} progress={progress} onChange={onFileSelected} />
        )}

        <TextField
          label="Title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Optional"
        />

        <TextField
          label="When did this happen?"
          type="datetime-local"
          value={occurredAt}
          max={instantToLocalInput(new Date())}
          onChange={(event) => setOccurredAt(event.target.value)}
          hint="Leave empty for now. Set it to place an older memory correctly on your timeline."
        />

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={close} disabled={isSaving}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSaving} disabled={!canSubmit}>
            {mode === 'note' ? 'Save note' : 'Upload'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function ModeTab({
  mode,
  label,
  current,
  onSelect,
}: {
  mode: Mode
  label: string
  current: Mode
  onSelect: (mode: Mode) => void
}) {
  const isActive = current === mode

  return (
    <button
      type="button"
      role="tab"
      aria-selected={isActive}
      onClick={() => onSelect(mode)}
      className={`flex-1 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
        isActive ? 'bg-surface text-fg shadow-sm' : 'text-fg-muted hover:text-fg'
      }`}
    >
      {label}
    </button>
  )
}

function FilePicker({
  file,
  progress,
  onChange,
}: {
  file: File | undefined
  progress: number | undefined
  onChange: (event: ChangeEvent<HTMLInputElement>) => void
}) {
  return (
    <div className="flex flex-col gap-2">
      <label className="text-sm font-medium text-fg" htmlFor="memory-file">
        Photo, video or audio
      </label>
      <input
        id="memory-file"
        type="file"
        accept={ACCEPTED_MEDIA_TYPES}
        onChange={onChange}
        className="rounded-xl border border-line bg-surface px-3.5 py-2.5 text-sm text-fg-muted file:mr-3 file:rounded-lg file:border-0 file:bg-accent file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-white hover:file:bg-accent-strong"
      />
      {file && (
        <p className="text-xs text-fg-muted">
          {file.name} · {formatFileSize(file.size)}
        </p>
      )}
      {progress !== undefined && (
        <div className="h-1.5 overflow-hidden rounded-full bg-surface-raised" aria-hidden>
          <div className="h-full bg-accent transition-[width]" style={{ width: `${progress}%` }} />
        </div>
      )}
      <p className="text-xs text-fg-muted">
        Images, MP4/MOV/WebM, MP3/M4A/WAV. With an OpenAI key, audio/video are transcribed for search
        and Ask.
      </p>
    </div>
  )
}
