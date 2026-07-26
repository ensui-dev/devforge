import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { RenderResult } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../shared/auth/AuthProvider'
import { ToastProvider } from '../shared/components/Toast'
import { FALLBACK_INSTANCE, InstanceContext } from '../shared/instance/InstanceContext'
import type { Instance } from '../shared/types'

interface RenderOptions {
  /** Initial history entries, for testing route-dependent screens. */
  route?: string
  withAuth?: boolean
  /**
   * Overrides for this deployment's settings.
   *
   * Supplied directly rather than fetched, so a test that cares about branding or
   * registration policy states it in the test rather than in a request mock.
   */
  instance?: Partial<Instance>
}

/**
 * Renders a component inside the providers the app supplies at runtime.
 *
 * Retries are disabled so a test asserting an error state does not wait for a
 * retry cycle before the state appears.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', withAuth = false, instance }: RenderOptions = {},
): RenderResult & { queryClient: QueryClient } {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })

  const resolved: Instance = { ...FALLBACK_INSTANCE, ...instance }
  const instanceValue = {
    instance: resolved,
    isLoading: false,
    docsPath: (slug?: string) => {
      if (!resolved.handbookPath) {
        return '/docs'
      }
      return slug ? `/docs/${resolved.handbookPath}/${slug}` : `/docs/${resolved.handbookPath}`
    },
  }

  const wrap = (children: ReactNode) =>
    withAuth ? <AuthProvider>{children}</AuthProvider> : <>{children}</>

  const result = render(
    <QueryClientProvider client={queryClient}>
      {wrap(
        <InstanceContext.Provider value={instanceValue}>
          <ToastProvider>
            <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
          </ToastProvider>
        </InstanceContext.Provider>,
      )}
    </QueryClientProvider>,
  )

  return { ...result, queryClient }
}
