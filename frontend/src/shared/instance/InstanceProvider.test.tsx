import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as endpoints from '../api/endpoints'
import type { Instance } from '../types'
import { InstanceGate } from './InstanceGate'
import { InstanceProvider } from './InstanceProvider'
import { useInstance } from './useInstance'

function instance(overrides: Partial<Instance> = {}): Instance {
  return {
    configured: true,
    name: 'Acme Docs',
    tagline: 'How Acme builds things',
    logoMark: '◆',
    logoImage: null,
    accentColor: '#7a3ea1',
    registrationMode: 'OPEN',
    allowedEmailDomains: [],
    publicDocsEnabled: true,
    handbookPath: 'ops/handbook',
    ...overrides,
  }
}

function Probe() {
  const { instance: current, docsPath } = useInstance()
  return (
    <>
      <p>{current.name}</p>
      <p data-testid="docs-path">{docsPath('api-authentication')}</p>
    </>
  )
}

/** The provider and the gate together, which is how the app composes them. */
function renderApp(route = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <InstanceProvider>
        <MemoryRouter initialEntries={[route]}>
          <InstanceGate>
            <Routes>
              <Route path="/" element={<Probe />} />
              <Route path="/setup" element={<p>Set up this instance</p>} />
            </Routes>
          </InstanceGate>
        </MemoryRouter>
      </InstanceProvider>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  vi.restoreAllMocks()
  document.documentElement.style.removeProperty('--trace')
  document.title = ''
})

describe('InstanceProvider', () => {
  it('supplies what the deployment says about itself', async () => {
    vi.spyOn(endpoints.instanceApi, 'describe').mockResolvedValue(instance())

    renderApp()

    expect(await screen.findByText('Acme Docs')).toBeInTheDocument()
    expect(screen.getByTestId('docs-path')).toHaveTextContent(
      '/docs/ops/handbook/api-authentication',
    )
  })

  it('repaints the accent and names the browser tab', async () => {
    vi.spyOn(endpoints.instanceApi, 'describe').mockResolvedValue(instance())

    renderApp()
    await screen.findByText('Acme Docs')

    expect(document.documentElement.style.getPropertyValue('--trace')).toBe('#7a3ea1')
    expect(document.title).toBe('Acme Docs — How Acme builds things')
  })

  /** Only the accent moves; a rebrand must not become a redesign. */
  it('leaves the accent alone when the operator chose none', async () => {
    vi.spyOn(endpoints.instanceApi, 'describe').mockResolvedValue(
      instance({ accentColor: null }),
    )

    renderApp()
    await screen.findByText('Acme Docs')

    expect(document.documentElement.style.getPropertyValue('--trace')).toBe('')
  })

  it('ignores an accent that is not a hex colour', async () => {
    vi.spyOn(endpoints.instanceApi, 'describe').mockResolvedValue(
      instance({ accentColor: 'purple' }),
    )

    renderApp()
    await screen.findByText('Acme Docs')

    expect(document.documentElement.style.getPropertyValue('--trace')).toBe('')
  })

  it('sends an unclaimed deployment to setup once the answer arrives', async () => {
    vi.spyOn(endpoints.instanceApi, 'describe').mockResolvedValue(
      instance({ configured: false }),
    )

    renderApp()

    expect(await screen.findByText('Set up this instance')).toBeInTheDocument()
  })

  /**
   * The property that matters most here. If a backend outage made the client
   * assume the instance was unconfigured, every visitor would be shown a setup
   * form during the outage — and the first request to succeed afterwards would
   * claim the instance.
   */
  it('does not offer setup when the instance cannot be reached', async () => {
    vi.spyOn(endpoints.instanceApi, 'describe').mockRejectedValue(new Error('offline'))

    renderApp()

    // Falls back to a configured instance under the project's own name.
    expect(await screen.findByText('DevForge')).toBeInTheDocument()
    expect(screen.queryByText('Set up this instance')).not.toBeInTheDocument()
  })
})
