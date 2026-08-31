import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { TextField } from '@/components/ui/TextField'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { authApi } from '@/features/auth/authApi'
import { useAuth } from '@/features/auth/useAuth'
import { ApiRequestError } from '@/lib/ApiRequestError'

const MIN_PASSWORD_LENGTH = 10

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const { data: options } = useQuery({
    queryKey: ['auth', 'registration'],
    queryFn: () => authApi.registration(),
  })
  const inviteRequired = options?.inviteRequired ?? false

  const mutation = useMutation({
    mutationFn: () =>
      register({
        displayName,
        email,
        password,
        ...(inviteRequired ? { inviteCode } : {}),
      }),
    onSuccess: () => navigate('/', { replace: true }),
  })

  const error = mutation.error instanceof ApiRequestError ? mutation.error : null

  return (
    <AuthLayout
      title="Create your account"
      subtitle="One account holds one person's memories."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="text-accent hover:underline">
            Sign in
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
        {error && error.code !== 'VALIDATION_FAILED' && error.code !== 'INVITE_INVALID' && (
          <Alert>{error.message}</Alert>
        )}

        {inviteRequired && (
          <TextField
            label="Invite code"
            autoComplete="off"
            required
            value={inviteCode}
            error={error?.code === 'INVITE_INVALID' ? error.message : error?.fieldError('inviteCode')}
            hint="The person who invited you sent this."
            onChange={(event) => setInviteCode(event.target.value)}
          />
        )}

        <TextField
          label="Name"
          autoComplete="name"
          required
          value={displayName}
          error={error?.fieldError('displayName')}
          onChange={(event) => setDisplayName(event.target.value)}
        />
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
          autoComplete="new-password"
          required
          minLength={MIN_PASSWORD_LENGTH}
          value={password}
          error={error?.fieldError('password')}
          hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
          onChange={(event) => setPassword(event.target.value)}
        />

        <Button type="submit" isLoading={mutation.isPending} className="mt-2 w-full">
          Create account
        </Button>
      </form>
    </AuthLayout>
  )
}
