import { describe, expect, it } from 'vitest'
import { applyMoveLocally } from './useBoards'
import type { Board, Task } from '../../shared/types'

/**
 * The optimistic move must reproduce the backend's ordering rules exactly,
 * otherwise a drag would show one order and the refetch would snap to another.
 * These mirror the backend's TaskOrderingTest cases.
 */
function task(id: string, columnId: string, position: number): Task {
  return {
    id,
    boardId: 'board-1',
    columnId,
    title: id,
    description: null,
    position,
    priority: 'MEDIUM',
    assignee: null,
    linkedDocuments: [],
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
      {
        id: 'backlog',
        name: 'Backlog',
        position: 0,
        wipLimit: null,
        tasks: [task('A', 'backlog', 0), task('B', 'backlog', 1), task('C', 'backlog', 2)],
      },
      {
        id: 'doing',
        name: 'In Progress',
        position: 1,
        wipLimit: null,
        tasks: [task('D', 'doing', 0)],
      },
    ],
  }
}

const titlesIn = (result: Board, columnId: string) =>
  result.columns.find((column) => column.id === columnId)!.tasks.map((item) => item.id)

describe('applyMoveLocally', () => {
  it('reorders within a column', () => {
    const result = applyMoveLocally(board(), 'C', { columnId: 'backlog', position: 0 })

    expect(titlesIn(result, 'backlog')).toEqual(['C', 'A', 'B'])
  })

  it('renumbers positions contiguously after a reorder', () => {
    const result = applyMoveLocally(board(), 'C', { columnId: 'backlog', position: 0 })

    expect(result.columns[0].tasks.map((item) => item.position)).toEqual([0, 1, 2])
  })

  it('moves across columns and closes the source gap', () => {
    const result = applyMoveLocally(board(), 'B', { columnId: 'doing', position: 0 })

    expect(titlesIn(result, 'backlog')).toEqual(['A', 'C'])
    expect(titlesIn(result, 'doing')).toEqual(['B', 'D'])
    expect(result.columns[0].tasks.map((item) => item.position)).toEqual([0, 1])
    expect(result.columns[1].tasks.map((item) => item.position)).toEqual([0, 1])
  })

  it('updates the moved task’s column id', () => {
    const result = applyMoveLocally(board(), 'A', { columnId: 'doing', position: 1 })

    const moved = result.columns[1].tasks.find((item) => item.id === 'A')
    expect(moved?.columnId).toBe('doing')
    expect(moved?.position).toBe(1)
  })

  it('clamps a position past the end of the target column', () => {
    const result = applyMoveLocally(board(), 'A', { columnId: 'doing', position: 99 })

    expect(titlesIn(result, 'doing')).toEqual(['D', 'A'])
  })

  it('leaves the board untouched for an unknown task', () => {
    const original = board()

    const result = applyMoveLocally(original, 'missing', { columnId: 'doing', position: 0 })

    expect(result).toBe(original)
  })

  it('leaves the board untouched for an unknown column', () => {
    const original = board()

    const result = applyMoveLocally(original, 'A', { columnId: 'nope', position: 0 })

    expect(result).toBe(original)
  })

  it('does not mutate the board it was given', () => {
    const original = board()

    applyMoveLocally(original, 'C', { columnId: 'doing', position: 0 })

    expect(original.columns[0].tasks.map((item) => item.id)).toEqual(['A', 'B', 'C'])
    expect(original.columns[1].tasks).toHaveLength(1)
  })
})
