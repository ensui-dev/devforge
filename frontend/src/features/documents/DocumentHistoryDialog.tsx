import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { revisionApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { Modal } from '../../shared/components/Modal'
import { describeError } from '../../shared/components/describeError'
import { useToast } from '../../shared/components/useToast'
import { formatRelative } from '../../shared/utils/slugify'
import { diffLines } from '../../shared/utils/diffLines'
import type { DocumentRevision } from '../../shared/types'
import './DocumentHistoryDialog.css'

interface DocumentHistoryDialogProps {
  workspaceId: string
  documentId: string
  /** The live body, which every revision is compared against. */
  currentContent: string
  canWrite: boolean
  open: boolean
  onClose: () => void
}

/** Why a revision exists, said in words rather than an enum name. */
const REASON_LABEL: Record<DocumentRevision['reason'], string> = {
  CREATED: 'Created',
  UPDATED: 'Edited',
  RESTORED: 'Restored',
}

/**
 * A document's history, with a diff against the live version and a way back.
 *
 * <p>Restoring appends rather than rewinds, so nothing here is destructive and the
 * confirmation says so — the usual "this cannot be undone" warning would be a lie.
 */
export function DocumentHistoryDialog({
  workspaceId,
  documentId,
  currentContent,
  canWrite,
  open,
  onClose,
}: DocumentHistoryDialogProps) {
  const queryClient = useQueryClient()
  const { notify, notifyError } = useToast()
  const [selected, setSelected] = useState<number | null>(null)

  const history = useQuery({
    queryKey: queryKeys.documents.revisions(workspaceId, documentId, 0, 50),
    queryFn: () => revisionApi.list(workspaceId, documentId, { page: 0, size: 50 }),
    enabled: open,
  })

  // Bodies are omitted from the list, so the chosen revision is fetched in full.
  const revision = useQuery({
    queryKey: queryKeys.documents.revision(workspaceId, documentId, selected ?? -1),
    queryFn: () => revisionApi.get(workspaceId, documentId, selected as number),
    enabled: open && selected !== null,
  })

  const restore = useMutation({
    mutationFn: (target: number) => revisionApi.restore(workspaceId, documentId, target),
    onSuccess: async (_updated, target) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.documents.all(workspaceId) })
      notify(`Restored revision ${target}`)
      setSelected(null)
      onClose()
    },
    onError: (error) => notifyError(describeError(error, 'Could not restore that revision.')),
  })

  const diff = useMemo(() => {
    if (revision.data?.content == null) {
      return null
    }
    // Old on the left of the comparison: this is what changed *since* then.
    return diffLines(revision.data.content, currentContent)
  }, [revision.data?.content, currentContent])

  const revisions = history.data?.content ?? []
  const latest = revisions[0]?.revision

  return (
    <Modal title="History" open={open} onClose={onClose} width="lg">
      {history.isPending ? <LoadingState label="Loading history" /> : null}
      {history.error ? <ErrorState error={history.error} onRetry={history.refetch} /> : null}

      {revisions.length > 0 ? (
        <div className="history">
          <ol className="history__list">
            {revisions.map((entry) => (
              <li key={entry.revision}>
                <button
                  type="button"
                  className={
                    entry.revision === selected ? 'revision revision--selected' : 'revision'
                  }
                  aria-pressed={entry.revision === selected}
                  onClick={() =>
                    setSelected(entry.revision === selected ? null : entry.revision)
                  }
                >
                  <span className="revision__head">
                    <span className="revision__n">r{entry.revision}</span>
                    {entry.revision === latest ? <Badge tone="trace">Current</Badge> : null}
                  </span>
                  <span className="revision__title">{entry.title}</span>
                  <span className="revision__meta">
                    {REASON_LABEL[entry.reason]}
                    {entry.restoredFrom ? ` from r${entry.restoredFrom}` : ''}
                    {' · '}
                    {formatRelative(entry.createdAt)}
                  </span>
                  <span className="revision__author">
                    {/* Null for revisions backfilled when history was introduced. */}
                    {entry.authorLabel ?? 'author not recorded'}
                  </span>
                </button>
              </li>
            ))}
          </ol>

          <div className="history__detail">
            {selected === null ? (
              <p className="history__hint">
                Pick a revision to see what has changed since it, and to restore it.
              </p>
            ) : revision.isPending ? (
              <LoadingState label={`Loading revision ${selected}`} />
            ) : revision.error ? (
              <ErrorState error={revision.error} onRetry={revision.refetch} />
            ) : (
              <>
                <div className="history__detail-head">
                  <div>
                    <p className="mono-label">
                      Revision {selected} compared with now
                    </p>
                    <p className="history__stats">
                      {diff?.stats.unchanged ? (
                        'Identical to the current version.'
                      ) : (
                        <>
                          <span className="history__added">+{diff?.stats.added ?? 0}</span>{' '}
                          <span className="history__removed">−{diff?.stats.removed ?? 0}</span>{' '}
                          lines
                        </>
                      )}
                    </p>
                  </div>
                  {canWrite && selected !== latest ? (
                    <Button
                      size="sm"
                      variant="secondary"
                      loading={restore.isPending}
                      onClick={() => restore.mutate(selected)}
                    >
                      Restore this revision
                    </Button>
                  ) : null}
                </div>

                {diff && !diff.stats.unchanged ? (
                  <div className="diff" role="group" aria-label={`Changes since revision ${selected}`}>
                    {diff.lines.map((line, index) => (
                      <div className={`diff__line diff__line--${line.kind}`} key={index}>
                        <span className="diff__gutter" aria-hidden="true">
                          {line.before ?? ''}
                        </span>
                        <span className="diff__gutter" aria-hidden="true">
                          {line.after ?? ''}
                        </span>
                        <span className="diff__marker" aria-hidden="true">
                          {line.kind === 'added' ? '+' : line.kind === 'removed' ? '−' : ' '}
                        </span>
                        <span className="diff__text">{line.text || ' '}</span>
                      </div>
                    ))}
                  </div>
                ) : null}
              </>
            )}
          </div>
        </div>
      ) : null}
    </Modal>
  )
}
