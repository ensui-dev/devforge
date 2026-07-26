import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import { SiteChrome } from './SiteChrome'

describe('SiteChrome', () => {
  it('names the instance rather than the product', () => {
    renderWithProviders(<SiteChrome>content</SiteChrome>, {
      withAuth: true,
      instance: { name: 'Acme Docs', logoMark: '◆' },
    })

    expect(screen.getByText('Acme Docs')).toBeInTheDocument()
    expect(screen.getByText('◆')).toBeInTheDocument()
  })

  /**
   * An operator who switched public documentation off has taken these pages
   * offline; a navigation link to them would lead to a 404.
   */
  it('drops the handbook links when the instance publishes no documentation', () => {
    renderWithProviders(<SiteChrome>content</SiteChrome>, {
      withAuth: true,
      instance: { publicDocsEnabled: false },
    })

    expect(screen.queryByRole('link', { name: 'Handbook' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'API reference' })).not.toBeInTheDocument()
    // The rest of the navigation survives — only the offline routes go.
    const nav = screen.getByRole('navigation', { name: 'Site' })
    expect(within(nav).getByRole('link', { name: 'Overview' })).toBeInTheDocument()
  })

  it('keeps the handbook links when documentation is public', () => {
    renderWithProviders(<SiteChrome>content</SiteChrome>, {
      withAuth: true,
      instance: { publicDocsEnabled: true, handbookPath: 'ops/handbook' },
    })

    expect(screen.getAllByRole('link', { name: 'Handbook' }).length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: 'API reference' })).toHaveAttribute(
      'href',
      '/docs/ops/handbook/api-authentication',
    )
  })

  /**
   * Being open source is part of what this project is, not a footnote — so the
   * route to the source is on every public page, whatever the instance has
   * switched off.
   */
  it('links to the source and names the licence on every page', () => {
    renderWithProviders(<SiteChrome>content</SiteChrome>, {
      withAuth: true,
      instance: { publicDocsEnabled: false, registrationMode: 'CLOSED' },
    })

    const source = screen.getByRole('link', { name: /source/i })
    expect(source).toHaveAttribute('href', 'https://github.com/ensui-dev/devforge')
    expect(source).toHaveAttribute('rel', expect.stringContaining('noopener'))
    expect(screen.getByRole('link', { name: 'MIT' })).toBeInTheDocument()
    expect(screen.getByText(/free and open source software/)).toBeInTheDocument()
  })

  /** A closed instance refuses every sign-up, so inviting one is a dead end. */
  it('does not offer to create an account on a closed instance', () => {
    renderWithProviders(<SiteChrome>content</SiteChrome>, {
      withAuth: true,
      instance: { registrationMode: 'CLOSED' },
    })

    expect(screen.queryByRole('link', { name: 'Get started' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('offers to create an account when registration is open', () => {
    renderWithProviders(<SiteChrome>content</SiteChrome>, {
      withAuth: true,
      instance: { registrationMode: 'OPEN' },
    })

    expect(screen.getByRole('link', { name: 'Get started' })).toBeInTheDocument()
  })
})
