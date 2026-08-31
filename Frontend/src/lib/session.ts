const STORAGE_KEY = 'memorygraph.session'

interface StoredSession {
  accessToken: string
  expiresAt: string
}

/**
 * Persists the access token so a page reload does not log the user out.
 *
 * The token lives in localStorage, which is readable by any script running on the page. That is an
 * accepted trade-off for the MVP given short-lived tokens and no third-party scripts; moving to a
 * refresh token in an httpOnly cookie is tracked as a follow-up.
 */
export const sessionStorageKey = STORAGE_KEY

export function readSession(): StoredSession | null {
  const raw = window.localStorage.getItem(STORAGE_KEY)
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
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  window.localStorage.removeItem(STORAGE_KEY)
}

export function readAccessToken(): string | null {
  return readSession()?.accessToken ?? null
}
