import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import type { DocumentRevision, Page } from '../../shared/types'
import { DocumentHistoryDialog } from './DocumentHistoryDialog'

function revision(overrides: Partial<DocumentRevision> = {}): DocumentRevision {
  return {
    revision: 1,
    title: 'Event ingestion',
    slug: 'event-ingestion',
    content: null,
    documentType: 'ARCHITECTURE',
    internal: false,
    reason: 'CREATED',
    restoredFrom: null,
    authorId: 'u1',
    authorLabel: 'Ada Lovelace <ada@example.com>',
    createdAt: '2026-07-20T10:00:00Z',
    ...overrides,
  }
}

function page(content: DocumentRevision[]): Page<DocumentRevision> {
  return {
    content,
    page: 0,
    size: 50,
    totalElements: content.length,
    totalPages: 1,
    last: true,
  }
}

function render(canWrite = true, currentContent = 'line one\nline two') {
  return renderWithProviders(
    <DocumentHistoryDialog
      workspaceId="w1"
      documentId="d1"
      currentContent={currentContent}
      canWrite={canWrite}
      open
      onClose={() => {}}
    />,
    { withAuth: true },
  )
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('DocumentHistoryDialog', () => {
  it('lists revisions newest first with who wrote each', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockResolvedValue(
      page([
        revision({ revision: 2, reason: 'UPDATED', title: 'Renamed' }),
        revision({ revision: 1 }),
      ]),
    )

    render()

    expect(await screen.findByText('r2')).toBeInTheDocument()
    expect(screen.getByText('r1')).toBeInTheDocument()
    expect(screen.getAllByText(/Ada Lovelace/).length).toBeGreaterThan(0)
    // The newest is marked, so nobody restores what is already live.
    expect(screen.getByText('Current')).toBeInTheDocument()
  })

  /** Backfilled revisions have no author; inventing one would be worse. */
  it('says so when the author was never recorded', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockResolvedValue(
      page([revision({ authorLabel: null, authorId: null })]),
    )

    render()

    expect(await screen.findByText('author not recorded')).toBeInTheDocument()
  })

  it('diffs the chosen revision against the live document', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockResolvedValue(page([revision()]))
    vi.spyOn(endpoints.revisionApi, 'get').mockResolvedValue(
      revision({ content: 'line one\nold line' }),
    )

    render(true, 'line one\nline two')
    await userEvent.click(await screen.findByText('r1'))

    const diff = await screen.findByRole('group', { name: /Changes since revision 1/ })
    expect(within(diff).getByText('old line')).toBeInTheDocument()
    expect(within(diff).getByText('line two')).toBeInTheDocument()
    // One line each way, and the counts say so.
    expect(screen.getByText('+1')).toBeInTheDocument()
    expect(screen.getByText('−1')).toBeInTheDocument()
  })

  it('states plainly when a revision matches the current document', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockResolvedValue(page([revision()]))
    vi.spyOn(endpoints.revisionApi, 'get').mockResolvedValue(
      revision({ content: 'identical' }),
    )

    render(true, 'identical')
    await userEvent.click(await screen.findByText('r1'))

    expect(await screen.findByText('Identical to the current version.')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: /Changes since/ })).not.toBeInTheDocument()
  })

  it('restores the chosen revision', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockResolvedValue(
      page([revision({ revision: 2, reason: 'UPDATED' }), revision({ revision: 1 })]),
    )
    vi.spyOn(endpoints.revisionApi, 'get').mockResolvedValue(
      revision({ revision: 1, content: 'the original' }),
    )
    const restore = vi.spyOn(endpoints.revisionApi, 'restore').mockResolvedValue({
      id: 'd1',
      workspaceId: 'w1',
      title: 'Event ingestion',
      slug: 'event-ingestion',
      content: 'the original',
      documentType: 'ARCHITECTURE',
      internal: false,
      createdAt: '2026-07-20T10:00:00Z',
      updatedAt: '2026-07-27T10:00:00Z',
    })

    render()
    await userEvent.click(await screen.findByText('r1'))
    await userEvent.click(await screen.findByRole('button', { name: 'Restore this revision' }))

    await waitFor(() => expect(restore).toHaveBeenCalledWith('w1', 'd1', 1))
  })

  /** Restoring what is already live would be a no-op dressed as an action. */
  it('does not offer to restore the current revision', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockResolvedValue(page([revision({ revision: 3 })]))
    vi.spyOn(endpoints.revisionApi, 'get').mockResolvedValue(
      revision({ revision: 3, content: 'line one\nline two' }),
    )

    render()
    await userEvent.click(await screen.findByText('r3'))

    await screen.findByText(/Identical to the current version/)
    expect(screen.queryByRole('button', { name: 'Restore this revision' })).not.toBeInTheDocument()
  })

  /** A viewer may read history; changing the document needs MEMBER. */
  it('hides restore from someone who cannot write', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockResolvedValue(
      page([revision({ revision: 2 }), revision({ revision: 1 })]),
    )
    vi.spyOn(endpoints.revisionApi, 'get').mockResolvedValue(
      revision({ revision: 1, content: 'older' }),
    )

    render(false)
    await userEvent.click(await screen.findByText('r1'))

    await screen.findByRole('group', { name: /Changes since revision 1/ })
    expect(screen.queryByRole('button', { name: 'Restore this revision' })).not.toBeInTheDocument()
  })

  it('shows where a restored revision came from', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockResolvedValue(
      page([revision({ revision: 3, reason: 'RESTORED', restoredFrom: 1 })]),
    )

    render()

    expect(await screen.findByText(/Restored from r1/)).toBeInTheDocument()
  })

  it('reports a failure to load rather than an empty panel', async () => {
    vi.spyOn(endpoints.revisionApi, 'list').mockRejectedValue(new Error('nope'))

    render()

    expect(await screen.findByText('Could not load this')).toBeInTheDocument()
  })
})
