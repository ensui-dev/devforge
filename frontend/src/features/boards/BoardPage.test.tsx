import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Routes, Route } from 'react-router-dom'
import { renderWithProviders } from '../../test/renderWithProviders'
import * as endpoints from '../../shared/api/endpoints'
import type { Board, Workspace, WorkspaceRole } from '../../shared/types'
import { WorkspaceContext } from '../workspaces/WorkspaceContext'
import { BoardPage } from './BoardPage'

function workspace(callerRole: WorkspaceRole): Workspace {
  return {
    id: 'workspace-1',
    name: 'Platform',
    description: null,
    slug: 'platform',
    callerRole,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

function board(): Board {
  return {
    id: 'board-1',
    workspaceId: 'workspace-1',
    name: 'Delivery',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    columns: [
      { id: 'backlog', name: 'Backlog', position: 0, wipLimit: null, tasks: [] },
      { id: 'doing', name: 'In Progress', position: 1, wipLimit: 2, tasks: [] },
    ],
  }
}

function renderBoard(callerRole: WorkspaceRole = 'MEMBER') {
  return renderWithProviders(
    <WorkspaceContext.Provider value={workspace(callerRole)}>
      <Routes>
        <Route path="/workspaces/:workspaceId/boards/:boardId" element={<BoardPage />} />
      </Routes>
    </WorkspaceContext.Provider>,
    { route: '/workspaces/workspace-1/boards/board-1' },
  )
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('BoardPage', () => {
  it('renders the board with its columns', async () => {
    vi.spyOn(endpoints.boardApi, 'get').mockResolvedValue(board())

    renderBoard()

    expect(await screen.findByRole('heading', { name: 'Delivery' })).toBeInTheDocument()
    expect(screen.getByText('Backlog')).toBeInTheDocument()
    expect(screen.getByText('In Progress')).toBeInTheDocument()
  })

  it('shows a column’s work-in-progress limit alongside its count', async () => {
    vi.spyOn(endpoints.boardApi, 'get').mockResolvedValue(board())

    renderBoard()
    await screen.findByRole('heading', { name: 'Delivery' })

    expect(screen.getByText('0/2')).toBeInTheDocument()
  })

  it('renames the board', async () => {
    vi.spyOn(endpoints.boardApi, 'get').mockResolvedValue(board())
    const rename = vi
      .spyOn(endpoints.boardApi, 'rename')
      .mockResolvedValue({ ...board(), name: 'Q3 Delivery' })

    renderBoard()
    await userEvent.click(await screen.findByRole('button', { name: 'Rename' }))

    const dialog = screen.getByRole('dialog', { name: 'Rename board' })
    const field = within(dialog).getByLabelText('Name')
    await userEvent.clear(field)
    await userEvent.type(field, 'Q3 Delivery')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save name' }))

    await waitFor(() => expect(rename).toHaveBeenCalledWith('workspace-1', 'board-1', 'Q3 Delivery'))
  })

  it('will not save a rename that changes nothing', async () => {
    vi.spyOn(endpoints.boardApi, 'get').mockResolvedValue(board())

    renderBoard()
    await userEvent.click(await screen.findByRole('button', { name: 'Rename' }))

    const dialog = screen.getByRole('dialog', { name: 'Rename board' })
    expect(within(dialog).getByRole('button', { name: 'Save name' })).toBeDisabled()
  })

  it('hides every write control from a viewer', async () => {
    vi.spyOn(endpoints.boardApi, 'get').mockResolvedValue(board())

    renderBoard('VIEWER')
    await screen.findByRole('heading', { name: 'Delivery' })

    expect(screen.queryByRole('button', { name: 'Rename' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add column' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'New task' })).not.toBeInTheDocument()
  })

  /** Deleting a board is an ADMIN action, matching the backend. */
  it('offers board deletion only to an admin', async () => {
    vi.spyOn(endpoints.boardApi, 'get').mockResolvedValue(board())

    renderBoard('MEMBER')
    await screen.findByRole('heading', { name: 'Delivery' })
    expect(screen.queryByRole('button', { name: 'Delete board' })).not.toBeInTheDocument()
  })

  it('reports a load failure with the backend’s message', async () => {
    const { ApiError } = await import('../../shared/api/client')
    vi.spyOn(endpoints.boardApi, 'get').mockRejectedValue(new ApiError('Board not found', 404))

    renderBoard()

    expect(await screen.findByRole('alert')).toHaveTextContent('Board not found')
  })
})
