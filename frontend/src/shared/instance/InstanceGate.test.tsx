import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../../test/renderWithProviders'
import { InstanceGate } from './InstanceGate'

/** A stand-in for the whole route table, so the assertions are about the gate. */
function routes() {
  return (
    <InstanceGate>
      <Routes>
        <Route path="/" element={<p>Homepage</p>} />
        <Route path="/docs" element={<p>Handbook</p>} />
        <Route path="/setup" element={<p>Set up this instance</p>} />
      </Routes>
    </InstanceGate>
  )
}

describe('InstanceGate', () => {
  it('sends a deployment nobody has claimed to its setup screen', () => {
    renderWithProviders(routes(), { route: '/', instance: { configured: false } })

    expect(screen.getByText('Set up this instance')).toBeInTheDocument()
    expect(screen.queryByText('Homepage')).not.toBeInTheDocument()
  })

  /** Any route, not just the homepage — an unclaimed instance has nothing to show. */
  it('redirects from a deep link while the instance is unconfigured', () => {
    renderWithProviders(routes(), { route: '/docs', instance: { configured: false } })

    expect(screen.getByText('Set up this instance')).toBeInTheDocument()
    expect(screen.queryByText('Handbook')).not.toBeInTheDocument()
  })

  /**
   * The security-relevant half. The endpoint already refuses a second run, but a
   * form that cannot succeed invites someone to try.
   */
  it('takes the setup screen away once the instance is configured', () => {
    renderWithProviders(routes(), { route: '/setup', instance: { configured: true } })

    expect(screen.getByText('Homepage')).toBeInTheDocument()
    expect(screen.queryByText('Set up this instance')).not.toBeInTheDocument()
  })

  it('leaves an ordinary route alone on a configured instance', () => {
    renderWithProviders(routes(), { route: '/docs', instance: { configured: true } })

    expect(screen.getByText('Handbook')).toBeInTheDocument()
  })
})
