import type { FormEvent, ReactNode } from 'react'

const EXAMPLES = [
  'What happened on my Sikkim trip?',
  'When did I first talk about going to Sikkim?',
  'Show me everything about my birthday',
]

/**
 * Composer for Ask. In a thread it sits at the bottom; examples only show before the first question.
 */
export function AskBar({
  value,
  onChange,
  onSubmit,
  autoFocus = false,
  isLoading = false,
  showExamples = true,
  placeholder = 'Ask anything about your memories…',
  examples,
}: {
  value: string
  onChange: (value: string) => void
  onSubmit?: () => void
  autoFocus?: boolean
  isLoading?: boolean
  showExamples?: boolean
  placeholder?: string
  examples?: ReactNode
}) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit?.()
  }

  return (
    <div className="rounded-panel border border-line bg-surface/80 p-1.5 shadow-2xl shadow-black/40 backdrop-blur-sm focus-within:border-accent/60">
      <form className="flex flex-col gap-3 p-3 sm:p-4" onSubmit={handleSubmit}>
        <label htmlFor="ask" className="sr-only">
          Ask anything about your memories
        </label>
        <div className="flex items-end gap-3">
          <span aria-hidden="true" className="mb-1 text-lg text-accent">
            ✦
          </span>
          <textarea
            id="ask"
            rows={1}
            autoFocus={autoFocus}
            value={value}
            onChange={(event) => onChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                onSubmit?.()
              }
            }}
            placeholder={placeholder}
            disabled={isLoading}
            className="max-h-32 min-h-10 min-w-0 flex-1 resize-none bg-transparent py-2 text-base text-fg placeholder:text-fg-muted/70 focus:outline-none disabled:opacity-60"
          />
          <button
            type="submit"
            disabled={value.trim().length === 0 || isLoading}
            className="mb-0.5 rounded-lg bg-accent px-3.5 py-1.5 text-sm font-medium text-white transition-opacity disabled:opacity-40"
          >
            {isLoading ? 'Thinking…' : 'Ask'}
          </button>
        </div>

        {showExamples
          ? (examples ?? (
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
            ))
          : null}
      </form>
    </div>
  )
}
