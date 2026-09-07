/** Signed-out after this much silence in an open tab (mouse, keys, touch, or API traffic). */
export const IDLE_TIMEOUT_MS = 30 * 60 * 1000

const CHECK_MS = 15_000
const BUMP_THROTTLE_MS = 1_000

let lastActivityAt = Date.now()
let lastBumpAt = 0

const idleListeners = new Set<() => void>()

export function touchSessionActivity(): void {
  lastActivityAt = Date.now()
}

/**
 * Activity that should keep a signed-in tab alive. Throttled so pointer-move does not flood.
 */
export function bumpSessionActivity(): void {
  const now = Date.now()
  if (now - lastBumpAt < BUMP_THROTTLE_MS) return
  lastBumpAt = now
  lastActivityAt = now
}

export function onIdleTimeout(listener: () => void): () => void {
  idleListeners.add(listener)
  return () => idleListeners.delete(listener)
}

export function isIdleTimedOut(): boolean {
  return Date.now() - lastActivityAt >= IDLE_TIMEOUT_MS
}

/**
 * Starts watching this tab for inactivity. Call only while a user is signed in.
 */
export function startIdleWatch(): () => void {
  touchSessionActivity()

  const bump = () => bumpSessionActivity()
  const events = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'click'] as const
  for (const event of events) {
    window.addEventListener(event, bump, { passive: true })
  }

  const check = () => {
    if (!isIdleTimedOut()) return
    idleListeners.forEach((listener) => listener())
  }

  const intervalId = window.setInterval(check, CHECK_MS)
  const onVisibility = () => {
    if (document.visibilityState === 'visible') check()
  }
  document.addEventListener('visibilitychange', onVisibility)

  return () => {
    for (const event of events) {
      window.removeEventListener(event, bump)
    }
    window.clearInterval(intervalId)
    document.removeEventListener('visibilitychange', onVisibility)
  }
}
