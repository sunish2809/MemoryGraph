/** Formatting helpers shared by every surface that shows a memory. */

/**
 * The timezone the browser is running in. Sent with timeline and search requests so calendar days
 * mean the viewer's days, not UTC's.
 */
export const VIEWER_TIME_ZONE = Intl.DateTimeFormat().resolvedOptions().timeZone

const DATE_TIME = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const DAY_WITH_WEEKDAY = new Intl.DateTimeFormat(undefined, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
})

export function formatDateTime(isoInstant: string): string {
  return DATE_TIME.format(new Date(isoInstant))
}

const TIME_ONLY = new Intl.DateTimeFormat(undefined, {
  hour: 'numeric',
  minute: '2-digit',
})

export function formatTime(isoInstant: string): string {
  return TIME_ONLY.format(new Date(isoInstant))
}

/** Renders a `YYYY-MM-DD` timeline day heading without shifting it into another timezone. */
export function formatTimelineDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return DAY_WITH_WEEKDAY.format(new Date(year, month - 1, day))
}

export function formatDateRange(fromIso: string, toIso: string): string {
  const from = new Date(fromIso)
  const to = new Date(toIso)
  if (from.toDateString() === to.toDateString()) {
    return DAY_WITH_WEEKDAY.format(from)
  }
  return `${DAY_WITH_WEEKDAY.format(from)} – ${DAY_WITH_WEEKDAY.format(to)}`
}

/** `YYYY-MM-DD` in the viewer's timezone, for date inputs. */
export function instantToDateInput(isoInstant: string): string {
  const date = new Date(isoInstant)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function dateInputToStartInstant(value: string): string | undefined {
  if (!value) return undefined
  const parsed = new Date(`${value}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

export function dateInputToEndInstant(value: string): string | undefined {
  if (!value) return undefined
  const parsed = new Date(`${value}T23:59:59.999`)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let size = bytes / 1024
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${size < 10 ? size.toFixed(1) : Math.round(size)} ${units[unitIndex]}`
}

/**
 * Converts a `datetime-local` value into an instant.
 *
 * The input has no timezone, and the user means their own — so it is interpreted in the browser's
 * zone rather than assumed to be UTC, which would silently shift every memory.
 */
export function localInputToInstant(value: string): string | undefined {
  if (!value) return undefined
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString()
}

/** The reverse, for pre-filling a `datetime-local` input with "now". */
export function instantToLocalInput(date: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
