import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../shared/api/client'
import { instanceApi, userApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { Button } from '../../shared/components/Button'
import { TextField } from '../../shared/components/Field'
import { Modal } from '../../shared/components/Modal'
import { describeError } from '../../shared/components/describeError'
import { useToast } from '../../shared/components/useToast'

interface AddOperatorDialogProps {
  open: boolean
  onClose: () => void
}

/**
 * Adds someone to the instance, whether or not they already have an account.
 *
 * <p>Two paths because an operator has two situations: appointing a colleague who
 * is already here, and populating a closed instance where nobody can sign
 * themselves up. Search first, since promoting an existing account is the case
 * that must not accidentally create a duplicate.
 */
export function AddOperatorDialog({ open, onClose }: AddOperatorDialogProps) {
  const queryClient = useQueryClient()
  const { notify } = useToast()

  const [query, setQuery] = useState('')
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)

  const matches = useQuery({
    queryKey: queryKeys.users.search(query.trim()),
    queryFn: () => userApi.search(query.trim()),
    enabled: open && query.trim().length >= 2,
  })

  const done = async (message: string) => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.instance.admins })
    notify(message)
    onClose()
  }

  const promote = useMutation({
    mutationFn: (userId: string) => instanceApi.setInstanceAdmin(userId, true),
    onSuccess: (user) => done(`${user.displayName} can now administer this instance`),
    onError: setError,
  })

  const create = useMutation({
    mutationFn: () =>
      instanceApi.createAccount({
        email: email.trim(),
        displayName: displayName.trim(),
        password,
        instanceAdmin: true,
      }),
    onSuccess: (user) => done(`Created ${user.email}`),
    onError: setError,
  })

  const fieldError = (field: string) =>
    error instanceof ApiError ? error.fieldError(field) : undefined

  const handleCreate = (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    create.mutate()
  }

  const results = matches.data ?? []

  return (
    <Modal title="Add an operator" open={open} onClose={onClose} width="md">
      {error ? (
        <p className="instance__error" role="alert">
          {describeError(error, 'Could not add that operator.')}
        </p>
      ) : null}

      <section className="add-operator__section">
        <h3 className="add-operator__heading">Appoint someone who is already here</h3>
        <TextField
          label="Search accounts"
          placeholder="Name or email"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {query.trim().length >= 2 ? (
          results.length > 0 ? (
            <ul className="add-operator__results">
              {results.map((candidate) => (
                <li key={candidate.id}>
                  <span className="add-operator__who">
                    <span>{candidate.displayName}</span>
                    <span className="add-operator__email">{candidate.email}</span>
                  </span>
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={promote.isPending}
                    onClick={() => {
                      setError(null)
                      promote.mutate(candidate.id)
                    }}
                  >
                    Appoint
                  </Button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="add-operator__none">
              {matches.isPending ? 'Searching…' : 'No account matches that.'}
            </p>
          )
        ) : null}
      </section>

      <form className="add-operator__section" onSubmit={handleCreate} noValidate>
        <h3 className="add-operator__heading">Or create an account</h3>
        <p className="add-operator__note">
          Works whatever the registration mode is — this is how you add people to a closed instance.
          Send them the password and have them change it.
        </p>
        <TextField
          label="Name"
          required
          value={displayName}
          error={fieldError('displayName')}
          onChange={(event) => setDisplayName(event.target.value)}
        />
        <TextField
          label="Email"
          type="email"
          required
          value={email}
          error={fieldError('email')}
          onChange={(event) => setEmail(event.target.value)}
        />
        <TextField
          label="Temporary password"
          type="password"
          required
          value={password}
          hint="At least 8 characters."
          error={fieldError('password')}
          onChange={(event) => setPassword(event.target.value)}
        />
        <Button type="submit" loading={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create operator account'}
        </Button>
      </form>
    </Modal>
  )
}
