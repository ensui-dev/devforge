import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DiffView } from './DiffView'

describe('DiffView', () => {
  it('shows what was added and what was removed', () => {
    render(<DiffView before={'one\ntwo'} after={'one\nthree'} label="Changes" />)

    expect(screen.getByText('two')).toBeInTheDocument()
    expect(screen.getByText('three')).toBeInTheDocument()
  })

  /**
   * Colour is never the only signal: the diff has to survive being printed, read
   * aloud, or seen by someone who cannot tell the two washes apart.
   */
  it('marks every changed line in text as well as colour', () => {
    const { container } = render(
      <DiffView before={'one'} after={'two'} label="Changes" />,
    )

    const markers = Array.from(container.querySelectorAll('.diff__marker')).map(
      (node) => node.textContent,
    )
    expect(markers).toContain('+')
    expect(markers).toContain('−')
  })

  it('is named, for anyone not reading the colours', () => {
    render(<DiffView before={'a'} after={'b'} label="Changes in Kafka conventions" />)

    expect(screen.getByRole('group', { name: 'Changes in Kafka conventions' })).toBeInTheDocument()
  })

  /** An empty box reads as broken, so nothing is shown unless something is said. */
  it('renders nothing when the two texts are identical', () => {
    const { container } = render(<DiffView before={'same'} after={'same'} label="Changes" />)

    expect(container.querySelector('.diff')).toBeNull()
    expect(container.textContent).toBe('')
  })

  it('explains an absent diff when given words for it', () => {
    render(
      <DiffView
        before={'same'}
        after={'same'}
        label="Changes"
        unchangedNote="Its wording has not changed."
      />,
    )

    expect(screen.getByText('Its wording has not changed.')).toBeInTheDocument()
  })
})
