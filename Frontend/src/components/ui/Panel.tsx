import type { ReactNode } from 'react'

interface PanelProps {
  title?: ReactNode
  action?: ReactNode
  className?: string
  children: ReactNode
}

/** The one container primitive used across the app, so every surface reads the same. */
export function Panel({ title, action, className = '', children }: PanelProps) {
  return (
    <section
      className={`rounded-panel border border-line/80 bg-surface/78 backdrop-blur-sm ${className}`}
    >
      {(title || action) && (
        <header className="flex items-center justify-between gap-4 border-b border-line/70 px-5 py-3">
          {typeof title === 'string' ? (
            <h2 className="font-display text-sm font-semibold tracking-tight text-fg">{title}</h2>
          ) : (
            title
          )}
          {action}
        </header>
      )}
      <div className="p-5">{children}</div>
    </section>
  )
}
