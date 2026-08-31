import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { Alert } from '@/components/ui/Alert'
import { Panel } from '@/components/ui/Panel'
import { useGoogleCallback } from '@/features/imports/useImports'
import { ApiRequestError } from '@/lib/ApiRequestError'

/**
 * Google OAuth redirect target. Exchanges the auth code while the user is still signed in,
 * then returns to Import.
 */
export function GoogleOAuthCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const callback = useGoogleCallback()
  const [error, setError] = useState<string>()
  const started = useRef(false)

  useEffect(() => {
    if (started.current) {
      return
    }
    started.current = true

    const code = params.get('code')
    const state = params.get('state')
    const oauthError = params.get('error')

    if (oauthError) {
      setError(oauthError === 'access_denied' ? 'Google access was denied.' : `Google error: ${oauthError}`)
      return
    }
    if (!code || !state) {
      setError('Missing OAuth code or state from Google.')
      return
    }

    void callback
      .mutateAsync({ code, state })
      .then(() => navigate('/import?google=connected', { replace: true }))
      .catch((err) => {
        setError(err instanceof ApiRequestError ? err.message : 'Could not connect Google Photos')
      })
  }, [callback, navigate, params])

  return (
    <div className="mx-auto max-w-lg px-4 py-16">
      <Panel title="Connecting Google Photos">
        {error ? (
          <div className="flex flex-col gap-3">
            <Alert>{error}</Alert>
            <Link className="text-sm text-accent hover:underline" to="/import">
              Back to Import
            </Link>
          </div>
        ) : (
          <p className="text-sm text-fg-muted">Finishing sign-in with Google…</p>
        )}
      </Panel>
    </div>
  )
}
