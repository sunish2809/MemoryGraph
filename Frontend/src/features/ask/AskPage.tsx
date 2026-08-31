import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { EmptyState } from '@/components/ui/EmptyState'
import { Panel } from '@/components/ui/Panel'
import { accountApi } from '@/features/account/accountApi'
import { AskBar } from '@/features/ask/AskBar'
import { AskSourceCard } from '@/features/ask/AskSourceCard'
import { useAsk } from '@/features/ask/useAsk'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { VIEWER_TIME_ZONE } from '@/lib/format'
import type { AskSource } from '@/types/api'

/**
 * Ask: retrieve relevant memories, then answer from them.
 * Questions only run when the user submits — not on page load or navigation.
 */
export function AskPage() {
  const [draft, setDraft] = useState('')
  const ask = useAsk()
  const result = ask.data
  const requestError = ask.error instanceof ApiRequestError ? ask.error : null
  const { data: privacy } = useQuery({
    queryKey: ['account', 'privacy'],
    queryFn: accountApi.privacy,
  })
  const chatOn = privacy?.languageModelEnabled
  const emptyDescription =
    chatOn === true
      ? 'Answers are grounded in memories you have saved. If OpenAI is unavailable, you still get the matching memories listed.'
      : chatOn === false
        ? 'A language model is not configured on this server. Ask still searches your archive and lists the matching memories instead of paraphrasing them.'
        : 'Answers are grounded in memories you have saved. Chat days show messages; photos show images.'

  return (
    <PageShell
      title="Ask AI"
      description="Questions are answered from your own memories, with chat lines and photos shown as sources."
      toolbar={
        <AskBar
          autoFocus
          value={draft}
          onChange={setDraft}
          onSubmit={() => {
            const trimmed = draft.trim()
            if (trimmed) {
              ask.mutate({ question: trimmed, zone: VIEWER_TIME_ZONE })
            }
          }}
          isLoading={ask.isPending}
        />
      }
    >
      {!result && !ask.isPending && !requestError && (
        <EmptyState
          icon="✦"
          title="Ask something about your past"
          description={emptyDescription}
          footnote={
            <Link to="/privacy" className="text-accent hover:underline">
              See how Ask uses your data
            </Link>
          }
        />
      )}

      {requestError && <Alert>{requestError.message}</Alert>}

      {ask.isPending && (
        <div className="h-40 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />
      )}

      {result && !ask.isPending && (
        <div className="flex flex-col gap-4">
          {result.notice && <Alert tone="info">{result.notice}</Alert>}
          <Panel title="Answer">
            <p className="mb-3 text-sm text-fg-muted">
              {result.grounded
                ? `Grounded in ${result.sources.length} ${result.sources.length === 1 ? 'memory' : 'memories'}`
                : 'No matching memories'}
              {result.model === 'retrieval-only'
                ? ' · language model not used — showing retrieved memories'
                : ` · ${result.model}`}
            </p>
            <div className="whitespace-pre-wrap text-base leading-relaxed text-fg">{result.answer}</div>
          </Panel>

          {result.sources.length > 0 && (
            <section className="flex flex-col gap-3">
              <h2 className="text-xs font-semibold tracking-wide text-fg-muted uppercase">Sources</h2>
              <div className="flex flex-col gap-3">
                {visibleSources(result.sources).map((source) => (
                  <AskSourceCard key={source.id} source={source} />
                ))}
              </div>
            </section>
          )}
        </div>
      )}
    </PageShell>
  )
}

/** Photos already shown under a chat day are not repeated as standalone cards. */
function visibleSources(sources: AskSource[]) {
  const nestedPhotoIds = new Set(sources.flatMap((source) => source.relatedPhotos.map((photo) => photo.id)))
  return sources.filter((source) => !(source.type === 'PHOTO' && nestedPhotoIds.has(source.id)))
}
