import { useState } from 'react'

import { PageShell } from '@/components/layout/PageShell'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Pagination } from '@/components/ui/Pagination'
import { MemoryCard } from '@/features/memories/MemoryCard'
import { NewMemoryDialog } from '@/features/memories/NewMemoryDialog'
import { useDeleteTimelineDay, useTimeline } from '@/features/memories/useMemories'
import { formatTimelineDay, VIEWER_TIME_ZONE } from '@/lib/format'

const PAGE_SIZE = 50

export function TimelinePage() {
  const [page, setPage] = useState(0)
  const [isAdding, setIsAdding] = useState(false)
  const [confirmDay, setConfirmDay] = useState<string | null>(null)
  const { data: timeline, isPending, isError } = useTimeline({ zone: VIEWER_TIME_ZONE, page, size: PAGE_SIZE })
  const deleteDay = useDeleteTimelineDay()

  return (
    <PageShell
      title="Timeline"
      description="Memories ordered by when they happened — older photos land in the right year, not today."
      actions={<Button onClick={() => setIsAdding(true)}>Add a memory</Button>}
      footer={
        timeline && timeline.totalPages > 1 ? (
          <Pagination
            page={timeline.page}
            totalPages={timeline.totalPages}
            totalItems={timeline.totalItems}
            itemLabel="memories"
            mode="timeline"
            onPageChange={(next) => {
              setPage(next)
            }}
          />
        ) : undefined
      }
    >
      {isPending && <div className="h-48 animate-pulse rounded-xl bg-surface-raised/50" aria-busy />}

      {isError && (
        <EmptyState
          icon="⚠"
          title="Your timeline could not be loaded"
          description="The server did not respond as expected. Check that the backend is running and try again."
        />
      )}

      {timeline && timeline.days.length === 0 && (
        <EmptyState
          icon="◈"
          title="Nothing here yet"
          description="Write a note or upload a photo. Each memory is placed by when it happened."
          footnote={
            <button type="button" onClick={() => setIsAdding(true)} className="text-accent hover:underline">
              Add your first memory
            </button>
          }
        />
      )}

      <div className="flex flex-col gap-6">
        {timeline?.days.map((day) => (
          <section key={day.date} className="flex flex-col gap-2.5">
            <div className="sticky top-0 z-10 -mx-1 flex items-center gap-3 bg-surface/90 px-1 py-1.5 backdrop-blur-sm">
              <h2 className="font-display text-sm font-semibold text-accent">{formatTimelineDay(day.date)}</h2>
              <span className="h-px flex-1 bg-gradient-to-r from-line to-transparent" aria-hidden />
              <span className="text-[11px] tracking-wide text-fg-muted uppercase">
                {day.memories.length} {day.memories.length === 1 ? 'memory' : 'memories'}
              </span>
              {confirmDay === day.date ? (
                <span className="flex items-center gap-2 text-xs text-fg-muted">
                  Delete day?
                  <button
                    type="button"
                    className="text-fg-muted hover:text-fg"
                    onClick={() => setConfirmDay(null)}
                  >
                    Keep
                  </button>
                  <button
                    type="button"
                    className="text-danger hover:underline"
                    disabled={deleteDay.isPending}
                    onClick={() => {
                      deleteDay.mutate(
                        { date: day.date, zone: VIEWER_TIME_ZONE },
                        { onSuccess: () => setConfirmDay(null) },
                      )
                    }}
                  >
                    Delete
                  </button>
                </span>
              ) : (
                <button
                  type="button"
                  className="text-[11px] text-fg-muted/80 transition-colors hover:text-danger"
                  onClick={() => setConfirmDay(day.date)}
                >
                  Delete day
                </button>
              )}
            </div>
            <div className="flex flex-col gap-2">
              {day.memories.map((memory) => (
                <MemoryCard key={memory.id} memory={memory} />
              ))}
            </div>
          </section>
        ))}
      </div>

      <NewMemoryDialog isOpen={isAdding} onClose={() => setIsAdding(false)} />
    </PageShell>
  )
}
