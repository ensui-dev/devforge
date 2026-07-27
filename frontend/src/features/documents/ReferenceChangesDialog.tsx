import { useQuery } from '@tanstack/react-query'
import { documentApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { Button } from '../../shared/components/Button'
import { DiffView } from '../../shared/components/DiffView'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { Modal } from '../../shared/components/Modal'
import { formatRelative } from '../../shared/utils/slugify'
import type { DocumentReference } from '../../shared/types'
import './ReferenceChangesDialog.css'

interface ReferenceChangesDialogProps {
  workspaceId: string
  documentId: string
  reference: DocumentReference
  onClose: () => void
}

/**
 * What a linked page changed since this one last kept up with it.
 *
 * Deliberately not that page's whole history — that is a different question with
 * its own screen. This answers only "what arrived after I last looked", which is
 * what somebody deciding whether their own page needs rewriting actually needs.
 */
export function ReferenceChangesDialog({
  workspaceId,
  documentId,
  reference,
  onClose,
}: ReferenceChangesDialogProps) {
  const changes = useQuery({
    queryKey: queryKeys.documents.referenceChanges(workspaceId, documentId, reference.id),
    queryFn: () => documentApi.referenceChanges(workspaceId, documentId, reference.id),
  })

  const title = reference.relatedDocumentTitle ?? 'the linked document'

  return (
    <Modal
      title={reference.outgoing ? `What changed in ${title}` : `What ${title} has not caught up with`}
      open
      onClose={onClose}
      width="lg"
      footer={<Button onClick={onClose}>Close</Button>}
    >
      {changes.isPending ? <LoadingState label="Loading the changes" /> : null}
      {changes.error ? (
        <ErrorState
          title="Could not load the changes"
          error={changes.error}
          onRetry={changes.refetch}
        />
      ) : null}

      {changes.data ? (
        <div className="ref-changes">
          <p className="ref-changes__lead">
            {reference.outgoing ? (
              <>
                <strong>{changes.data.relatedDocumentTitle}</strong> changed{' '}
                {formatRelative(changes.data.afterChangedAt)}, after this page was last written{' '}
                {formatRelative(changes.data.since)}.
              </>
            ) : (
              <>
                This page changed after <strong>{changes.data.relatedDocumentTitle}</strong> was
                last written {formatRelative(changes.data.since)}. Below is what that page has
                changed in the meantime, which may be nothing.
              </>
            )}
          </p>

          {changes.data.before === null ? (
            <p className="ref-changes__note">
              That page did not exist yet, so there is nothing to compare against — everything in
              it is new.
            </p>
          ) : (
            <DiffView
              before={changes.data.before}
              after={changes.data.after}
              label={`Changes in ${changes.data.relatedDocumentTitle}`}
              unchangedNote="Its wording has not changed — only its title, type, or visibility did."
            />
          )}

          <p className="ref-changes__revisions">
            {changes.data.before === null
              ? `Now at revision ${changes.data.afterRevision}.`
              : `Revision ${changes.data.beforeRevision} → ${changes.data.afterRevision}.`}
          </p>
        </div>
      ) : null}
    </Modal>
  )
}
