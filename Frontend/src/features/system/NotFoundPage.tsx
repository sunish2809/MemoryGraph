import { Link } from 'react-router-dom'

import { NetworkBackdrop } from '@/components/layout/NetworkBackdrop'
import { Button } from '@/components/ui/Button'

export function NotFoundPage() {
  return (
    <div className="relative grid min-h-dvh place-items-center p-6 text-center">
      <NetworkBackdrop />
      <div className="relative z-10 flex flex-col items-center gap-4">
        <p className="text-sm tracking-widest text-fg-muted uppercase">404</p>
        <h1 className="text-2xl font-semibold tracking-tight">This page does not exist</h1>
        <Link to="/">
          <Button variant="secondary">Back to dashboard</Button>
        </Link>
      </div>
    </div>
  )
}
