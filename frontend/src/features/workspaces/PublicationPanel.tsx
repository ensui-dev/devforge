import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { publicationApi } from '../../shared/api/endpoints'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { describeError } from '../../shared/components/describeError'
import { ErrorState } from '../../shared/components/Feedback'
import { Modal } from '../../shared/components/Modal'
import { useToast } from '../../shared/components/useToast'
import { roleAtLeast, type Workspace } from '../../shared/types'
import { formatDate } from '../../shared/utils/slugify'
import './PublicationPanel.css'

/**
 * Publishing a workspace's documentation.
 *
 * Publishing exposes every page that is not marked internal, so the confirmation
 * states that count plainly rather than asking for a yes to an abstract question.
 */
export function PublicationPanel({ workspace }: { workspace: Workspace }) {
  const queryClient = useQueryClient()
  const { notify, notifyError } = useToast()
  const [confirmOpen, setConfirmOpen] = useState(false)

  const publication = useQuery({
    queryKey: ['workspaces', workspace.id, 'publication'],
    queryFn: () => publicationApi.get(workspace.id),
  })

  const setPublished = useMutation({
    mutationFn: (published: boolean) => publicationApi.set(workspace.id, published),
    onSuccess: (result) => {
      queryClient.setQueryData(['workspaces', workspace.id, 'publication'], result)
      // The public directory and any cached public reads are now stale.
      queryClient.invalidateQueries({ queryKey: ['handbook'] })
    },
  })

  const canPublish = roleAtLeast(workspace.callerRole, 'ADMIN')

  if (publication.error) {
    return <ErrorState error={publication.error} onRetry={publication.refetch} />
  }

  const state = publication.data
  if (!state) {
    return null
  }

  const publish = async (published: boolean) => {
    try {
      await setPublished.mutateAsync(published)
      notify(published ? 'Documentation published' : 'Documentation unpublished')
      setConfirmOpen(false)
    } catch (error) {
      notifyError(describeError(error, 'Could not change publication.'))
    }
  }

  return (
    <section className={state.published ? 'publication publication--live' : 'publication'}>
      <header className="publication__head">
        <div>
          <div className="row">
            <h2 className="publication__title">Public documentation</h2>
            <Badge tone={state.published ? 'success' : 'neutral'}>
              {state.published ? 'Published' : 'Private'}
            </Badge>
          </div>
          <p className="publication__detail">
            {state.published
              ? `Anyone with the link can read ${state.publicPages} ${state.publicPages === 1 ? 'page' : 'pages'} of this workspace. Published ${formatDate(state.publishedAt ?? '')}.`
              : 'Only members can read this workspace. Publishing makes its documentation readable by anyone with the link — no account needed.'}
          </p>
        </div>
      </header>

      {state.published && state.publicPath ? (
        <div className="publication__link">
          <span className="mono-label">Public address</span>
          <div className="row">
            <Link className="publication__url" to={state.publicPath}>
              {state.publicPath}
            </Link>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                void navigator.clipboard?.writeText(
                  `${window.location.origin}${state.publicPath}`,
                )
                notify('Link copied')
              }}
            >
              Copy link
            </Button>
          </div>
        </div>
      ) : null}

      {state.published && state.publicPath ? (
        <p className="publication__note">
          The first segment is the owner's handle, so another team can publish a workspace with
          the same name without clashing.
        </p>
      ) : null}

      <dl className="publication__counts">
        <div>
          <dt className="mono-label">Public pages</dt>
          <dd className="publication__figure">{state.publicPages}</dd>
        </div>
        <div>
          <dt className="mono-label">Held back</dt>
          <dd className="publication__figure">{state.internalPages}</dd>
        </div>
      </dl>

      {state.internalPages > 0 ? (
        <p className="publication__note">
          {state.internalPages} {state.internalPages === 1 ? 'page is' : 'pages are'} marked
          internal and stay private either way. Mark a page internal from its editor.
        </p>
      ) : null}

      {canPublish ? (
        <div className="publication__actions">
          {state.published ? (
            <Button
              variant="secondary"
              onClick={() => publish(false)}
              loading={setPublished.isPending}
            >
              Make private
            </Button>
          ) : (
            <Button onClick={() => setConfirmOpen(true)} disabled={state.publicPages === 0}>
              Publish documentation
            </Button>
          )}
        </div>
      ) : (
        <p className="publication__note">Only admins and owners can change this.</p>
      )}

      {state.publicPages === 0 && !state.published ? (
        <p className="publication__note">
          Write at least one page that is not marked internal before publishing.
        </p>
      ) : null}

      <Modal
        title="Publish this documentation?"
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmOpen(false)}>
              Cancel
            </Button>
            <Button onClick={() => publish(true)} loading={setPublished.isPending}>
              Publish {state.publicPages} {state.publicPages === 1 ? 'page' : 'pages'}
            </Button>
          </>
        }
      >
        <p>
          <strong>
            {state.publicPages} {state.publicPages === 1 ? 'page' : 'pages'}
          </strong>{' '}
          become readable by anyone with the link, without an account. Boards, tasks, and your team
          list stay private.
        </p>
        <p>
          Pages you add later are public too, unless you mark them internal. You can make the
          workspace private again at any time.
        </p>
      </Modal>
    </section>
  )
}
