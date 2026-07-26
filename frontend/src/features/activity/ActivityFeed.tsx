import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { EmptyState } from '../../shared/components/EmptyState'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { formatRelative } from '../../shared/utils/slugify'
import {
  describeAction,
  describeActor,
  describeActorDetail,
  describeDetail,
  describeTarget,
} from './describeEvent'
import type { AuditAction, AuditEvent, Page } from '../../shared/types'
import './ActivityFeed.css'

interface ActivityFeedProps {
  page: Page<AuditEvent> | undefined
  isPending: boolean
  error: unknown
  onRetry: () => void
  /** Current filter, or 'ALL'. */
  action: AuditAction | 'ALL'
  onActionChange: (action: AuditAction | 'ALL') => void
  pageIndex: number
  onPageChange: (page: number) => void
  /** Instance feeds span workspaces, so they say which one each event belongs to. */
  showWorkspace?: boolean
}

/**
 * A grouped filter, so the select is navigable. Every action the backend can
 * record appears here; anything missing would be invisible to the filter while
 * still showing in the unfiltered feed.
 */
const FILTER_GROUPS: Array<{ label: string; actions: AuditAction[] }> = [
  {
    label: 'Documents',
    actions: [
      'DOCUMENT_CREATED', 'DOCUMENT_UPDATED', 'DOCUMENT_DELETED',
      'DOCUMENT_RESTORED', 'DOCUMENT_LINKED', 'DOCUMENT_UNLINKED',
    ],
  },
  {
    label: 'Workspace',
    actions: [
      'WORKSPACE_CREATED', 'WORKSPACE_UPDATED', 'WORKSPACE_DELETED',
      'WORKSPACE_PUBLISHED', 'WORKSPACE_UNPUBLISHED',
    ],
  },
  { label: 'People', actions: ['MEMBER_ADDED', 'MEMBER_ROLE_CHANGED', 'MEMBER_REMOVED'] },
  {
    label: 'Boards',
    actions: [
      'BOARD_CREATED', 'BOARD_UPDATED', 'BOARD_DELETED',
      'COLUMN_CREATED', 'COLUMN_UPDATED', 'COLUMN_DELETED',
      'TASK_CREATED', 'TASK_UPDATED', 'TASK_MOVED', 'TASK_DELETED',
      'TASK_DOCUMENT_LINKED', 'TASK_DOCUMENT_UNLINKED',
    ],
  },
  {
    label: 'Instance',
    actions: [
      'INSTANCE_SET_UP', 'INSTANCE_SETTINGS_CHANGED',
      'INSTANCE_ADMIN_GRANTED', 'INSTANCE_ADMIN_REVOKED', 'ACCOUNT_CREATED',
    ],
  },
]

/** Actions worth marking, because they change who can see or do something. */
const CONSEQUENTIAL: ReadonlySet<AuditAction> = new Set<AuditAction>([
  'WORKSPACE_PUBLISHED',
  'WORKSPACE_UNPUBLISHED',
  'WORKSPACE_DELETED',
  'MEMBER_ROLE_CHANGED',
  'INSTANCE_ADMIN_GRANTED',
  'INSTANCE_ADMIN_REVOKED',
  'INSTANCE_SETTINGS_CHANGED',
])

export function ActivityFeed({
  page,
  isPending,
  error,
  onRetry,
  action,
  onActionChange,
  pageIndex,
  onPageChange,
  showWorkspace = false,
}: ActivityFeedProps) {
  const events = page?.content ?? []
  const totalPages = page?.totalPages ?? 0

  return (
    <div className="activity">
      <div className="activity__controls">
        <label className="activity__filter">
          <span className="mono-label">Show</span>
          <select
            className="field__control field__control--select"
            value={action}
            onChange={(event) => {
              onActionChange(event.target.value as AuditAction | 'ALL')
              // A filter change makes the current offset meaningless.
              onPageChange(0)
            }}
          >
            <option value="ALL">Everything</option>
            {FILTER_GROUPS.map((group) => (
              <optgroup key={group.label} label={group.label}>
                {group.actions.map((value) => (
                  <option key={value} value={value}>
                    {describeAction(value)}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </label>
      </div>

      {isPending ? <LoadingState label="Loading activity" /> : null}
      {error ? <ErrorState title="Could not load the activity log" error={error} onRetry={onRetry} /> : null}

      {page && events.length === 0 ? (
        <EmptyState
          title={action === 'ALL' ? 'Nothing recorded yet' : 'Nothing of that kind yet'}
          description={
            action === 'ALL'
              ? 'Changes appear here as they happen, with who made them.'
              : 'Try a different filter, or show everything.'
          }
          action={
            action === 'ALL' ? undefined : (
              <Button variant="secondary" onClick={() => { onActionChange('ALL'); onPageChange(0) }}>
                Show everything
              </Button>
            )
          }
        />
      ) : null}

      {events.length > 0 ? (
        <ol className="feed" aria-label="Activity">
          {events.map((event) => {
            const target = describeTarget(event)
            const detail = describeDetail(event)
            return (
              <li className="event" key={event.id}>
                <div className="event__line">
                  <span className="event__actor" title={describeActorDetail(event)}>
                    {describeActor(event)}
                  </span>{' '}
                  <span className="event__verb">{describeAction(event.action)}</span>
                  {target ? (
                    <>
                      {' '}
                      <span className="event__target">{target}</span>
                    </>
                  ) : null}
                  {CONSEQUENTIAL.has(event.action) ? (
                    <>
                      {' '}
                      <Badge tone="signal">notable</Badge>
                    </>
                  ) : null}
                </div>

                {detail.length > 0 ? (
                  <ul className="event__detail">
                    {detail.map((line) => (
                      <li key={line}>{line}</li>
                    ))}
                  </ul>
                ) : null}

                <p className="event__when">
                  <time dateTime={event.occurredAt} title={new Date(event.occurredAt).toLocaleString()}>
                    {formatRelative(event.occurredAt)}
                  </time>
                  {showWorkspace && event.workspaceId ? (
                    <span className="event__scope"> · workspace {event.workspaceId.slice(0, 8)}</span>
                  ) : null}
                </p>
              </li>
            )
          })}
        </ol>
      ) : null}

      {totalPages > 1 ? (
        <div className="activity__pager">
          <Button
            variant="secondary"
            size="sm"
            disabled={pageIndex === 0}
            onClick={() => onPageChange(pageIndex - 1)}
          >
            Newer
          </Button>
          <span className="activity__count">
            Page {pageIndex + 1} of {totalPages}
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={pageIndex >= totalPages - 1}
            onClick={() => onPageChange(pageIndex + 1)}
          >
            Older
          </Button>
        </div>
      ) : null}
    </div>
  )
}
