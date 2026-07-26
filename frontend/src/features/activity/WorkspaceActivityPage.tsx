import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { activityApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { useCurrentWorkspace } from '../workspaces/WorkspaceContext'
import { ActivityFeed } from './ActivityFeed'
import type { AuditAction } from '../../shared/types'

const SIZE = 25

/**
 * What has changed in this workspace, and who changed it.
 *
 * Visible to every member including viewers: it reveals nothing they cannot
 * already read, and knowing who last touched a page is part of reading it
 * honestly.
 */
export function WorkspaceActivityPage() {
  const workspace = useCurrentWorkspace()
  const [action, setAction] = useState<AuditAction | 'ALL'>('ALL')
  const [page, setPage] = useState(0)

  const activity = useQuery({
    queryKey: queryKeys.activity.workspace(workspace.id, action, page, SIZE),
    queryFn: () =>
      activityApi.forWorkspace(workspace.id, {
        action: action === 'ALL' ? undefined : action,
        page,
        size: SIZE,
      }),
  })

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <p className="mono-label">Activity</p>
          <h1 className="page-header__title">What has changed</h1>
          <p className="page-header__subtitle">
            Every change to this workspace, newest first, with the account responsible.
            Entries are never edited or removed.
          </p>
        </div>
      </div>

      <ActivityFeed
        page={activity.data}
        isPending={activity.isPending}
        error={activity.error}
        onRetry={activity.refetch}
        action={action}
        onActionChange={setAction}
        pageIndex={page}
        onPageChange={setPage}
      />
    </div>
  )
}
