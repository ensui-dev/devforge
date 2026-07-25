import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { Button } from './Button'
import { describeError } from './describeError'
import './Feedback.css'

/** Placeholder rows shown while a list loads, sized like the real content. */
export function Skeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="skeleton" aria-hidden="true">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="skeleton__row" />
      ))}
    </div>
  )
}

export function LoadingState({ label }: { label: string }) {
  return (
    <div className="loading-state" role="status">
      <span className="mono-label">{label}</span>
      <Skeleton />
    </div>
  )
}

interface ErrorStateProps {
  title?: string
  error: unknown
  onRetry?: () => void
}

export function ErrorState({ title = 'Could not load this', error, onRetry }: ErrorStateProps) {
  return (
    <div className="error-state" role="alert">
      <p className="error-state__title">{title}</p>
      <p className="error-state__detail">{describeError(error)}</p>
      {onRetry ? (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Try again
        </Button>
      ) : null}
    </div>
  )
}

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

/**
 * Last line of defence: keeps a render-time bug in one screen from blanking the
 * whole application.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unhandled render error', error, info.componentStack)
  }

  private readonly reset = () => {
    this.setState({ error: null })
  }

  render(): ReactNode {
    if (this.state.error) {
      return (
        <div className="error-boundary" role="alert">
          <p className="mono-label">Unexpected error</p>
          <h1 className="error-boundary__title">This screen stopped responding</h1>
          <p className="error-boundary__detail">{this.state.error.message}</p>
          <Button variant="secondary" onClick={this.reset}>
            Reload this screen
          </Button>
        </div>
      )
    }
    return this.props.children
  }
}
