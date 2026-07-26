import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../../shared/api/client'
import { useAuth } from '../../shared/auth/useAuth'
import { Button } from '../../shared/components/Button'
import { TextField } from '../../shared/components/Field'
import { describeError } from '../../shared/components/describeError'
import { AuthShell } from './AuthShell'
import './AuthForm.css'

export function LoginPage() {
  const { logIn, isAuthenticated, isInitialising } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  // Where the user was headed before the redirect, if anywhere.
  const destination = (location.state as { from?: string } | null)?.from ?? '/app'

  if (!isInitialising && isAuthenticated) {
    return <Navigate to={destination} replace />
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await logIn({ email, password })
      navigate(destination, { replace: true })
    } catch (caught) {
      setError(caught)
    } finally {
      setSubmitting(false)
    }
  }

  const fieldError = (field: string) =>
    error instanceof ApiError ? error.fieldError(field) : undefined

  return (
    <AuthShell
      title="Sign in"
      subtitle="Pick up where your team left off."
      footer={
        <>
          No account yet? <Link to="/register">Create one</Link>
        </>
      }
    >
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        {error ? (
          <p className="auth-form__error" role="alert">
            {describeError(error, 'Could not sign in.')}
          </p>
        ) : null}

        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          error={fieldError('email')}
          onChange={(event) => setEmail(event.target.value)}
        />

        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          error={fieldError('password')}
          onChange={(event) => setPassword(event.target.value)}
        />

        <Button type="submit" loading={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </AuthShell>
  )
}
