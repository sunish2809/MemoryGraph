import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon: string
  title: string
  description: string
  footnote?: ReactNode
}

/**
 * Used wherever a surface has nothing to show yet. States plainly what will appear here rather than
 * pretending the feature is broken.
 */
export function EmptyState({ icon, title, description, footnote }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <span aria-hidden="true" className="text-3xl">
        {icon}
      </span>
      <h3 className="text-base font-medium text-fg">{title}</h3>
      <p className="max-w-md text-sm text-fg-muted">{description}</p>
      {footnote && <div className="mt-2 text-xs text-fg-muted/80">{footnote}</div>}
    </div>
  )
}
