import type { ReactNode } from 'react'

/**
 * Markers wrapping matched words inside a search snippet. Must stay in lockstep with
 * `SearchHighlight` on the backend: the server decides which words matched (stemming means the
 * client cannot), and ships them as inert text so the client never has to render HTML from a note.
 */
const HIGHLIGHT_START = '[['
const HIGHLIGHT_END = ']]'

interface Segment {
  text: string
  marked: boolean
}

/**
 * Splits a snippet into ordinary text and highlighted spans. Unmatched markers are left as text
 * rather than swallowed, so a note that happens to contain `[[` still renders in full.
 */
function parseHighlights(snippet: string): Segment[] {
  const segments: Segment[] = []
  let cursor = 0

  while (cursor < snippet.length) {
    const start = snippet.indexOf(HIGHLIGHT_START, cursor)
    if (start === -1) {
      segments.push({ text: snippet.slice(cursor), marked: false })
      break
    }
    if (start > cursor) {
      segments.push({ text: snippet.slice(cursor, start), marked: false })
    }
    const end = snippet.indexOf(HIGHLIGHT_END, start + HIGHLIGHT_START.length)
    if (end === -1) {
      segments.push({ text: snippet.slice(start), marked: false })
      break
    }
    segments.push({ text: snippet.slice(start + HIGHLIGHT_START.length, end), marked: true })
    cursor = end + HIGHLIGHT_END.length
  }

  return segments
}

export function HighlightedText({ text, className = '' }: { text: string; className?: string }) {
  return (
    <p className={className}>
      {parseHighlights(text).map((segment, index) =>
        segment.marked ? <Mark key={index}>{segment.text}</Mark> : <span key={index}>{segment.text}</span>,
      )}
    </p>
  )
}

function Mark({ children }: { children: ReactNode }) {
  return (
    <mark className="rounded-sm bg-accent/30 px-0.5 text-fg not-italic">{children}</mark>
  )
}
