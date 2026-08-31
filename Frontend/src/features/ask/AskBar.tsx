import type { FormEvent, ReactNode } from 'react'

const EXAMPLES = [
  'What happened on my Sikkim trip?',
  'When did I first talk about going to Sikkim?',
  'Show me everything about my birthday',
]

/**
 * The Ask control: a question that will be answered from the user's own memories.
 */
export function AskBar({
  value,
  onChange,
  onSubmit,
  autoFocus = false,
  isLoading = false,
  examples,
}: {
  value: string
  onChange: (value: string) => void
  onSubmit?: () => void
  autoFocus?: boolean
  isLoading?: boolean
  examples?: ReactNode
}) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit?.()
  }

  return (
    <div className="rounded-panel border border-line bg-surface/80 p-1.5 shadow-2xl shadow-black/40 backdrop-blur-sm focus-within:border-accent/60">
      <form className="flex flex-col gap-3 p-4" onSubmit={handleSubmit}>
        <label htmlFor="ask" className="sr-only">
          Ask anything about your memories
        </label>
        <div className="flex items-center gap-3">
          <span aria-hidden="true" className="text-lg text-accent">
            ✦
          </span>
          <input
            id="ask"
            autoFocus={autoFocus}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder="Ask anything about your memories…"
            disabled={isLoading}
            className="min-w-0 flex-1 bg-transparent text-base text-fg placeholder:text-fg-muted/70 focus:outline-none disabled:opacity-60"
          />
          <button
            type="submit"
            disabled={value.trim().length === 0 || isLoading}
            className="rounded-lg bg-accent px-3.5 py-1.5 text-sm font-medium text-white transition-opacity disabled:opacity-40"
          >
            {isLoading ? 'Thinking…' : 'Ask'}
          </button>
        </div>

        {examples ?? (
          <div className="flex flex-wrap gap-2">
            {EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => onChange(example)}
                className="rounded-full border border-line px-3 py-1 text-xs text-fg-muted transition-colors hover:border-accent/50 hover:text-fg"
              >
                {example}
              </button>
            ))}
          </div>
        )}
      </form>
    </div>
  )
}
