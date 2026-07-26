import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Routes, Route } from 'react-router-dom'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import type { DocumentSummary, Page, Workspace } from '../../shared/types'
import { WorkspaceContext } from '../workspaces/WorkspaceContext'
import { DocumentListPage } from './DocumentListPage'

const workspace: Workspace = {
  id: 'workspace-1',
  name: 'Platform',
  description: null,
  slug: 'platform',
  callerRole: 'MEMBER',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function document(index: number): DocumentSummary {
  return {
    id: `doc-${index}`,
    workspaceId: workspace.id,
    title: `Doc ${String(index).padStart(2, '0')}`,
    slug: `doc-${index}`,
    excerpt: `body ${index}`,
    documentType: 'GENERAL',
    internal: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

/** Mirrors the backend: 30 documents at 25 per page. */
function pageOf(pageNumber: number): Page<DocumentSummary> {
  const all = Array.from({ length: 30 }, (_, index) => document(index))
  const content = all.slice(pageNumber * 25, pageNumber * 25 + 25)
  return {
    content,
    page: pageNumber,
    size: 25,
    totalElements: 30,
    totalPages: 2,
    last: pageNumber >= 1,
  }
}

function renderList() {
  return renderWithProviders(
    <WorkspaceContext.Provider value={workspace}>
      <Routes>
        <Route path="/workspaces/:workspaceId/documents" element={<DocumentListPage />} />
      </Routes>
    </WorkspaceContext.Provider>,
    { route: '/workspaces/workspace-1/documents' },
  )
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('DocumentListPage paging', () => {
  it('shows the first page and its pager', async () => {
    vi.spyOn(endpoints.documentApi, 'list').mockImplementation(
      async (_ws, options) => pageOf(options?.page ?? 0),
    )

    renderList()

    expect(await screen.findByRole('heading', { name: 'Doc 00' })).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled()
  })

  /** The reported bug: the second page rendered the empty state. */
  it('shows the second page of documents after pressing Next', async () => {
    vi.spyOn(endpoints.documentApi, 'list').mockImplementation(
      async (_ws, options) => pageOf(options?.page ?? 0),
    )

    renderList()
    await screen.findByRole('heading', { name: 'Doc 00' })

    await userEvent.click(screen.getByRole('button', { name: 'Next' }))

    expect(await screen.findByRole('heading', { name: 'Doc 25' })).toBeInTheDocument()
    expect(screen.queryByText('No documents yet')).not.toBeInTheDocument()
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()
  })

  it('requests the page the user asked for', async () => {
    const list = vi
      .spyOn(endpoints.documentApi, 'list')
      .mockImplementation(async (_ws, options) => pageOf(options?.page ?? 0))

    renderList()
    await screen.findByRole('heading', { name: 'Doc 00' })
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))

    await waitFor(() =>
      expect(list).toHaveBeenCalledWith('workspace-1', expect.objectContaining({ page: 1 })),
    )
  })

  it('can go back to the first page', async () => {
    vi.spyOn(endpoints.documentApi, 'list').mockImplementation(
      async (_ws, options) => pageOf(options?.page ?? 0),
    )

    renderList()
    await screen.findByRole('heading', { name: 'Doc 00' })
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByRole('heading', { name: 'Doc 25' })

    await userEvent.click(screen.getByRole('button', { name: 'Previous' }))

    expect(await screen.findByRole('heading', { name: 'Doc 00' })).toBeInTheDocument()
  })

  it('disables Next on the final page', async () => {
    vi.spyOn(endpoints.documentApi, 'list').mockImplementation(
      async (_ws, options) => pageOf(options?.page ?? 0),
    )

    renderList()
    await screen.findByRole('heading', { name: 'Doc 00' })
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByRole('heading', { name: 'Doc 25' })

    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  /**
   * Returning to page 0 matters: otherwise a filter applied while on page 2 asks
   * for page 2 of a much shorter list and lands on nothing.
   */
  it('returns to the first page when a type filter is applied', async () => {
    const list = vi
      .spyOn(endpoints.documentApi, 'list')
      .mockImplementation(async (_ws, options) => pageOf(options?.page ?? 0))

    renderList()
    await screen.findByRole('heading', { name: 'Doc 00' })
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByRole('heading', { name: 'Doc 25' })

    await userEvent.click(screen.getByRole('button', { name: 'Runbook' }))

    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(
        'workspace-1',
        expect.objectContaining({ page: 0, documentType: 'RUNBOOK' }),
      ),
    )
  })

  it('shows the empty state only when the workspace really has no documents', async () => {
    vi.spyOn(endpoints.documentApi, 'list').mockResolvedValue({
      content: [],
      page: 0,
      size: 25,
      totalElements: 0,
      totalPages: 0,
      last: true,
    })

    renderList()

    expect(await screen.findByText('No documents yet')).toBeInTheDocument()
  })

  it('always requests an explicit page size', async () => {
    const list = vi
      .spyOn(endpoints.documentApi, 'list')
      .mockImplementation(async (_ws, options) => pageOf(options?.page ?? 0))

    renderList()
    await screen.findByRole('heading', { name: 'Doc 00' })

    // The size in the request has to match the one in the cache key, or another
    // screen's differently sized page can be read as this screen's.
    expect(list).toHaveBeenCalledWith('workspace-1', expect.objectContaining({ size: 25 }))
  })

  /**
   * The reported failure. Another screen had already cached this workspace at a
   * different page size, which made the pager believe in pages that did not
   * exist; Next then asked for a range past the end and rendered "no documents".
   */
  it('is unaffected by another screen caching the same workspace at a different size', async () => {
    const list = vi.spyOn(endpoints.documentApi, 'list').mockImplementation(
      async (_ws, options) => {
        const size = options?.size ?? 25
        const all = Array.from({ length: 21 }, (_, index) => document(index))
        const requested = options?.page ?? 0
        const content = all.slice(requested * size, requested * size + size)
        return {
          content,
          page: requested,
          size,
          totalElements: all.length,
          totalPages: Math.ceil(all.length / size),
          last: (requested + 1) * size >= all.length,
        }
      },
    )

    const { queryClient } = renderList()

    // Exactly what visiting the overview first used to do.
    await queryClient.prefetchQuery({
      queryKey: queryKeys.documents.list('workspace-1', 'ALL', 0, 5),
      queryFn: () => endpoints.documentApi.list('workspace-1', { size: 5 }),
    })

    // 21 documents at 25 per page is a single page, so no pager should appear.
    expect(await screen.findByRole('heading', { name: 'Doc 00' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Doc 20' })).toBeInTheDocument()
    expect(screen.queryByText(/Page 1 of 5/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument()
    expect(screen.queryByText('No documents yet')).not.toBeInTheDocument()

    // The two sizes were fetched separately rather than sharing one entry.
    expect(list).toHaveBeenCalledWith('workspace-1', expect.objectContaining({ size: 25 }))
    expect(list).toHaveBeenCalledWith('workspace-1', expect.objectContaining({ size: 5 }))
  })

  it('recovers from a page that no longer exists instead of stranding the reader', async () => {
    // One page of results, but the component starts by asking for the second.
    vi.spyOn(endpoints.documentApi, 'list').mockImplementation(async (_ws, options) => {
      const requested = options?.page ?? 0
      return {
        content: requested === 0 ? [document(0)] : [],
        page: requested,
        size: 25,
        totalElements: 1,
        totalPages: 1,
        last: true,
      }
    })

    const { queryClient } = renderList()
    await screen.findByRole('heading', { name: 'Doc 00' })
    queryClient.clear()

    // Whatever page it lands on, it must show results rather than a false empty.
    expect(await screen.findByRole('heading', { name: 'Doc 00' })).toBeInTheDocument()
    expect(screen.queryByText('No documents yet')).not.toBeInTheDocument()
  })

  describe('filtering by a type with no matches', () => {
    /**
     * The misleading message: a filter matching nothing is not an empty
     * workspace, and the handbook really does have types with no documents.
     */
    it('says the filter is empty, not the workspace', async () => {
      vi.spyOn(endpoints.documentApi, 'list').mockImplementation(async (_ws, options) => {
        const empty = options?.documentType === 'CODE'
        return {
          content: empty ? [] : [document(0)],
          page: 0,
          size: 25,
          totalElements: empty ? 0 : 30,
          totalPages: empty ? 0 : 2,
          last: true,
        }
      })

      renderList()
      await screen.findByRole('heading', { name: 'Doc 00' })

      await userEvent.click(screen.getByRole('button', { name: 'Code' }))

      expect(await screen.findByText('No code documents')).toBeInTheDocument()
      expect(screen.queryByText('No documents yet')).not.toBeInTheDocument()
    })

    it('offers a way back to the full list', async () => {
      const list = vi.spyOn(endpoints.documentApi, 'list').mockImplementation(
        async (_ws, options) => {
          const empty = options?.documentType === 'CODE'
          return {
            content: empty ? [] : [document(0)],
            page: 0,
            size: 25,
            totalElements: empty ? 0 : 30,
            totalPages: empty ? 0 : 2,
            last: true,
          }
        },
      )

      renderList()
      await screen.findByRole('heading', { name: 'Doc 00' })
      await userEvent.click(screen.getByRole('button', { name: 'Code' }))
      await screen.findByText('No code documents')

      await userEvent.click(screen.getByRole('button', { name: 'Show all documents' }))

      await waitFor(() =>
        expect(list).toHaveBeenLastCalledWith(
          'workspace-1',
          expect.objectContaining({ documentType: undefined }),
        ),
      )
      expect(await screen.findByRole('heading', { name: 'Doc 00' })).toBeInTheDocument()
    })
  })
})
