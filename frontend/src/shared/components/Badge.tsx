import type { ReactNode } from 'react'
import './Badge.css'

type BadgeTone = 'neutral' | 'trace' | 'signal' | 'danger' | 'success'

interface BadgeProps {
  children: ReactNode
  tone?: BadgeTone
  title?: string
}

/** A small monospace tag. Used for document types, roles, and priorities. */
export function Badge({ children, tone = 'neutral', title }: BadgeProps) {
  return (
    <span className={`badge badge--${tone}`} title={title}>
      {children}
    </span>
  )
}
