import type { ReactNode } from 'react'

type PageShellProps = {
  title: string
  description?: ReactNode
  actions?: ReactNode
  toolbar?: ReactNode
  footer?: ReactNode
  children: ReactNode
}

/**
 * Fixed-height page chrome: sticky title/toolbar, scrollable body, sticky footer (e.g. pagination).
 * Keeps long lists inside a pane so the whole app doesn’t scroll endlessly.
 */
export function PageShell({ title, description, actions, toolbar, footer, children }: PageShellProps) {
  return (
    <div className="page-enter flex min-h-0 flex-1 flex-col gap-0 overflow-hidden rounded-panel border border-line/50 bg-surface/15 shadow-[0_20px_60px_-40px_rgb(0_0_0_/_0.8)] backdrop-blur-[2px]">
      <header className="shrink-0 border-b border-line/40 bg-surface/35 px-5 py-4 backdrop-blur-sm sm:px-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h1 className="font-display text-2xl font-semibold tracking-tight text-fg sm:text-[1.65rem]">
              {title}
            </h1>
            {description && <div className="mt-1 max-w-2xl text-sm text-fg-muted">{description}</div>}
          </div>
          {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
        </div>
        {toolbar && <div className="mt-4">{toolbar}</div>}
      </header>

      <div className="scroll-pane min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-4 sm:px-6">
        {children}
      </div>

      {footer && <div className="shrink-0 bg-surface/30 px-4 backdrop-blur-sm sm:px-5">{footer}</div>}
    </div>
  )
}

/** Full-pane scroll for detail / form pages that don’t need a sticky pager. */
export function ScrollPage({ children }: { children: ReactNode }) {
  return (
    <div className="page-enter scroll-pane min-h-0 flex-1 overflow-y-auto overscroll-contain rounded-panel border border-line/50 bg-surface/22 p-5 shadow-[0_20px_60px_-40px_rgb(0_0_0_/_0.8)] backdrop-blur-[2px] sm:p-6">
      <div className="mx-auto flex w-full max-w-4xl flex-col gap-5">{children}</div>
    </div>
  )
}
