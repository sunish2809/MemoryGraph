import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { PageShell } from '@/components/layout/PageShell'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { useAuth } from '@/features/auth/useAuth'
import { MemoryCard } from '@/features/memories/MemoryCard'
import { NewMemoryDialog } from '@/features/memories/NewMemoryDialog'
import { useMemoryList, useMemoryStats } from '@/features/memories/useMemories'
import { SearchBar } from '@/features/search/SearchBar'

const RECENT_COUNT = 8

const EXAMPLES = ['Sikkim', 'birthday', 'Gangtok']

export function DashboardPage() {
  const { user } = useAuth()
  const firstName = user?.displayName.split(' ')[0] ?? 'there'
  const navigate = useNavigate()
  const [isAdding, setIsAdding] = useState(false)
  const [question, setQuestion] = useState('')
  const { data: recent, isPending } = useMemoryList(0, RECENT_COUNT)
  const { data: stats } = useMemoryStats()

  const total = stats?.totalMemories ?? 0

  function goToSearch(phrase: string) {
    const trimmed = phrase.trim()
    navigate(trimmed ? `/search?q=${encodeURIComponent(trimmed)}` : '/search')
  }

  return (
    <PageShell
      title={`Hello, ${firstName}`}
      description={
        total === 0
          ? 'Nothing saved yet. Add your first memory to start your timeline.'
          : `${total.toLocaleString()} ${total === 1 ? 'memory' : 'memories'} in your private archive.`
      }
      actions={<Button onClick={() => setIsAdding(true)}>Add a memory</Button>}
      toolbar={
        <SearchBar
          value={question}
          onChange={setQuestion}
          onSubmit={() => goToSearch(question)}
          examples={
            <div className="flex flex-wrap items-center gap-2">
              {EXAMPLES.map((example) => (
                <button
                  key={example}
                  type="button"
                  onClick={() => goToSearch(example)}
                  className="rounded-md border border-line px-3 py-1 text-xs text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
                >
                  {example}
                </button>
              ))}
              <Link to="/ask" className="ml-auto text-xs text-fg-muted/80 hover:text-accent">
                Ask a question instead
              </Link>
            </div>
          }
        />
      }
    >
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_17rem]">
        <section className="flex min-w-0 flex-col gap-3">
          <div className="flex items-center justify-between gap-2">
            <h2 className="font-display text-sm font-semibold text-fg">Recent</h2>
            {total > 0 && (
              <Link to="/timeline" className="text-xs text-accent hover:underline">
                Open timeline
              </Link>
            )}
          </div>
          {isPending ? (
            <div className="h-40 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />
          ) : total === 0 ? (
            <EmptyState
              icon="◈"
              title="Your timeline is empty"
              description="Write a note about something that happened, or upload a photo."
              footnote={
                <button type="button" onClick={() => setIsAdding(true)} className="text-accent hover:underline">
                  Add your first memory
                </button>
              }
            />
          ) : (
            <div className="flex flex-col gap-2">
              {recent?.items.map((memory) => (
                <MemoryCard key={memory.id} memory={memory} />
              ))}
            </div>
          )}
        </section>

        <aside className="flex flex-col gap-2 lg:sticky lg:top-0">
          <StatRow label="Memories" value={total} />
          <StatRow
            label="Earliest year"
            value={stats?.earliestOccurredAt ? formatEarliest(stats.earliestOccurredAt) : '—'}
          />
          <StatRow
            label="People"
            value={stats?.totalPeople ?? 0}
            to="/people"
          />
          <StatRow
            label="Places"
            value={stats?.totalPlaces ?? 0}
            to="/places"
          />
          <Link
            to="/import"
            className="mt-2 rounded-lg border border-dashed border-line px-4 py-3 text-center text-xs text-fg-muted transition-colors hover:border-accent/50 hover:text-accent"
          >
            Import WhatsApp or Google Photos →
          </Link>
        </aside>
      </div>

      <NewMemoryDialog isOpen={isAdding} onClose={() => setIsAdding(false)} />
    </PageShell>
  )
}

function formatEarliest(isoInstant: string): string {
  return String(new Date(isoInstant).getFullYear())
}

function StatRow({ label, value, to }: { label: string; value: string | number; to?: string }) {
  const body = (
    <>
      <p className="text-[10px] tracking-[0.14em] text-fg-muted uppercase">{label}</p>
      <p className="mt-1 font-display text-2xl font-semibold tracking-tight text-fg">{value}</p>
    </>
  )
  if (to) {
    return (
      <Link
        to={to}
        className="rounded-lg border border-line/80 bg-ink/40 px-4 py-3 transition-colors hover:border-accent/45"
      >
        {body}
      </Link>
    )
  }
  return <div className="rounded-lg border border-line/80 bg-ink/40 px-4 py-3">{body}</div>
}
