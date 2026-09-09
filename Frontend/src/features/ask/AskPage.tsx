import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { accountApi } from '@/features/account/accountApi'
import { AskBar } from '@/features/ask/AskBar'
import { AskMarkdown } from '@/features/ask/AskMarkdown'
import { AskSourceCard } from '@/features/ask/AskSourceCard'
import { askApi } from '@/features/ask/askApi'
import { useAsk } from '@/features/ask/useAsk'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { VIEWER_TIME_ZONE } from '@/lib/format'
import type { AskHistoryTurn, AskQuota, AskResponse, AskSource } from '@/types/api'

type ThreadTurn = {
  id: string
  question: string
  response?: AskResponse
  error?: string
}

/**
 * Chat-style Ask: each question stays in the thread so a follow-up does not wipe the last answer.
 */
export function AskPage() {
  const [draft, setDraft] = useState('')
  const [turns, setTurns] = useState<ThreadTurn[]>([])
  const ask = useAsk()
  const scroller = useRef<HTMLDivElement>(null)
  const { data: privacy } = useQuery({
    queryKey: ['account', 'privacy'],
    queryFn: accountApi.privacy,
  })
  const { data: loadedQuota } = useQuery({
    queryKey: ['ask', 'quota'],
    queryFn: askApi.quota,
  })
  const lastQuota = [...turns].reverse().find((turn) => turn.response?.quota)?.response?.quota
  const quota = lastQuota ?? loadedQuota
  const chatOn = privacy?.languageModelEnabled
  const emptyDescription = askEmptyDescription(chatOn, quota)
  const pending = ask.isPending

  useEffect(() => {
    scroller.current?.scrollTo({ top: scroller.current.scrollHeight, behavior: 'smooth' })
  }, [turns, pending])

  function submit(question: string) {
    const trimmed = question.trim()
    if (!trimmed || pending) return
    const id = crypto.randomUUID()
    const history = toHistory(turns)
    setTurns((current) => [...current, { id, question: trimmed }])
    setDraft('')
    ask.mutate(
      { question: trimmed, zone: VIEWER_TIME_ZONE, history },
      {
        onSuccess: (response) => {
          setTurns((current) => current.map((turn) => (turn.id === id ? { ...turn, response } : turn)))
        },
        onError: (error) => {
          const message = error instanceof ApiRequestError ? error.message : 'That question could not be answered.'
          setTurns((current) => current.map((turn) => (turn.id === id ? { ...turn, error: message } : turn)))
        },
      },
    )
  }

  return (
    <PageShell
      title="Ask AI"
      description="A chat over your own memories. Follow-ups stay in this thread; sources stay attached to each answer."
      bodyRef={scroller}
      actions={
        turns.length > 0 ? (
          <Button
            variant="ghost"
            className="!px-2 !py-1.5 text-xs"
            disabled={pending}
            onClick={() => setTurns([])}
          >
            New chat
          </Button>
        ) : null
      }
      toolbar={quotaMeter(quota)}
      footer={
        <div className="px-1 py-3">
          <AskBar
            autoFocus
            value={draft}
            onChange={setDraft}
            onSubmit={() => submit(draft)}
            isLoading={pending}
            showExamples={turns.length === 0}
            placeholder={turns.length === 0 ? 'Ask anything about your memories…' : 'Ask a follow-up…'}
          />
        </div>
      }
    >
      <div className="flex min-h-full flex-col gap-5">
        {turns.length === 0 && !pending && (
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

        {turns.map((turn) => (
          <ThreadMessage key={turn.id} turn={turn} pending={!turn.response && !turn.error} />
        ))}
      </div>
    </PageShell>
  )
}

function ThreadMessage({ turn, pending }: { turn: ThreadTurn; pending: boolean }) {
  const sourcesRef = useRef<HTMLDetailsElement>(null)
  const result = turn.response

  return (
    <article className="flex flex-col gap-3">
      <div className="flex justify-end">
        <p className="max-w-[min(100%,56rem)] rounded-2xl rounded-br-md bg-accent-muted px-4 py-2.5 text-sm leading-relaxed text-fg">
          {turn.question}
        </p>
      </div>

      {turn.error && <Alert>{turn.error}</Alert>}

      {pending && (
        <div className="max-w-[min(100%,56rem)] space-y-2" aria-busy aria-label="Thinking">
          <div className="h-4 w-40 animate-pulse rounded bg-surface-raised/70" />
          <div className="h-24 animate-pulse rounded-xl bg-surface-raised/50" />
        </div>
      )}

      {result && (
        <div className="flex w-full max-w-5xl flex-col gap-3">
          {result.notice && <Alert tone="info">{result.notice}</Alert>}
          <div className="rounded-2xl rounded-tl-md border border-line/70 bg-surface/55 px-4 py-4 sm:px-5">
            <p className="mb-3 text-xs text-fg-muted">
              {result.grounded
                ? `From ${result.sources.length} ${result.sources.length === 1 ? 'memory' : 'memories'}`
                : 'No matching memories'}
              {result.model === 'retrieval-only'
                ? ' · listed from search'
                : ` · ${result.model}`}
            </p>
            <AskMarkdown
              text={result.answer}
              onCite={() => {
                if (sourcesRef.current) {
                  sourcesRef.current.open = true
                  sourcesRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
                }
              }}
            />
          </div>
          {result.sources.length > 0 && (
            <details className="rounded-xl border border-line/50 bg-ink/20 open:bg-ink/30" ref={sourcesRef}>
              <summary className="cursor-pointer px-4 py-2.5 text-xs font-semibold tracking-wide text-fg-muted uppercase">
                Sources · {visibleSources(result.sources).length}
              </summary>
              <div className="flex flex-col gap-3 px-3 pb-3">
                {visibleSources(result.sources).map((source) => (
                  <AskSourceCard key={source.id} source={source} />
                ))}
              </div>
            </details>
          )}
        </div>
      )}
    </article>
  )
}

function toHistory(turns: ThreadTurn[]): AskHistoryTurn[] {
  const history: AskHistoryTurn[] = []
  for (const turn of turns) {
    if (!turn.response) continue
    history.push({ role: 'user', content: turn.question })
    history.push({ role: 'assistant', content: turn.response.answer })
  }
  return history.slice(-10)
}

function askEmptyDescription(chatOn: boolean | undefined, quota: AskQuota | undefined): string {
  if (chatOn === false) {
    return 'A language model is not configured on this server. Ask still searches your archive and lists the matching memories instead of paraphrasing them.'
  }
  if (quota?.capped && quota.remaining === 0) {
    return 'This account has used this month’s AI answers. Ask still searches your archive and lists the matching memories.'
  }
  if (chatOn === true) {
    return 'Answers are grounded in memories you have saved. Ask a follow-up without erasing the last reply.'
  }
  return 'Answers are grounded in memories you have saved. Chat days show messages; photos show images.'
}

function quotaMeter(quota: AskQuota | undefined) {
  if (!quota?.capped) return null
  const label =
    quota.remaining === 0
      ? `This month’s ${quota.limit} AI answers are used. Matching memories are still listed.`
      : `${quota.remaining} of ${quota.limit} AI answers left this month`
  return <p className="text-xs text-fg-muted">{label}</p>
}

function visibleSources(sources: AskSource[]) {
  const nestedPhotoIds = new Set(sources.flatMap((source) => source.relatedPhotos.map((photo) => photo.id)))
  return sources.filter((source) => !(source.type === 'PHOTO' && nestedPhotoIds.has(source.id)))
}
