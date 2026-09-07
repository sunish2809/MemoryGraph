import { useMutation } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { TextField } from '@/components/ui/TextField'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { authApi } from '@/features/auth/authApi'
import { useAuth } from '@/features/auth/useAuth'
import { ApiRequestError } from '@/lib/ApiRequestError'

const MIN_PASSWORD_LENGTH = 10

export function ResetPasswordPage() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [searchParams] = useSearchParams()
  const token = useMemo(() => searchParams.get('token')?.trim() ?? '', [searchParams])
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [mismatch, setMismatch] = useState<string | undefined>()

  const mutation = useMutation({
    mutationFn: () => authApi.resetPassword(token, password),
    onSuccess: () => {
      logout()
      navigate('/login', { replace: true, state: { passwordReset: true } })
    },
  })

  const error = mutation.error instanceof ApiRequestError ? mutation.error : null

  return (
    <AuthLayout
      title="Choose a new password"
      subtitle="This link works once and then expires."
      footer={
        <>
          Back to{' '}
          <Link to="/login" className="text-accent hover:underline">
            sign in
          </Link>
        </>
      }
    >
      {!token ? (
        <Alert>This reset link is missing a token. Request a new one from the sign-in page.</Alert>
      ) : (
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (password !== confirm) {
              setMismatch('Passwords do not match.')
              return
            }
            setMismatch(undefined)
            mutation.mutate()
          }}
        >
          {error && error.code !== 'VALIDATION_FAILED' && <Alert>{error.message}</Alert>}

          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            required
            minLength={MIN_PASSWORD_LENGTH}
            value={password}
            error={error?.fieldError('password')}
            hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
            onChange={(event) => setPassword(event.target.value)}
          />
          <TextField
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            required
            minLength={MIN_PASSWORD_LENGTH}
            value={confirm}
            error={mismatch}
            onChange={(event) => setConfirm(event.target.value)}
          />

          <Button type="submit" isLoading={mutation.isPending} className="mt-2 w-full">
            Update password
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
