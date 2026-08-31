import { Link } from 'react-router-dom'

import { MemoryTypeBadge, ProcessingBadge } from '@/components/ui/Badges'
import { AuthenticatedImage } from '@/features/memories/AuthenticatedImage'
import { HighlightedText } from '@/features/search/HighlightedText'
import { formatDateTime } from '@/lib/format'
import type { MemorySummary } from '@/types/api'

/**
 * One memory in a list. Leads with the photo where there is one, because a thumbnail is how people
 * recognise their own memories far faster than they read a title.
 *
 * `snippet` is the matching passage from search. When it is present it replaces the excerpt, because
 * showing the opening of a note that matched ten thousand characters in would look like a miss.
 */
export function MemoryCard({ memory, snippet }: { memory: MemorySummary; snippet?: string | undefined }) {
  const thumbnail = memory.assets[0]
  const body = snippet || memory.excerpt

  return (
    <Link
      to={`/memories/${memory.id}`}
      className="group flex gap-3 rounded-lg border border-line/70 bg-ink/25 p-2.5 transition-all hover:border-accent/45 hover:bg-surface-raised/50"
    >
      {thumbnail ? (
        <AuthenticatedImage
          downloadPath={thumbnail.downloadPath}
          alt={memory.title ?? thumbnail.fileName}
          className="size-16 shrink-0 rounded-md object-cover ring-1 ring-line/60 sm:size-[4.5rem]"
        />
      ) : (
        <div
          aria-hidden
          className="flex size-16 shrink-0 items-center justify-center rounded-md bg-surface-raised text-lg ring-1 ring-line/60 sm:size-[4.5rem]"
        >
          ·
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="truncate text-sm font-medium text-fg group-hover:text-accent">
            {memory.title ?? 'Untitled memory'}
          </h3>
          <MemoryTypeBadge type={memory.type} />
          <ProcessingBadge status={memory.processingStatus} />
        </div>
        {body &&
          (snippet ? (
            <HighlightedText text={snippet} className="line-clamp-2 text-sm text-fg-muted" />
          ) : (
            <p className="line-clamp-2 text-sm text-fg-muted">{memory.excerpt}</p>
          ))}
        <time dateTime={memory.occurredAt} className="mt-auto text-xs text-fg-muted/80">
          {formatDateTime(memory.occurredAt)}
        </time>
      </div>
    </Link>
  )
}
