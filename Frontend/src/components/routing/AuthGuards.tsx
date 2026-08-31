import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'

/** Blocks the application shell until a session is confirmed, then hands over to the layout. */
export function RequireAuth() {
  const { isAuthenticated, isRestoring } = useAuth()
  const location = useLocation()

  if (isRestoring) {
    return <FullPageLoader />
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}

/** Keeps signed-in users out of the login and register screens. */
export function RedirectIfAuthenticated() {
  const { isAuthenticated, isRestoring } = useAuth()

  if (isRestoring) {
    return <FullPageLoader />
  }

  return isAuthenticated ? <Navigate to="/" replace /> : <Outlet />
}

function FullPageLoader() {
  return (
    <div className="grid min-h-dvh place-items-center">
      <span
        role="status"
        aria-label="Loading"
        className="size-6 animate-spin rounded-full border-2 border-accent border-t-transparent"
      />
    </div>
  )
}
