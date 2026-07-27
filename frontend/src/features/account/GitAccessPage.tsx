import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { gitTokenApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { useAuth } from '../../shared/auth/useAuth'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { SelectField, TextField } from '../../shared/components/Field'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { Modal } from '../../shared/components/Modal'
import { describeError } from '../../shared/components/describeError'
import { useToast } from '../../shared/components/useToast'
import { useInstance } from '../../shared/instance/useInstance'
import { formatRelative } from '../../shared/utils/slugify'
import type { GitAccessToken } from '../../shared/types'
import './GitAccessPage.css'

/** Offered rather than typed: every option here is a considered answer. */
const LIFETIMES = [
  { value: '', label: 'Until I revoke it' },
  { value: '30', label: '30 days' },
  { value: '90', label: '90 days' },
  { value: '365', label: 'A year' },
]

/**
 * The account's git credentials.
 *
 * The screen is built around one fact: the secret exists in exactly one response
 * and can never be shown again. So issuing a token opens a dialog that says so and
 * does not close on a stray click, and the list afterwards shows a hint rather than
 * pretending the value is retrievable.
 */
export function GitAccessPage() {
  const queryClient = useQueryClient()
  const { notify, notifyError } = useToast()
  const { instance } = useInstance()
  const { user } = useAuth()

  const [name, setName] = useState('')
  const [lifetime, setLifetime] = useState('')
  /** Held in memory only: the server will never return it again. */
  const [freshSecret, setFreshSecret] = useState<string | null>(null)
  const [confirmRevoke, setConfirmRevoke] = useState<GitAccessToken | null>(null)

  const tokens = useQuery({
    queryKey: queryKeys.gitTokens.all,
    queryFn: gitTokenApi.list,
  })

  const create = useMutation({
    mutationFn: () =>
      gitTokenApi.create({
        name: name.trim(),
        ...(lifetime ? { expiresInDays: Number(lifetime) } : {}),
      }),
    onSuccess: async (issued) => {
      setFreshSecret(issued.secret)
      setName('')
      setLifetime('')
      await queryClient.invalidateQueries({ queryKey: queryKeys.gitTokens.all })
    },
    onError: (error) => notifyError(describeError(error, 'Could not issue a token.')),
  })

  const revoke = useMutation({
    mutationFn: (tokenId: string) => gitTokenApi.revoke(tokenId),
    onSuccess: async () => {
      setConfirmRevoke(null)
      await queryClient.invalidateQueries({ queryKey: queryKeys.gitTokens.all })
      notify('Token revoked')
    },
    onError: (error) => notifyError(describeError(error, 'Could not revoke that token.')),
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (name.trim()) {
      create.mutate()
    }
  }

  return (
    <div className="gitaccess">
      <div className="page-header">
        <div>
          <p className="mono-label">Account</p>
          <h1 className="page-header__title">Git access</h1>
          <p className="page-header__subtitle">
            Tokens for pushing to and cloning from {instance.name} over HTTPS. Each one belongs to{' '}
            {user?.displayName ?? 'this account'} and carries exactly the access the account has.
          </p>
        </div>
        <div className="page-header__actions">
          <Link className="gitaccess__back" to="/app">
            Back to workspaces
          </Link>
        </div>
      </div>

      <section className="panel">
        <div className="panel__head">
          <h2 className="panel__title">Issue a token</h2>
          <p className="panel__note">
            Use it as the password when git asks. Any username works — the token identifies its
            owner. This is not an SSH key: DevForge serves git over HTTPS only.
          </p>
        </div>

        <form className="gitaccess__form" onSubmit={handleSubmit} noValidate>
          <TextField
            label="What is it for"
            required
            placeholder="Work laptop"
            value={name}
            hint="So you can tell which one to revoke later."
            onChange={(event) => setName(event.target.value)}
          />
          <SelectField
            label="Expires"
            value={lifetime}
            onChange={(event) => setLifetime(event.target.value)}
          >
            {LIFETIMES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </SelectField>
          <Button type="submit" loading={create.isPending} disabled={!name.trim()}>
            {create.isPending ? 'Issuing…' : 'Issue token'}
          </Button>
        </form>
      </section>

      <section className="panel">
        <div className="panel__head">
          <h2 className="panel__title">Your tokens</h2>
          <p className="panel__note">
            Revoking one takes effect immediately, everywhere it is used.
          </p>
        </div>

        {tokens.isPending ? <LoadingState label="Loading your tokens" /> : null}
        {tokens.error ? (
          <ErrorState
            title="Could not load your tokens"
            error={tokens.error}
            onRetry={tokens.refetch}
          />
        ) : null}

        {tokens.data?.length === 0 ? (
          <p className="gitaccess__empty">
            No tokens yet. Issue one above, then use it as the password when git asks.
          </p>
        ) : null}

        {tokens.data?.length ? (
          <ul className="gitaccess__list">
            {tokens.data.map((token) => (
              <li className="gitaccess__token" key={token.id}>
                <div>
                  <p className="gitaccess__name">
                    {token.name}
                    {token.expired ? <Badge tone="danger">Expired</Badge> : null}
                  </p>
                  <p className="gitaccess__meta">
                    <code>{token.hint}…</code> · created {formatRelative(token.createdAt)} ·{' '}
                    {token.lastUsedAt
                      ? `last used ${formatRelative(token.lastUsedAt)}`
                      : 'never used'}
                    {token.expiresAt
                      ? ` · ${token.expired ? 'expired' : 'expires'} ${formatRelative(token.expiresAt)}`
                      : ''}
                  </p>
                </div>
                <Button variant="ghost" size="sm" onClick={() => setConfirmRevoke(token)}>
                  Revoke
                </Button>
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      <Modal
        title="Copy your token now"
        open={freshSecret !== null}
        onClose={() => setFreshSecret(null)}
        footer={<Button onClick={() => setFreshSecret(null)}>Done</Button>}
      >
        <p className="gitaccess__once">
          This is the only time it can be read. Only its digest is stored, so nothing can show it
          again — issue a new one if you lose it.
        </p>
        <code className="gitaccess__secret">{freshSecret}</code>
      </Modal>

      <Modal
        title="Revoke this token?"
        open={confirmRevoke !== null}
        onClose={() => setConfirmRevoke(null)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmRevoke(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={revoke.isPending}
              onClick={() => confirmRevoke && revoke.mutate(confirmRevoke.id)}
            >
              Revoke
            </Button>
          </>
        }
      >
        <p>
          Anything signed in with <strong>{confirmRevoke?.name}</strong> stops working straight
          away. Nothing it already pushed is affected.
        </p>
      </Modal>
    </div>
  )
}
