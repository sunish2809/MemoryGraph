import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { TextField } from '@/components/ui/TextField'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { authApi } from '@/features/auth/authApi'
import { ApiRequestError } from '@/lib/ApiRequestError'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')

  const mutation = useMutation({
    mutationFn: () => authApi.forgotPassword(email),
  })

  const error = mutation.error instanceof ApiRequestError ? mutation.error : null

  return (
    <AuthLayout
      title="Reset your password"
      subtitle="We will send a one-time link if this email has an account."
      footer={
        <>
          Remembered it?{' '}
          <Link to="/login" className="text-accent hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      {mutation.isSuccess ? (
        <Alert tone="info">{mutation.data.message}</Alert>
      ) : (
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            mutation.mutate()
          }}
        >
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

          <Button type="submit" isLoading={mutation.isPending} className="mt-2 w-full">
            Send reset link
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
