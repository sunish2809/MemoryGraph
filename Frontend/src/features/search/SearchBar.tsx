import type { FormEvent, ReactNode } from 'react'

/**
 * The primary control of the product: find a memory by a word you remember.
 *
 * Kept presentational. The search page owns the URL; the dashboard just navigates into it. Putting
 * routing in here would force one of those to pretend to be the other.
 */
export function SearchBar({
  value,
  onChange,
  onSubmit,
  autoFocus = false,
  placeholder = 'Search your memories…',
  examples,
}: {
  value: string
  onChange: (value: string) => void
  onSubmit?: () => void
  autoFocus?: boolean
  placeholder?: string
  examples?: ReactNode
}) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit?.()
  }

  return (
    <div className="rounded-panel border border-line bg-surface/80 p-1.5 shadow-2xl shadow-black/40 backdrop-blur-sm focus-within:border-accent/60">
      <form className="flex flex-col gap-3 p-4" onSubmit={handleSubmit} role="search">
        <label htmlFor="memory-search" className="sr-only">
          Search your memories
        </label>
        <div className="flex items-center gap-3">
          <span aria-hidden="true" className="text-lg text-accent">
            ⌕
          </span>
          <input
            id="memory-search"
            autoFocus={autoFocus}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder={placeholder}
            autoComplete="off"
            spellCheck={false}
            className="min-w-0 flex-1 bg-transparent text-base text-fg placeholder:text-fg-muted/70 focus:outline-none"
          />
          <button
            type="submit"
            className="rounded-lg bg-accent px-3.5 py-1.5 text-sm font-medium text-white transition-opacity"
          >
            Search
          </button>
        </div>
        {examples}
      </form>
    </div>
  )
}
