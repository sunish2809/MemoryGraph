interface AlertProps {
  tone?: 'danger' | 'info'
  children: string
}

export function Alert({ tone = 'danger', children }: AlertProps) {
  const styles =
    tone === 'danger'
      ? 'border-danger/40 bg-danger-muted text-danger'
      : 'border-accent/30 bg-accent-muted text-fg'

  return (
    <p role="alert" className={`rounded-xl border px-3.5 py-2.5 text-sm ${styles}`}>
      {children}
    </p>
  )
}
