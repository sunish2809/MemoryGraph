import { useQuery } from '@tanstack/react-query'

import { systemApi } from '@/features/auth/authApi'

/**
 * Small liveness indicator. Useful during development to tell "no memories yet" apart from
 * "the backend is not running".
 */
export function BackendStatus() {
  const { data, isError, isPending } = useQuery({
    queryKey: ['health'],
    queryFn: systemApi.health,
    refetchInterval: 60_000,
  })

  const tone = isPending ? 'bg-fg-muted' : isError ? 'bg-danger' : 'bg-support'
  const label = isPending ? 'Checking API' : isError ? 'API unreachable' : `API ${data?.status}`

  return (
    <div className="flex items-center gap-2 rounded-xl border border-line px-3 py-2 text-xs text-fg-muted">
      <span aria-hidden="true" className={`size-2 rounded-full ${tone}`} />
      {label}
      {data?.version && <span className="ml-auto opacity-60">{data.version}</span>}
    </div>
  )
}
