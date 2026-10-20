import { OWNER_EMAIL, ownerMailto } from '@/demo/ownerContact'

/** Polite cost note + how to ask for a real hosted demo. */
export function DemoHostNotice() {
  return (
    <aside
      id="try-demo"
      className="rounded-panel border border-accent/35 bg-accent-muted/80 px-5 py-4 text-sm leading-relaxed text-fg"
    >
      <p className="text-[10px] tracking-[0.18em] text-accent uppercase">Sample demo</p>
      <p className="mt-2 text-fg">
        This walkthrough uses illustrated sample memories so you can see what MemoryGraph offers
        without a live server. Keeping the real app online costs more than we can always cover, so
        we need a little support to bear that cost.
      </p>
      <p className="mt-2 text-fg-muted">
        If you would like a real demo with your own photos or chats, email{' '}
        <a href={ownerMailto()} className="text-accent underline decoration-accent/40 underline-offset-2 hover:text-fg">
          {OWNER_EMAIL}
        </a>
        .
      </p>
    </aside>
  )
}
