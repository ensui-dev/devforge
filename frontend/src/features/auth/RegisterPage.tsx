import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../../shared/api/client'
import { useAuth } from '../../shared/auth/useAuth'
import { Button } from '../../shared/components/Button'
import { TextField } from '../../shared/components/Field'
import { describeError } from '../../shared/components/describeError'
import { AuthShell } from './AuthShell'
import './AuthForm.css'

export function RegisterPage() {
  const { register, isAuthenticated, isInitialising } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!isInitialising && isAuthenticated) {
    return <Navigate to="/" replace />
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await register({ email, displayName, password })
      navigate('/', { replace: true })
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
      title="Create your account"
      subtitle="Start documenting a project, then invite the people building it."
      footer={
        <>
          Already have an account? <Link to="/login">Sign in</Link>
        </>
      }
    >
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        {error ? (
          <p className="auth-form__error" role="alert">
            {describeError(error, 'Could not create the account.')}
          </p>
        ) : null}

        <TextField
          label="Name"
          autoComplete="name"
          required
          value={displayName}
          error={fieldError('displayName')}
          onChange={(event) => setDisplayName(event.target.value)}
        />

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
          autoComplete="new-password"
          required
          value={password}
          hint="At least 8 characters."
          error={fieldError('password')}
          onChange={(event) => setPassword(event.target.value)}
        />

        <Button type="submit" loading={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
    </AuthShell>
  )
}
