import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { activityApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { ActivityFeed } from './ActivityFeed'
import type { AuditAction } from '../../shared/types'

const SIZE = 25

/**
 * Everything that has happened on this instance.
 *
 * Instance administrators only — it spans workspaces the reader may not be a
 * member of, and includes events belonging to no workspace at all, such as first-run
 * setup and administration grants. The server refuses anyone else.
 */
export function InstanceActivityPage() {
  const [action, setAction] = useState<AuditAction | 'ALL'>('ALL')
  const [page, setPage] = useState(0)

  const activity = useQuery({
    queryKey: queryKeys.activity.instance(action, page, SIZE),
    queryFn: () =>
      activityApi.forInstance({
        action: action === 'ALL' ? undefined : action,
        page,
        size: SIZE,
      }),
  })

  return (
    <div className="instance">
      <div className="page-header">
        <div>
          <p className="mono-label">Instance</p>
          <h1 className="page-header__title">Activity across this deployment</h1>
          <p className="page-header__subtitle">
            Every recorded change, including those belonging to no workspace — setup,
            account creation, and administration grants. A workspace's own entries
            survive its deletion, so this is where they remain visible.
          </p>
        </div>
        <div className="page-header__actions">
          <Link className="instance__back" to="/instance">
            Instance settings
          </Link>
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
        showWorkspace
      />
    </div>
  )
}
