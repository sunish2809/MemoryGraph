import { useEffect, useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/Button'

type PaginationProps = {
  /** Zero-based page index from the API. */
  page: number
  totalPages: number
  totalItems: number
  itemLabel?: string
  onPageChange: (page: number) => void
  /** Timeline uses Newer/Older instead of Previous/Next. */
  mode?: 'default' | 'timeline'
  className?: string
}

/**
 * Compact pager with numbered jumps and a direct “go to page” field.
 */
export function Pagination({
  page,
  totalPages,
  totalItems,
  itemLabel = 'items',
  onPageChange,
  mode = 'default',
  className = '',
}: PaginationProps) {
  const [draft, setDraft] = useState(String(page + 1))
  const humanPage = page + 1

  useEffect(() => {
    setDraft(String(page + 1))
  }, [page])

  if (totalPages <= 1) {
    return null
  }

  function jump(event: FormEvent) {
    event.preventDefault()
    const next = Number.parseInt(draft, 10)
    if (!Number.isFinite(next)) {
      setDraft(String(humanPage))
      return
    }
    const clamped = Math.min(Math.max(next, 1), totalPages)
    setDraft(String(clamped))
    onPageChange(clamped - 1)
  }

  const prevLabel = mode === 'timeline' ? 'Newer' : 'Previous'
  const nextLabel = mode === 'timeline' ? 'Older' : 'Next'
  const windowPages = pageWindow(page, totalPages)

  return (
    <nav
      className={`flex flex-wrap items-center justify-between gap-3 border-t border-line/80 bg-surface/90 px-1 py-3 backdrop-blur-md ${className}`}
      aria-label="Pagination"
    >
      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="secondary"
          className="!px-3 !py-1.5 text-xs"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
        >
          {prevLabel}
        </Button>
        <Button
          type="button"
          variant="secondary"
          className="!px-3 !py-1.5 text-xs"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
        >
          {nextLabel}
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        {windowPages.map((entry, index) =>
          entry === '…' ? (
            <span key={`e-${index}`} className="px-1 text-xs text-fg-muted">
              …
            </span>
          ) : (
            <button
              key={entry}
              type="button"
              onClick={() => onPageChange(entry)}
              aria-current={entry === page ? 'page' : undefined}
              className={`min-w-8 rounded-lg px-2 py-1 text-xs font-medium transition-colors ${
                entry === page
                  ? 'bg-accent text-ink'
                  : 'text-fg-muted hover:bg-surface-raised hover:text-fg'
              }`}
            >
              {entry + 1}
            </button>
          ),
        )}
      </div>

      <form onSubmit={jump} className="flex items-center gap-2 text-xs text-fg-muted">
        <span className="hidden sm:inline">
          {totalItems.toLocaleString()} {itemLabel}
        </span>
        <label className="flex items-center gap-1.5">
          <span className="sr-only">Go to page</span>
          <span className="hidden md:inline">Go to</span>
          <input
            type="number"
            min={1}
            max={totalPages}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            className="w-14 rounded-lg border border-line bg-ink px-2 py-1 text-center text-fg tabular-nums focus:border-accent focus:outline-none"
          />
          <span>/ {totalPages}</span>
        </label>
        <Button type="submit" variant="ghost" className="!px-2 !py-1 text-xs">
          Go
        </Button>
      </form>
    </nav>
  )
}

function pageWindow(page: number, totalPages: number): Array<number | '…'> {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i)
  }
  const pages = new Set<number>([0, totalPages - 1, page])
  for (let i = page - 1; i <= page + 1; i++) {
    if (i > 0 && i < totalPages - 1) pages.add(i)
  }
  const sorted = [...pages].sort((a, b) => a - b)
  const out: Array<number | '…'> = []
  for (let i = 0; i < sorted.length; i++) {
    if (i > 0 && sorted[i] - sorted[i - 1] > 1) out.push('…')
    out.push(sorted[i])
  }
  return out
}
