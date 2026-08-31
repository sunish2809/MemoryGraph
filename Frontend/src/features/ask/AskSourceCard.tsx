import { Link } from 'react-router-dom'

import { MemoryTypeBadge } from '@/components/ui/Badges'
import { AuthenticatedImage } from '@/features/memories/AuthenticatedImage'
import { formatDateTime, formatTime } from '@/lib/format'
import type { AskRelatedPhoto, AskSource, ConversationMessage, MediaAsset } from '@/types/api'

/**
 * Ask evidence card: chat bubbles for conversations, large media for photos/Google Photos, and a
 * related-image strip for WhatsApp days that had attachments.
 */
export function AskSourceCard({ source }: { source: AskSource }) {
  const primaryAssets = source.assets.filter((asset) => isVisual(asset))
  const relatedVisuals = source.relatedPhotos.flatMap((photo) =>
    photo.assets.filter(isVisual).map((asset) => ({ photo, asset })),
  )

  return (
    <article className="overflow-hidden rounded-xl border border-line/70 bg-ink/25">
      <header className="flex flex-wrap items-start justify-between gap-2 border-b border-line/50 px-3.5 py-3">
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex flex-wrap items-center gap-2">
            <MemoryTypeBadge type={source.type} />
            {source.people.length > 0 && (
              <span className="text-xs text-fg-muted">{source.people.join(' · ')}</span>
            )}
          </div>
          <Link
            to={`/memories/${source.id}`}
            className="block truncate text-sm font-medium text-fg hover:text-accent"
          >
            {source.title ?? 'Untitled memory'}
          </Link>
          <time dateTime={source.occurredAt} className="text-xs text-fg-muted/80">
            {formatDateTime(source.occurredAt)}
          </time>
        </div>
        <Link to={`/memories/${source.id}`} className="text-xs text-accent hover:underline">
          Open
        </Link>
      </header>

      {primaryAssets.length > 0 && (
        <div
          className={`grid gap-1 bg-black/20 p-1 ${
            primaryAssets.length === 1 ? 'grid-cols-1' : 'grid-cols-2'
          }`}
        >
          {primaryAssets.slice(0, 4).map((asset) => (
            <AuthenticatedImage
              key={asset.id}
              downloadPath={asset.downloadPath}
              alt={source.title ?? asset.fileName}
              className={`w-full object-cover ${
                primaryAssets.length === 1 ? 'max-h-80 rounded-lg object-contain' : 'aspect-square rounded-md'
              }`}
            />
          ))}
        </div>
      )}

      {source.description && source.type !== 'CONVERSATION' && (
        <p className="border-b border-line/40 px-3.5 py-2 text-sm text-fg-muted">{source.description}</p>
      )}

      {source.messages.length > 0 ? (
        <ol className="flex max-h-80 flex-col gap-2 overflow-y-auto px-3.5 py-3">
          {source.messages.map((message) => (
            <ChatBubble key={message.id} message={message} />
          ))}
        </ol>
      ) : (
        source.excerpt &&
        source.type === 'CONVERSATION' && (
          <pre className="max-h-64 overflow-y-auto px-3.5 py-3 font-sans text-sm leading-relaxed whitespace-pre-wrap text-fg-muted">
            {source.excerpt}
          </pre>
        )
      )}

      {relatedVisuals.length > 0 && (
        <div className="border-t border-line/50 px-3.5 py-3">
          <p className="mb-2 text-xs font-semibold tracking-wide text-fg-muted uppercase">
            Photos from this chat day
          </p>
          <div className="flex gap-2 overflow-x-auto pb-1">
            {relatedVisuals.map(({ photo, asset }) => (
              <RelatedThumb key={`${photo.id}-${asset.id}`} photo={photo} asset={asset} />
            ))}
          </div>
        </div>
      )}
    </article>
  )
}

function ChatBubble({ message }: { message: ConversationMessage }) {
  return (
    <li className="rounded-xl border border-line/60 bg-surface-raised/45 px-3 py-2">
      <div className="mb-0.5 flex flex-wrap items-baseline justify-between gap-2">
        <span className="text-sm font-medium text-fg">{message.senderName}</span>
        <time dateTime={message.sentAt} className="text-xs text-fg-muted">
          {formatTime(message.sentAt)}
        </time>
      </div>
      <p className="text-sm leading-relaxed whitespace-pre-wrap text-fg">{message.body}</p>
    </li>
  )
}

function RelatedThumb({ photo, asset }: { photo: AskRelatedPhoto; asset: MediaAsset }) {
  return (
    <Link
      to={`/memories/${photo.id}`}
      className="group relative size-20 shrink-0 overflow-hidden rounded-lg ring-1 ring-line/60"
      title={photo.title ?? asset.fileName}
    >
      <AuthenticatedImage
        downloadPath={asset.downloadPath}
        alt={photo.title ?? asset.fileName}
        className="size-full object-cover transition-transform group-hover:scale-105"
      />
    </Link>
  )
}

function isVisual(asset: MediaAsset): boolean {
  return asset.mimeType.startsWith('image/') || asset.mimeType.startsWith('video/')
}
