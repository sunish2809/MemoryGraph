/** Shown on the demo so visitors can ask for a live instance. */
export const OWNER_EMAIL = 'sunish.5186@gmail.com'

export function ownerMailto(): string {
  return (
    `mailto:${OWNER_EMAIL}` +
    `?subject=${encodeURIComponent('MemoryGraph real demo')}` +
    `&body=${encodeURIComponent('Hi Sunish — I tried the MemoryGraph demo and would like a live walkthrough.')}`
  )
}
