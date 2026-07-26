import { Link, NavLink } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '../../shared/auth/useAuth'
import { GitHubMark } from '../../shared/components/GitHubMark'
import { PROJECT } from '../../shared/project'
import { InstanceMark } from '../../shared/instance/InstanceMark'
import { useInstance } from '../../shared/instance/useInstance'
import './SiteChrome.css'

/**
 * Header and footer for the public pages.
 *
 * Shared by the homepage and the documentation so navigation never changes shape
 * between them, and so the route to the handbook is in the same place on both.
 * The primary action adapts to whether anyone is signed in.
 */
export function SiteChrome({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const { instance, docsPath } = useInstance()

  // A route that leads nowhere is worse than no route. An operator who switched
  // public documentation off has taken these pages offline, and an instance that
  // refuses registrations should not invite them.
  const showDocs = instance.publicDocsEnabled
  const showRegister = instance.registrationMode !== 'CLOSED'

  return (
    <div className="site">
      <header className="site-head">
        <div className="site-head__inner">
          <Link className="brand" to="/">
            <InstanceMark
              name={instance.name}
              logoMark={instance.logoMark}
              logoImage={instance.logoImage}
            />
          </Link>

          <nav className="site-nav" aria-label="Site">
            <NavLink
              to="/"
              end
              className={({ isActive }) => (isActive ? 'site-nav__link is-active' : 'site-nav__link')}
            >
              Overview
            </NavLink>
            {showDocs ? (
              <NavLink
                to="/docs"
                className={({ isActive }) =>
                  isActive ? 'site-nav__link is-active' : 'site-nav__link'
                }
              >
                Handbook
              </NavLink>
            ) : null}
            <a
              className="site-nav__link site-nav__source"
              href={PROJECT.repository}
              target="_blank"
              rel="noreferrer noopener"
            >
              <GitHubMark />
              Source
            </a>
            {isAuthenticated ? (
              <Link className="site-nav__link site-nav__cta" to="/app">
                Open workspaces
              </Link>
            ) : (
              <>
                <Link
                  className={showRegister ? 'site-nav__link' : 'site-nav__link site-nav__cta'}
                  to="/login"
                >
                  Sign in
                </Link>
                {showRegister ? (
                  <Link className="site-nav__link site-nav__cta" to="/register">
                    Get started
                  </Link>
                ) : null}
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="site__main">{children}</main>

      <footer className="site-foot">
        <div className="site-foot__inner">
          <div className="site-foot__about">
            <p className="site-foot__note">
              {instance.tagline ??
                'DevForge keeps documentation beside delivery, and links the two. The handbook is a live workspace on this instance — editing it updates these pages.'}
            </p>
            <p className="site-foot__license">
            <a href={PROJECT.repository} target="_blank" rel="noreferrer noopener">
              {PROJECT.name}
            </a>{' '}
            is free and open source software, licensed under{' '}
            <a href={PROJECT.licenseUrl} target="_blank" rel="noreferrer noopener">
              {PROJECT.license}
            </a>
              . Run your own copy.
            </p>
          </div>
          <nav className="site-foot__links" aria-label="Footer">
            <Link to="/">Overview</Link>
            {showDocs ? (
              <>
                <Link to="/docs">Handbook</Link>
                <Link to={docsPath('api-authentication')}>API reference</Link>
                <Link to={docsPath('troubleshooting')}>Troubleshooting</Link>
              </>
            ) : null}
          </nav>
        </div>
      </footer>
    </div>
  )
}
