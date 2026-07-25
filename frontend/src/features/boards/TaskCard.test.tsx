import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Task } from '../../shared/types'
import { TaskCard } from './TaskCard'

function task(overrides: Partial<Task> = {}): Task {
  return {
    id: 'task-1',
    boardId: 'board-1',
    columnId: 'backlog',
    title: 'Partition the orders topic',
    description: null,
    position: 0,
    priority: 'MEDIUM',
    assignee: null,
    linkedDocuments: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function renderCard(overrides: Partial<Task> = {}, draggable = true) {
  const onOpen = vi.fn()
  render(
    <TaskCard
      task={task(overrides)}
      draggable={draggable}
      dragging={false}
      onOpen={onOpen}
      onDragStart={vi.fn()}
      onDragEnd={vi.fn()}
    />,
  )
  return { onOpen }
}

describe('TaskCard', () => {
  it('shows the title as the actionable control', async () => {
    const { onOpen } = renderCard()

    await userEvent.click(screen.getByRole('button', { name: 'Partition the orders topic' }))

    expect(onOpen).toHaveBeenCalledTimes(1)
  })

  /** MEDIUM is the default, so badging it would add noise to every card. */
  it('hides the badge for medium priority', () => {
    renderCard({ priority: 'MEDIUM' })

    expect(screen.queryByText('MEDIUM')).not.toBeInTheDocument()
  })

  it('badges priorities that need attention', () => {
    renderCard({ priority: 'CRITICAL' })

    expect(screen.getByText('CRITICAL')).toBeInTheDocument()
  })

  it('counts linked documents', () => {
    renderCard({
      linkedDocuments: [
        { id: 'doc-1', title: 'Spec', slug: 'spec', documentType: 'API' },
        { id: 'doc-2', title: 'Runbook', slug: 'runbook', documentType: 'RUNBOOK' },
      ],
    })

    expect(screen.getByText('2 docs')).toBeInTheDocument()
  })

  it('uses the singular for a single document', () => {
    renderCard({
      linkedDocuments: [{ id: 'doc-1', title: 'Spec', slug: 'spec', documentType: 'API' }],
    })

    expect(screen.getByText('1 doc')).toBeInTheDocument()
  })

  it('names the first documents and summarises the rest', () => {
    renderCard({
      linkedDocuments: [
        { id: 'doc-1', title: 'First', slug: 'first', documentType: 'API' },
        { id: 'doc-2', title: 'Second', slug: 'second', documentType: 'API' },
        { id: 'doc-3', title: 'Third', slug: 'third', documentType: 'API' },
      ],
    })

    expect(screen.getByText('First')).toBeInTheDocument()
    expect(screen.getByText('Second')).toBeInTheDocument()
    expect(screen.queryByText('Third')).not.toBeInTheDocument()
    expect(screen.getByText('+1 more')).toBeInTheDocument()
  })

  it('shows the assignee with their initials', () => {
    renderCard({
      assignee: { id: 'user-1', displayName: 'Ada Lovelace', email: 'ada@example.com' },
    })

    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
    expect(screen.getByText('AL')).toBeInTheDocument()
  })

  it('is draggable only when the caller may reorder the board', () => {
    const { container } = render(
      <TaskCard
        task={task()}
        draggable={false}
        dragging={false}
        onOpen={vi.fn()}
        onDragStart={vi.fn()}
        onDragEnd={vi.fn()}
      />,
    )

    expect(container.querySelector('article')).toHaveAttribute('draggable', 'false')
  })
})
