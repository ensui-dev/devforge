import type { ReactNode } from 'react'
import { InstanceMark } from '../../shared/instance/InstanceMark'
import { useInstance } from '../../shared/instance/useInstance'
import './AuthShell.css'

interface AuthShellProps {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}

/**
 * Frame for the sign-in and sign-up screens.
 *
 * The right panel states the product thesis using the vocabulary the app uses
 * everywhere else — typed edges between documents — so the first thing a new user
 * sees is the thing that makes this different from a wiki plus a board.
 */
export function AuthShell({ title, subtitle, children, footer }: AuthShellProps) {
  const { instance } = useInstance()

  return (
    <div className="auth">
      <main className="auth__panel">
        <div className="auth__brand">
          <InstanceMark
            name={instance.name}
            logoMark={instance.logoMark}
            logoImage={instance.logoImage}
          />
        </div>
        <div className="auth__intro">
          <h1 className="auth__title">{title}</h1>
          <p className="auth__subtitle">{subtitle}</p>
        </div>
        {children}
        <p className="auth__footer">{footer}</p>
      </main>

      <aside className="auth__aside" aria-hidden="true">
        <p className="mono-label">The connected knowledge graph</p>
        <ul className="auth__trace">
          <li className="auth__trace-node">
            <span className="auth__trace-type">ARCHITECTURE</span>
            <span className="auth__trace-title">Event ingestion pipeline</span>
          </li>
          <li className="auth__trace-edge">
            <span className="auth__trace-label">DEPENDS_ON</span>
          </li>
          <li className="auth__trace-node">
            <span className="auth__trace-type">TECHNOLOGY</span>
            <span className="auth__trace-title">Kafka topic conventions</span>
          </li>
          <li className="auth__trace-edge">
            <span className="auth__trace-label">DOCUMENTED_BY</span>
          </li>
          <li className="auth__trace-node auth__trace-node--task">
            <span className="auth__trace-type">TASK</span>
            <span className="auth__trace-title">Partition the orders topic</span>
          </li>
        </ul>
        <p className="auth__aside-note">
          Change one document and every task and page that depends on it is one hop away.
        </p>
      </aside>
    </div>
  )
}
