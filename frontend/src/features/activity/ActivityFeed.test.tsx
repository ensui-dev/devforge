import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import type { AuditEvent, Page } from '../../shared/types'
import { ActivityFeed } from './ActivityFeed'

function event(overrides: Partial<AuditEvent> = {}): AuditEvent {
  return {
    id: Math.random().toString(36).slice(2),
    occurredAt: '2026-07-27T10:00:00Z',
    actorId: 'u1',
    actorLabel: 'Ada Lovelace <ada@example.com>',
    action: 'DOCUMENT_UPDATED',
    targetType: 'DOCUMENT',
    targetId: 'd1',
    targetLabel: 'Event ingestion',
    workspaceId: 'w1',
    detail: {},
    ...overrides,
  }
}

function page(content: AuditEvent[], totalPages = 1, index = 0): Page<AuditEvent> {
  return {
    content,
    page: index,
    size: 25,
    totalElements: content.length,
    totalPages,
    last: index >= totalPages - 1,
  }
}

type Overrides = Partial<Parameters<typeof ActivityFeed>[0]>

function render(overrides: Overrides = {}) {
  const props = {
    page: page([event()]),
    isPending: false,
    error: null,
    onRetry: () => {},
    action: 'ALL' as const,
    onActionChange: () => {},
    pageIndex: 0,
    onPageChange: () => {},
    ...overrides,
  }
  return renderWithProviders(<ActivityFeed {...props} />, { withAuth: true })
}

describe('ActivityFeed', () => {
  it('reads each entry as a sentence', () => {
    render({ page: page([event()]) })

    // Scoped to the feed: the filter's options use the same phrases, so an
    // unscoped query for "edited" matches the dropdown too.
    const feed = within(screen.getByRole('list', { name: 'Activity' }))
    expect(feed.getByText('Ada Lovelace')).toBeInTheDocument()
    expect(feed.getByText('edited')).toBeInTheDocument()
    expect(feed.getByText('Event ingestion')).toBeInTheDocument()
  })

  it('shows the specifics of what changed', () => {
    render({
      page: page([event({ detail: { title: { from: 'Design', to: 'Renamed' } } })]),
    })

    expect(screen.getByText('title: Design → Renamed')).toBeInTheDocument()
  })

  /** Publishing and role changes alter who can see or do things. */
  it('marks the consequential actions', () => {
    render({ page: page([event({ action: 'WORKSPACE_PUBLISHED' })]) })
    expect(screen.getByText('notable')).toBeInTheDocument()
  })

  it('does not mark an ordinary edit', () => {
    render({ page: page([event({ action: 'DOCUMENT_UPDATED' })]) })
    expect(screen.queryByText('notable')).not.toBeInTheDocument()
  })

  it('filters by action and resets to the first page', async () => {
    const onActionChange = vi.fn()
    const onPageChange = vi.fn()

    render({ pageIndex: 3, onActionChange, onPageChange })
    await userEvent.selectOptions(screen.getByLabelText('Show'), 'DOCUMENT_DELETED')

    expect(onActionChange).toHaveBeenCalledWith('DOCUMENT_DELETED')
    // A filter change makes the current offset meaningless.
    expect(onPageChange).toHaveBeenCalledWith(0)
  })

  it('invites a filter reset when a filter hides everything', async () => {
    const onActionChange = vi.fn()
    render({ page: page([]), action: 'TASK_MOVED', onActionChange })

    expect(screen.getByText('Nothing of that kind yet')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Show everything' }))
    expect(onActionChange).toHaveBeenCalledWith('ALL')
  })

  /** An unfiltered empty log is a new workspace, not a mistake. */
  it('explains an empty log without offering a pointless reset', () => {
    render({ page: page([]), action: 'ALL' })

    expect(screen.getByText('Nothing recorded yet')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show everything' })).not.toBeInTheDocument()
  })

  it('pages from newer to older', async () => {
    const onPageChange = vi.fn()
    render({ page: page([event()], 4, 1), pageIndex: 1, onPageChange })

    expect(screen.getByText('Page 2 of 4')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Older' }))
    expect(onPageChange).toHaveBeenCalledWith(2)

    await userEvent.click(screen.getByRole('button', { name: 'Newer' }))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })

  it('does not offer paging for a single page', () => {
    render({ page: page([event()], 1, 0) })
    expect(screen.queryByRole('button', { name: 'Older' })).not.toBeInTheDocument()
  })

  it('disables newer on the first page and older on the last', () => {
    const { unmount } = render({ page: page([event()], 3, 0), pageIndex: 0 })
    expect(screen.getByRole('button', { name: 'Newer' })).toBeDisabled()
    unmount()

    render({ page: page([event()], 3, 2), pageIndex: 2 })
    expect(screen.getByRole('button', { name: 'Older' })).toBeDisabled()
  })

  it('reports a failure instead of an empty feed', () => {
    render({ error: new Error('nope'), page: undefined })
    expect(screen.getByText('Could not load the activity log')).toBeInTheDocument()
  })

  it('shows a loading state while the first page is in flight', () => {
    render({ isPending: true, page: undefined })
    expect(screen.getByText('Loading activity')).toBeInTheDocument()
  })
})
