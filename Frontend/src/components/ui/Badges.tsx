import { MEMORY_TYPE_LABELS } from '@/lib/memoryLabels'
import type { MemoryType, ProcessingStatus } from '@/types/api'

export function MemoryTypeBadge({ type }: { type: MemoryType }) {
  return (
    <span className="rounded-full border border-line bg-surface-raised px-2 py-0.5 text-xs font-medium text-fg-muted">
      {MEMORY_TYPE_LABELS[type]}
    </span>
  )
}

/**
 * Only shown while a memory is not yet fully processed, or when processing failed.
 *
 * A completed memory needs no badge: the normal state should not be announced, or the interface fills
 * with labels that carry no information.
 */
export function ProcessingBadge({ status }: { status: ProcessingStatus }) {
  if (status === 'COMPLETED' || status === 'SKIPPED') {
    return null
  }

  if (status === 'FAILED') {
    return (
      <span
        className="rounded-full border border-danger/40 bg-danger-muted px-2 py-0.5 text-xs font-medium text-danger"
        title="Some details could not be extracted. The memory itself is safe."
      >
        Incomplete
      </span>
    )
  }

  return (
    <span className="rounded-full border border-accent/40 bg-accent-muted px-2 py-0.5 text-xs font-medium text-accent">
      Processing
    </span>
  )
}
