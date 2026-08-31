import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { ScrollPage } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Panel } from '@/components/ui/Panel'
import { TextField } from '@/components/ui/TextField'
import { accountApi } from '@/features/account/accountApi'
import { useAuth } from '@/features/auth/useAuth'
import { ApiRequestError } from '@/lib/ApiRequestError'

export function PrivacyPage() {
  const { data: privacy } = useQuery({
    queryKey: ['account', 'privacy'],
    queryFn: accountApi.privacy,
  })
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [exportError, setExportError] = useState<string | null>(null)
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')

  const exporting = useMutation({
    mutationFn: accountApi.exportArchive,
    onSuccess: (blob) => {
      setExportError(null)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `memorygraph-archive.zip`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    },
    onError: (error) => {
      setExportError(error instanceof ApiRequestError ? error.message : 'Export failed')
    },
  })

  const deleting = useMutation({
    mutationFn: () => accountApi.deleteAccount(password),
    onSuccess: () => {
      logout()
      navigate('/login', { replace: true })
    },
  })

  const deleteError = deleting.error instanceof ApiRequestError ? deleting.error : null
  const chatOn = privacy?.languageModelEnabled === true
  const facesOn = privacy?.facesEnabled !== false

  return (
    <ScrollPage>
      <header>
        <h1 className="font-display text-2xl font-semibold tracking-tight text-fg">Privacy</h1>
        <p className="mt-1 max-w-2xl text-sm text-fg-muted">
          What this copy of MemoryGraph does with {user?.displayName ? `${user.displayName}'s` : 'your'}{' '}
          archive — before anyone else uses it.
        </p>
      </header>

      <Panel title="Where your data lives">
        <ul className="flex flex-col gap-3 text-sm leading-relaxed text-fg">
          <li>
            Memories, photos, chats, people and places are stored on the machine that runs this app
            (Postgres and local files). There is no MemoryGraph cloud.
          </li>
          <li>
            {facesOn
              ? 'Face detection runs locally (InsightFace on this computer). Photos are not sent to a face-recognition service.'
              : 'Face detection is turned off on this server. You can still tag people by name.'}
          </li>
          <li>
            {chatOn
              ? `Ask can send your question and the retrieved memory text to OpenAI (${privacy?.languageModel}) to phrase an answer. Retrieval still happens here. If OpenAI refuses (quota or an outage), Ask lists the matching memories instead of inventing a reply.`
              : 'Ask searches your archive here. A language model is not configured, so answers list the matching memories instead of paraphrasing them. Semantic (paraphrase) search also needs an OpenAI embedding key — without it, shared words still find memories.'}
          </li>
          <li>
            Signing in uses a token on this device. Other people need an account on this same server;
            they cannot see your memories.
          </li>
        </ul>
      </Panel>

      <Panel title="Export a copy">
        <p className="mb-4 text-sm leading-relaxed text-fg-muted">
          Download a zip of your memories (JSON) and the photo/file bytes. This is one account’s
          portable copy — not a full server backup. Volume backup is documented in the project README.
        </p>
        {exportError && <Alert>{exportError}</Alert>}
        <Button
          type="button"
          variant="secondary"
          isLoading={exporting.isPending}
          onClick={() => exporting.mutate()}
        >
          Download archive
        </Button>
      </Panel>

      <Panel title="Delete this account">
        <p className="mb-4 text-sm leading-relaxed text-fg-muted">
          Permanently removes your memories, files, people, places and this login. Export first if you
          want a copy. Type DELETE and your password to confirm.
        </p>
        {deleteError && <Alert>{deleteError.message}</Alert>}
        <form
          className="flex max-w-md flex-col gap-3"
          onSubmit={(event) => {
            event.preventDefault()
            if (confirmation.trim() === 'DELETE') {
              deleting.mutate()
            }
          }}
        >
          <TextField
            label="Type DELETE"
            autoComplete="off"
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <Button
            type="submit"
            variant="danger"
            isLoading={deleting.isPending}
            disabled={confirmation.trim() !== 'DELETE' || password.length === 0}
          >
            Delete my account
          </Button>
        </form>
      </Panel>
    </ScrollPage>
  )
}
