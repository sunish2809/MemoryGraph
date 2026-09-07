const STORAGE_KEY = 'memorygraph.session'
const LOGOUT_REASON_KEY = 'memorygraph.logoutReason'

interface StoredSession {
  accessToken: string
  expiresAt: string
}

export type LogoutReason = 'idle'

/**
 * Persists the access token for this browser tab only.
 *
 * sessionStorage is cleared when the tab (or last tab of the browser session) closes, so opening
 * the app again requires a fresh sign-in. Refreshing the same tab keeps the session. Leftover
 * localStorage tokens from earlier builds are migrated once, then removed.
 */
export const sessionStorageKey = STORAGE_KEY

function browserStore(): Storage | null {
  if (typeof window === 'undefined') return null
  migrateLegacyLocalStorage()
  return window.sessionStorage
}

function migrateLegacyLocalStorage(): void {
  const leftover = window.localStorage.getItem(STORAGE_KEY)
  if (leftover && !window.sessionStorage.getItem(STORAGE_KEY)) {
    window.sessionStorage.setItem(STORAGE_KEY, leftover)
  }
  if (leftover) {
    window.localStorage.removeItem(STORAGE_KEY)
  }
}

export function readSession(): StoredSession | null {
  const store = browserStore()
  if (!store) return null
  const raw = store.getItem(STORAGE_KEY)
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as Partial<StoredSession>
    if (!parsed.accessToken || !parsed.expiresAt) return null
    if (new Date(parsed.expiresAt).getTime() <= Date.now()) {
      clearSession()
      return null
    }
    return { accessToken: parsed.accessToken, expiresAt: parsed.expiresAt }
  } catch {
    clearSession()
    return null
  }
}

export function writeSession(session: StoredSession): void {
  const store = browserStore()
  if (!store) return
  store.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  if (typeof window === 'undefined') return
  window.sessionStorage.removeItem(STORAGE_KEY)
  window.localStorage.removeItem(STORAGE_KEY)
}

export function readAccessToken(): string | null {
  return readSession()?.accessToken ?? null
}

export function rememberLogoutReason(reason: LogoutReason): void {
  if (typeof window === 'undefined') return
  window.sessionStorage.setItem(LOGOUT_REASON_KEY, reason)
}

export function readLogoutReason(): LogoutReason | null {
  if (typeof window === 'undefined') return null
  return window.sessionStorage.getItem(LOGOUT_REASON_KEY) === 'idle' ? 'idle' : null
}

export function clearLogoutReason(): void {
  if (typeof window === 'undefined') return
  window.sessionStorage.removeItem(LOGOUT_REASON_KEY)
}
