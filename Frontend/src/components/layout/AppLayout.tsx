import { NavLink, Outlet } from 'react-router-dom'

import { NetworkBackdrop } from '@/components/layout/NetworkBackdrop'
import { Button } from '@/components/ui/Button'
import { useAuth } from '@/features/auth/useAuth'
import { BackendStatus } from '@/features/system/BackendStatus'

const navigation = [
  { to: '/', label: 'Dashboard', hint: 'Overview' },
  { to: '/timeline', label: 'Timeline', hint: 'By date' },
  { to: '/search', label: 'Search', hint: 'Find' },
  { to: '/people', label: 'People', hint: 'Who' },
  { to: '/faces', label: 'Faces', hint: 'Names' },
  { to: '/places', label: 'Places', hint: 'Where' },
  { to: '/trips', label: 'Trips', hint: 'When away' },
  { to: '/graph', label: 'Graph', hint: 'Links' },
  { to: '/import', label: 'Import', hint: 'Bring in' },
  { to: '/ask', label: 'Ask AI', hint: 'Questions' },
  { to: '/privacy', label: 'Privacy', hint: 'Trust' },
]

export function AppLayout() {
  const { user, logout } = useAuth()
  const initials = (user?.displayName ?? 'M')
    .split(/\s+/)
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()

  return (
    <div className="relative h-dvh w-full overflow-hidden">
      <NetworkBackdrop />
      <div className="relative z-10 flex h-full w-full overflow-hidden">
        <aside className="hidden h-full w-[15.5rem] shrink-0 flex-col border-r border-line/40 bg-surface/20 px-3 py-5 backdrop-blur-md lg:flex">
          <Wordmark />
          <nav className="scroll-pane mt-8 flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto pr-1">
            {navigation.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  `flex items-center justify-between rounded-lg px-3 py-2 text-sm transition-all ${
                    isActive
                      ? 'bg-accent-muted text-fg shadow-[inset_3px_0_0_0_var(--color-accent)]'
                      : 'text-fg-muted hover:bg-surface-raised/70 hover:text-fg'
                  }`
                }
              >
                <span className="font-medium">{item.label}</span>
                <span className="text-[10px] tracking-wide text-fg-muted/50 uppercase">{item.hint}</span>
              </NavLink>
            ))}
          </nav>
          <div className="mt-4 border-t border-line/50 pt-4">
            <BackendStatus />
          </div>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <header className="flex shrink-0 items-center justify-between gap-3 border-b border-line/40 bg-surface/18 px-4 py-3 backdrop-blur-md sm:px-5">
            <div className="lg:hidden">
              <Wordmark compact />
            </div>
            <p className="hidden text-xs tracking-[0.18em] text-fg-muted uppercase sm:block lg:ml-0">
              Private archive
            </p>
            <div className="ml-auto flex items-center gap-3">
              <div className="hidden text-right sm:block">
                <p className="text-sm font-medium text-fg">{user?.displayName}</p>
                <p className="text-xs text-fg-muted">{user?.email}</p>
              </div>
              <div
                aria-hidden
                className="grid size-9 place-items-center rounded-full border border-accent/40 bg-accent-muted text-xs font-semibold text-accent"
              >
                {initials}
              </div>
              <Button variant="ghost" className="!px-2 !py-1.5 text-xs" onClick={logout}>
                Sign out
              </Button>
            </div>
          </header>

          <main className="flex min-h-0 flex-1 flex-col overflow-hidden p-3 sm:p-4 lg:p-5">
            <Outlet />
          </main>

          <nav className="scroll-pane flex shrink-0 gap-1 overflow-x-auto border-t border-line/40 bg-surface/25 px-2 py-2 backdrop-blur-md lg:hidden">
            {navigation.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  `shrink-0 rounded-lg px-3 py-2 text-center text-xs font-medium ${
                    isActive ? 'bg-accent-muted text-accent' : 'text-fg-muted'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </div>
    </div>
  )
}

function Wordmark({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`flex items-center gap-2.5 ${compact ? '' : 'px-2'}`}>
      <span
        aria-hidden="true"
        className="grid size-8 place-items-center rounded-md bg-gradient-to-br from-accent to-accent-strong font-display text-sm font-bold text-ink"
      >
        M
      </span>
      <div className="leading-tight">
        <span className="font-display text-base font-semibold tracking-tight">MemoryGraph</span>
        {!compact && <p className="text-[10px] tracking-[0.16em] text-fg-muted uppercase">Your life, indexed</p>}
      </div>
    </div>
  )
}
