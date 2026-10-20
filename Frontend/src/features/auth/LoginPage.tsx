import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { TextField } from '@/components/ui/TextField'
import { OWNER_EMAIL } from '@/demo/ownerContact'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { useAuth } from '@/features/auth/useAuth'
import { ApiRequestError } from '@/lib/ApiRequestError'
import { clearLogoutReason, readLogoutReason } from '@/lib/session'

export function LoginPage() {
  const { login, enterDemo } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [notice] = useState(() => {
    if (location.state && typeof location.state === 'object' && 'passwordReset' in location.state) {
      return 'Password updated. Sign in with your new password.'
    }
    if (readLogoutReason() === 'idle') {
      return 'You were signed out after 30 minutes of inactivity.'
    }
    return null
  })

  const mutation = useMutation({
    mutationFn: () => login({ email, password }),
    onSuccess: () => {
      clearLogoutReason()
      navigate('/', { replace: true })
    },
  })

  const demoMutation = useMutation({
    mutationFn: enterDemo,
    onSuccess: () => {
      clearLogoutReason()
      navigate('/', { replace: true })
    },
  })

  const error = mutation.error instanceof ApiRequestError ? mutation.error : null

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to search your memories."
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="text-accent hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          mutation.mutate()
        }}
      >
        {notice && <Alert tone="info">{notice}</Alert>}
        {error && error.code !== 'VALIDATION_FAILED' && <Alert>{error.message}</Alert>}

        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          error={error?.fieldError('email')}
          onChange={(event) => setEmail(event.target.value)}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          error={error?.fieldError('password')}
          onChange={(event) => setPassword(event.target.value)}
        />

        <div className="-mt-1 text-right text-sm">
          <Link to="/forgot-password" className="text-accent hover:underline">
            Forgot password?
          </Link>
        </div>

        <Button type="submit" isLoading={mutation.isPending} className="mt-2 w-full">
          Sign in
        </Button>

        <div className="relative my-1 flex items-center gap-3">
          <span className="h-px flex-1 bg-line/70" />
          <span className="text-[10px] tracking-[0.16em] text-fg-muted uppercase">or</span>
          <span className="h-px flex-1 bg-line/70" />
        </div>

        <Button
          type="button"
          variant="secondary"
          isLoading={demoMutation.isPending}
          className="w-full"
          onClick={() => demoMutation.mutate()}
        >
          Try demo
        </Button>
        <p className="text-center text-xs leading-relaxed text-fg-muted">
          Sample archive in this browser. Live hosting costs extra — email {OWNER_EMAIL} for a real
          demo.
        </p>
      </form>
    </AuthLayout>
  )
}

