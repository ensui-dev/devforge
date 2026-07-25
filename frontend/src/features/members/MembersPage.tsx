import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { memberApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { useAuth } from '../../shared/auth/useAuth'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { SelectField, TextField } from '../../shared/components/Field'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { describeError } from '../../shared/components/describeError'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import {
  ROLE_DESCRIPTIONS,
  WORKSPACE_ROLES,
  roleAtLeast,
  type WorkspaceMember,
  type WorkspaceRole,
} from '../../shared/types'
import { formatDate, initials } from '../../shared/utils/slugify'
import { useCurrentWorkspace } from '../workspaces/WorkspaceContext'
import './MembersPage.css'

export function MembersPage() {
  const workspace = useCurrentWorkspace()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const { notify, notifyError } = useToast()

  const membersQuery = useQuery({
    queryKey: queryKeys.members.all(workspace.id),
    queryFn: () => memberApi.list(workspace.id),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.members.all(workspace.id) })
    // A role change can alter what the current user may do.
    queryClient.invalidateQueries({ queryKey: queryKeys.workspaces.detail(workspace.id) })
  }

  const addMember = useMutation({
    mutationFn: (input: { email: string; role: WorkspaceRole }) =>
      memberApi.add(workspace.id, input.email, input.role),
    onSuccess: invalidate,
  })

  const changeRole = useMutation({
    mutationFn: (input: { userId: string; role: WorkspaceRole }) =>
      memberApi.changeRole(workspace.id, input.userId, input.role),
    onSuccess: invalidate,
  })

  const removeMember = useMutation({
    mutationFn: (userId: string) => memberApi.remove(workspace.id, userId),
    onSuccess: invalidate,
  })

  const [inviteOpen, setInviteOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<WorkspaceRole>('MEMBER')
  const [inviteError, setInviteError] = useState<unknown>(null)
  const [pendingRemoval, setPendingRemoval] = useState<WorkspaceMember | null>(null)

  const canAdmin = roleAtLeast(workspace.callerRole, 'ADMIN')

  const handleInvite = async (event: FormEvent) => {
    event.preventDefault()
    setInviteError(null)
    try {
      const member = await addMember.mutateAsync({ email: email.trim(), role })
      notify(`Added ${member.displayName}`)
      setInviteOpen(false)
      setEmail('')
      setRole('MEMBER')
    } catch (caught) {
      setInviteError(caught)
    }
  }

  const handleRoleChange = async (member: WorkspaceMember, nextRole: WorkspaceRole) => {
    try {
      await changeRole.mutateAsync({ userId: member.userId, role: nextRole })
      notify(`${member.displayName} is now ${nextRole.toLowerCase()}`)
    } catch (caught) {
      notifyError(describeError(caught, 'Could not change the role.'))
    }
  }

  const handleRemove = async () => {
    if (!pendingRemoval) {
      return
    }
    const leaving = pendingRemoval.userId === user?.id
    try {
      await removeMember.mutateAsync(pendingRemoval.userId)
      notify(leaving ? `You left ${workspace.name}` : `Removed ${pendingRemoval.displayName}`)
      setPendingRemoval(null)
      if (leaving) {
        window.location.assign('/')
      }
    } catch (caught) {
      notifyError(describeError(caught, 'Could not remove the member.'))
    }
  }

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <p className="mono-label">Team</p>
          <h1 className="page-header__title">Who can see this workspace</h1>
          <p className="page-header__subtitle">
            Roles decide what each person can change. Everyone listed here can read the
            documentation and boards.
          </p>
        </div>
        {canAdmin ? (
          <div className="page-header__actions">
            <Button onClick={() => setInviteOpen(true)}>Add member</Button>
          </div>
        ) : null}
      </div>

      {membersQuery.isPending ? <LoadingState label="Loading team" /> : null}
      {membersQuery.error ? (
        <ErrorState error={membersQuery.error} onRetry={membersQuery.refetch} />
      ) : null}

      {membersQuery.data ? (
        <ul className="member-list">
          {membersQuery.data.map((member) => {
            const isSelf = member.userId === user?.id
            // Nobody may act on someone ranked above them; the backend enforces
            // this too, so this only keeps the UI honest.
            const canActOnMember = canAdmin && roleAtLeast(workspace.callerRole, member.role)

            return (
              <li className="member-row" key={member.userId}>
                <span className="member-row__avatar" aria-hidden="true">
                  {initials(member.displayName)}
                </span>

                <div className="member-row__identity">
                  <p className="member-row__name">
                    {member.displayName}
                    {isSelf ? <span className="member-row__you"> · you</span> : null}
                  </p>
                  <p className="member-row__email">{member.email ?? 'No address on file'}</p>
                </div>

                <span className="member-row__joined mono-label">
                  Joined {formatDate(member.joinedAt)}
                </span>

                {canActOnMember && !isSelf ? (
                  <label className="member-row__role">
                    <span className="visually-hidden">Role for {member.displayName}</span>
                    <select
                      className="field__control field__control--select"
                      value={member.role}
                      disabled={changeRole.isPending}
                      onChange={(event) =>
                        handleRoleChange(member, event.target.value as WorkspaceRole)
                      }
                    >
                      {WORKSPACE_ROLES.filter((candidate) =>
                        // An admin cannot grant a role above their own.
                        roleAtLeast(workspace.callerRole, candidate),
                      ).map((candidate) => (
                        <option key={candidate} value={candidate}>
                          {candidate}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : (
                  <Badge tone={member.role === 'VIEWER' ? 'neutral' : 'trace'}>{member.role}</Badge>
                )}

                {isSelf || canActOnMember ? (
                  <Button variant="ghost" size="sm" onClick={() => setPendingRemoval(member)}>
                    {isSelf ? 'Leave' : 'Remove'}
                  </Button>
                ) : (
                  <span />
                )}
              </li>
            )
          })}
        </ul>
      ) : null}

      <Modal
        title="Add a member"
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        footer={
          <>
            <Button variant="ghost" onClick={() => setInviteOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" form="invite-member" loading={addMember.isPending}>
              Add member
            </Button>
          </>
        }
      >
        <form id="invite-member" onSubmit={handleInvite} noValidate>
          <div className="stack">
            {inviteError ? (
              <p className="form-error" role="alert">
                {describeError(inviteError, 'Could not add the member.')}
              </p>
            ) : null}

            <TextField
              label="Email"
              type="email"
              required
              autoFocus
              value={email}
              hint="They need a DevForge account with this address."
              onChange={(event) => setEmail(event.target.value)}
            />

            <SelectField
              label="Role"
              value={role}
              hint={ROLE_DESCRIPTIONS[role]}
              onChange={(event) => setRole(event.target.value as WorkspaceRole)}
            >
              {WORKSPACE_ROLES.filter((candidate) =>
                roleAtLeast(workspace.callerRole, candidate),
              ).map((candidate) => (
                <option key={candidate} value={candidate}>
                  {candidate}
                </option>
              ))}
            </SelectField>
          </div>
        </form>
      </Modal>

      <Modal
        title={pendingRemoval?.userId === user?.id ? 'Leave this workspace?' : 'Remove this member?'}
        open={pendingRemoval !== null}
        onClose={() => setPendingRemoval(null)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setPendingRemoval(null)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={handleRemove} loading={removeMember.isPending}>
              {pendingRemoval?.userId === user?.id ? 'Leave workspace' : 'Remove member'}
            </Button>
          </>
        }
      >
        <p>
          {pendingRemoval?.userId === user?.id
            ? `You will lose access to ${workspace.name} until someone adds you back.`
            : `${pendingRemoval?.displayName} will lose access to ${workspace.name}. Their documents and tasks stay.`}
        </p>
      </Modal>
    </div>
  )
}
