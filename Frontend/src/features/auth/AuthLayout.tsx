import type { ReactNode } from 'react'

import { NetworkBackdrop } from '@/components/layout/NetworkBackdrop'

/**
 * Shared frame for the login and register screens: the product promise on one side, the form on the
 * other.
 */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}) {
  return (
    <div className="relative grid min-h-dvh lg:grid-cols-2">
      <NetworkBackdrop />
      <div className="relative z-10 hidden flex-col justify-between p-12 lg:flex">
        <div className="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            className="grid size-8 place-items-center rounded-lg bg-accent text-sm font-bold text-white"
          >
            M
          </span>
          <span className="text-base font-semibold tracking-tight">MemoryGraph</span>
        </div>

        <div className="max-w-md">
          <h1 className="text-3xl leading-tight font-semibold tracking-tight">
            A searchable memory of your life.
          </h1>
          <p className="mt-4 text-sm leading-relaxed text-fg-muted">
            Bring photos, notes, conversations and recordings together into one timeline, then ask
            questions about your own past in plain language.
          </p>
          <ul className="mt-8 flex flex-col gap-3 text-sm text-fg-muted">
            {[
              'Everything becomes a memory with a time, a place and people',
              'Answers cite the memories they came from',
              'Your memories stay on this server, not a MemoryGraph company cloud',
            ].map((point) => (
              <li key={point} className="flex gap-3">
                <span aria-hidden="true" className="text-support">
                  —
                </span>
                {point}
              </li>
            ))}
          </ul>
        </div>

        <p className="text-xs text-fg-muted/70">Private by default. No third-party analytics.</p>
      </div>

      <div className="relative z-10 flex items-center justify-center p-6">
        <div className="w-full max-w-sm rounded-panel border border-line/50 bg-surface/55 p-6 shadow-[0_20px_60px_-40px_rgb(0_0_0_/_0.8)] backdrop-blur-md">
          <h2 className="text-2xl font-semibold tracking-tight">{title}</h2>
          <p className="mt-1.5 text-sm text-fg-muted">{subtitle}</p>
          <div className="mt-8">{children}</div>
          <div className="mt-6 text-sm text-fg-muted">{footer}</div>
        </div>
      </div>
    </div>
  )
}
