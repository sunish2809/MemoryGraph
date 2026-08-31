import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'

import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Panel } from '@/components/ui/Panel'
import { TextField } from '@/components/ui/TextField'
import { PageShell } from '@/components/layout/PageShell'
import {
  invalidateMemoriesAfterImport,
  useCreatePickerSession,
  useDeleteImport,
  useGoogleAuthorize,
  useGoogleDisconnect,
  useGoogleIntegration,
  useGooglePhotosImport,
  useImportJob,
  useImportList,
  useImportPickerSession,
  usePickerSession,
  useWhatsAppImport,
} from '@/features/imports/useImports'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { formatFileSize, VIEWER_TIME_ZONE } from '@/lib/format'

type Source = 'whatsapp' | 'google-photos'

/**
 * Upload WhatsApp exports, Google Takeout zips, or pick live via Google Photos Picker OAuth.
 */
export function ImportPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [source, setSource] = useState<Source>('whatsapp')
  const [file, setFile] = useState<File>()
  const [zone, setZone] = useState(VIEWER_TIME_ZONE)
  const [progress, setProgress] = useState<number>()
  const [error, setError] = useState<string>()
  const [importId, setImportId] = useState<string>()
  const [pickerSessionId, setPickerSessionId] = useState<string>()
  const [pickerPolling, setPickerPolling] = useState(false)

  const whatsapp = useWhatsAppImport()
  const googlePhotos = useGooglePhotosImport()
  const google = useGoogleIntegration()
  const authorize = useGoogleAuthorize()
  const disconnect = useGoogleDisconnect()
  const createPicker = useCreatePickerSession()
  const importPicker = useImportPickerSession()
  const startPending = whatsapp.isPending || googlePhotos.isPending || importPicker.isPending
  const { data: job } = useImportJob(importId)
  const { data: pickerSession } = usePickerSession(pickerSessionId, pickerPolling)
  const pollImports =
    Boolean(importId) &&
    (!job || job.status === 'PENDING' || job.status === 'PROCESSING')
  const { data: pastImports } = useImportList(pollImports)
  const deleteImport = useDeleteImport()
  const [confirmImportId, setConfirmImportId] = useState<string | null>(null)

  // Prefer live poll; fall back to past-imports row (and prefer newer status if list is fresher).
  const listed = importId ? pastImports?.find((item) => item.id === importId) : undefined
  const liveJob =
    listed && job
      ? statusRank(listed.status) >= statusRank(job.status)
        ? listed
        : job
      : (job ?? listed)

  useEffect(() => {
    if (searchParams.get('google') === 'connected') {
      setSource('google-photos')
    }
  }, [searchParams])

  useEffect(() => {
    if (liveJob?.status === 'COMPLETED') {
      invalidateMemoriesAfterImport(queryClient)
      void queryClient.invalidateQueries({ queryKey: ['imports', 'list'] })
    }
  }, [liveJob?.status, queryClient])

  useEffect(() => {
    if (listed && importId && (listed.status === 'COMPLETED' || listed.status === 'FAILED')) {
      queryClient.setQueryData(['imports', importId], listed)
    }
  }, [listed, importId, queryClient])

  useEffect(() => {
    if (pickerSession?.mediaItemsSet) {
      setPickerPolling(false)
    }
  }, [pickerSession?.mediaItemsSet])

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(undefined)
    if (!file) {
      setError(source === 'whatsapp' ? 'Choose a WhatsApp .txt or .zip first.' : 'Choose a Takeout .zip first.')
      return
    }
    try {
      const payload = {
        file,
        zone: zone.trim() || VIEWER_TIME_ZONE,
        onProgress: setProgress,
      }
      const created =
        source === 'whatsapp'
          ? await whatsapp.mutateAsync(payload)
          : await googlePhotos.mutateAsync(payload)
      setImportId(created.id)
      setProgress(undefined)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Import failed')
      setProgress(undefined)
    }
  }

  function onFileSelected(event: ChangeEvent<HTMLInputElement>) {
    setFile(event.target.files?.[0])
    setError(undefined)
    setImportId(undefined)
  }

  function switchSource(next: Source) {
    setSource(next)
    setFile(undefined)
    setError(undefined)
    setImportId(undefined)
    setProgress(undefined)
    setPickerSessionId(undefined)
    setPickerPolling(false)
  }

  async function onConnectGoogle() {
    setError(undefined)
    try {
      const { authorizationUrl } = await authorize.mutateAsync()
      window.location.assign(authorizationUrl)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Could not start Google sign-in')
    }
  }

  async function onStartPicker() {
    setError(undefined)
    try {
      const session = await createPicker.mutateAsync()
      setPickerSessionId(session.sessionId)
      setPickerPolling(true)
      window.open(session.pickerUri, '_blank', 'noopener,noreferrer')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Could not open Google Photos picker')
    }
  }

  async function onImportPicked() {
    if (!pickerSessionId) {
      return
    }
    setError(undefined)
    try {
      const created = await importPicker.mutateAsync({
        sessionId: pickerSessionId,
        zone: zone.trim() || VIEWER_TIME_ZONE,
      })
      setImportId(created.id)
      setPickerSessionId(undefined)
      setPickerPolling(false)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : 'Could not import picked photos')
    }
  }

  const googleConfigured = google.data?.configured ?? false
  const googleConnected = google.data?.connected ?? false

  return (
    <PageShell
      title="Import"
      description="Bring WhatsApp chats or Google Photos onto your timeline (Takeout zip or live Picker)."
    >
      <div className="flex flex-col gap-4">
      <Panel title="Source">
        <div role="tablist" className="mb-4 flex gap-1 rounded-lg bg-surface-raised p-1">
          <SourceTab label="WhatsApp" active={source === 'whatsapp'} onClick={() => switchSource('whatsapp')} />
          <SourceTab
            label="Google Photos"
            active={source === 'google-photos'}
            onClick={() => switchSource('google-photos')}
          />
        </div>

        {source === 'google-photos' && (
          <div className="mb-6 rounded-lg border border-line/70 bg-ink/30 p-4">
            <h2 className="text-sm font-medium">Live pick (Photos Picker)</h2>
            <p className="mt-1 text-xs text-fg-muted">
              Choose photos in Google&apos;s UI. Continuous library sync is not available to apps —
              this is interactive selection only.
            </p>
            {!google.isLoading && !googleConfigured && (
              <p className="mt-3 text-xs text-fg-muted">
                Picker needs <code className="text-fg">GOOGLE_CLIENT_ID</code> /{' '}
                <code className="text-fg">GOOGLE_CLIENT_SECRET</code> in{' '}
                <code className="text-fg">.env</code>. Takeout upload below still works without them.
              </p>
            )}
            {googleConfigured && !googleConnected && (
              <div className="mt-3">
                <Button type="button" onClick={() => void onConnectGoogle()} disabled={authorize.isPending}>
                  {authorize.isPending ? 'Redirecting…' : 'Connect Google'}
                </Button>
              </div>
            )}
            {googleConnected && (
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <Button type="button" onClick={() => void onStartPicker()} disabled={createPicker.isPending}>
                  {createPicker.isPending ? 'Opening…' : 'Pick photos in Google'}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => void disconnect.mutateAsync()}
                  disabled={disconnect.isPending}
                >
                  Disconnect
                </Button>
              </div>
            )}
            {pickerSessionId && (
              <div className="mt-3 space-y-2 text-sm">
                {pickerPolling && !pickerSession?.mediaItemsSet && (
                  <p className="text-fg-muted">Waiting for you to finish selecting in Google Photos…</p>
                )}
                {pickerSession?.mediaItemsSet && (
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-fg-muted">Selection ready.</span>
                    <Button type="button" onClick={() => void onImportPicked()} disabled={importPicker.isPending}>
                      {importPicker.isPending ? 'Queuing…' : 'Import selection'}
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={onSubmit}>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-fg-muted">
              {source === 'whatsapp' ? 'Chat file (.txt or .zip)' : 'Takeout archive (.zip) — bulk path'}
            </span>
            <input
              type="file"
              accept={source === 'whatsapp' ? '.txt,.zip,text/plain,application/zip' : '.zip,application/zip'}
              onChange={onFileSelected}
              className="text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-surface-raised file:px-3 file:py-1.5"
            />
            {file && (
              <span className="text-xs text-fg-muted">
                {file.name} · {formatFileSize(file.size)}
              </span>
            )}
          </label>

          <TextField
            label="Timezone"
            value={zone}
            onChange={(event) => setZone(event.target.value)}
            hint={
              source === 'whatsapp'
                ? 'WhatsApp timestamps have no zone; we interpret them in this IANA timezone.'
                : 'Used when Takeout / Picker lack a capture time.'
            }
          />

          {error && <Alert>{error}</Alert>}

          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" disabled={startPending || !file}>
              {startPending && file ? 'Uploading…' : 'Start Takeout / file import'}
            </Button>
            {typeof progress === 'number' && (
              <span className="text-xs text-fg-muted">{progress}% uploaded</span>
            )}
          </div>
        </form>
      </Panel>

      {!importId && source === 'whatsapp' && (
        <Panel>
          <EmptyState
            icon="⤓"
            title="How to export from WhatsApp"
            description="In the chat, open the menu → More → Export chat. Without media is fine for text; with media attaches photos when the zip includes them."
          />
        </Panel>
      )}

      {!importId && source === 'google-photos' && (
        <Panel>
          <EmptyState
            icon="⤓"
            title="How to export Google Photos (bulk)"
            description="For large libraries use Google Takeout: select Google Photos → export → upload the .zip here (up to 1.5GB). Or Connect Google above and pick a smaller set live."
            footnote={
              <a
                className="text-accent hover:underline"
                href="https://takeout.google.com/"
                target="_blank"
                rel="noreferrer"
              >
                Open Google Takeout
              </a>
            }
          />
        </Panel>
      )}

      {liveJob && (
        <Panel title="Import status">
          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-fg-muted">Status</dt>
              <dd className="font-medium">{liveJob.status}</dd>
            </div>
            <div>
              <dt className="text-fg-muted">Kind</dt>
              <dd>{kindLabel(liveJob.kind)}</dd>
            </div>
            <div>
              <dt className="text-fg-muted">File</dt>
              <dd>{liveJob.fileName}</dd>
            </div>
            {liveJob.chatName && (
              <div>
                <dt className="text-fg-muted">Label</dt>
                <dd>{liveJob.chatName}</dd>
              </div>
            )}
            <div>
              <dt className="text-fg-muted">Memories created</dt>
              <dd>{liveJob.memoriesCreated}</dd>
            </div>
          </dl>
          {liveJob.status === 'FAILED' && liveJob.errorMessage && (
            <div className="mt-4">
              <Alert>{liveJob.errorMessage}</Alert>
            </div>
          )}
          {liveJob.status === 'COMPLETED' && (
            <p className="mt-4 text-sm text-fg-muted">
              Done.{' '}
              <Link className="text-accent hover:underline" to="/timeline">
                Open timeline
              </Link>{' '}
              or{' '}
              <Link className="text-accent hover:underline" to="/search">
                search
              </Link>
              .
            </p>
          )}
          {(liveJob.status === 'PENDING' || liveJob.status === 'PROCESSING') && (
            <p className="mt-4 text-sm text-fg-muted">
              {liveJob.kind === 'WHATSAPP'
                ? 'Parsing your chat… days appear on the timeline as they finish. This page updates when the whole import completes.'
                : 'Importing photos…'}
            </p>
          )}
        </Panel>
      )}

      {pastImports && pastImports.length > 0 && (
        <Panel title="Past imports">
          <p className="mb-3 text-sm text-fg-muted">
            Delete an import to remove its memories and free the file for re-upload.
          </p>
          <ul className="flex flex-col gap-2">
            {pastImports.map((item) => (
              <li
                key={item.id}
                className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-line/60 bg-ink/30 px-3 py-2 text-sm"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-fg">
                    {item.chatName || item.fileName}
                  </p>
                  <p className="text-xs text-fg-muted">
                    {kindLabel(item.kind)} · {item.status} · {item.memoriesCreated} memories
                  </p>
                </div>
                {confirmImportId === item.id ? (
                  <span className="flex items-center gap-2 text-xs">
                    <button type="button" className="text-fg-muted" onClick={() => setConfirmImportId(null)}>
                      Keep
                    </button>
                    <Button
                      type="button"
                      className="bg-danger shadow-none hover:bg-danger/85"
                      isLoading={deleteImport.isPending}
                      onClick={() => {
                        deleteImport.mutate(item.id, {
                          onSuccess: () => {
                            setConfirmImportId(null)
                            if (importId === item.id) setImportId(undefined)
                          },
                        })
                      }}
                    >
                      Delete
                    </Button>
                  </span>
                ) : (
                  <button
                    type="button"
                    className="text-xs text-fg-muted hover:text-danger"
                    onClick={() => setConfirmImportId(item.id)}
                  >
                    Delete
                  </button>
                )}
              </li>
            ))}
          </ul>
        </Panel>
      )}
      </div>
    </PageShell>
  )
}

function kindLabel(kind: string) {
  if (kind === 'GOOGLE_PHOTOS_PICKER') {
    return 'Google Photos (Picker)'
  }
  if (kind === 'GOOGLE_PHOTOS') {
    return 'Google Photos (Takeout)'
  }
  return 'WhatsApp'
}

function statusRank(status: string): number {
  switch (status) {
    case 'COMPLETED':
    case 'FAILED':
      return 2
    case 'PROCESSING':
      return 1
    default:
      return 0
  }
}

function SourceTab({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={`flex-1 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
        active ? 'bg-surface text-fg shadow-sm' : 'text-fg-muted hover:text-fg'
      }`}
    >
      {label}
    </button>
  )
}
