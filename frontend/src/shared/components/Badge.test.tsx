import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Badge } from './Badge'
import { EmptyState } from './EmptyState'
import { Button } from './Button'

describe('Badge', () => {
  it('renders its content', () => {
    render(<Badge>ARCHITECTURE</Badge>)

    expect(screen.getByText('ARCHITECTURE')).toBeInTheDocument()
  })

  it('applies the requested tone', () => {
    render(<Badge tone="signal">HIGH</Badge>)

    expect(screen.getByText('HIGH')).toHaveClass('badge--signal')
  })

  it('defaults to the neutral tone', () => {
    render(<Badge>VIEWER</Badge>)

    expect(screen.getByText('VIEWER')).toHaveClass('badge--neutral')
  })

  it('exposes a title as a tooltip', () => {
    render(<Badge title="Spec, Runbook">2 docs</Badge>)

    expect(screen.getByTitle('Spec, Runbook')).toBeInTheDocument()
  })
})

describe('EmptyState', () => {
  it('states what is missing and what to do next', () => {
    render(
      <EmptyState
        title="No documents yet"
        description="Start with the architecture overview."
        action={<Button>New document</Button>}
      />,
    )

    expect(screen.getByRole('heading', { name: 'No documents yet' })).toBeInTheDocument()
    expect(screen.getByText('Start with the architecture overview.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New document' })).toBeInTheDocument()
  })

  it('renders without an action for read-only viewers', () => {
    render(<EmptyState title="Nothing here" description="A member can add the first one." />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
