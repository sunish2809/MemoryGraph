const PREFIX = 'memorygraph.welcome.seen.'

export function welcomeStorageKey(userId: string): string {
  return `${PREFIX}${userId}`
}

export function hasSeenWelcome(userId: string | undefined): boolean {
  if (!userId || typeof window === 'undefined') {
    return true
  }
  return window.localStorage.getItem(welcomeStorageKey(userId)) === '1'
}

export function markWelcomeSeen(userId: string): void {
  window.localStorage.setItem(welcomeStorageKey(userId), '1')
}
