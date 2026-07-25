import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/renderWithProviders'
import type { DocumentReference } from '../../shared/types'
import { ReferenceRail } from './ReferenceRail'

const outgoing: DocumentReference = {
  id: 'ref-1',
  referenceType: 'DEPENDS_ON',
  outgoing: true,
  relatedDocumentId: 'doc-2',
  relatedDocumentTitle: 'Kafka topic conventions',
  relatedDocumentSlug: 'kafka-topic-conventions',
  relatedDocumentType: 'TECHNOLOGY',
  createdAt: '2026-01-01T00:00:00Z',
}

const backlink: DocumentReference = {
  id: 'ref-2',
  referenceType: 'IMPLEMENTS',
  outgoing: false,
  relatedDocumentId: 'doc-3',
  relatedDocumentTitle: 'Order service design',
  relatedDocumentSlug: 'order-service-design',
  relatedDocumentType: 'ARCHITECTURE',
  createdAt: '2026-01-02T00:00:00Z',
}

function renderRail(references: DocumentReference[], canWrite = true) {
  const onAdd = vi.fn()
  const result = renderWithProviders(
    <ReferenceRail
      workspaceId="workspace-1"
      documentId="doc-1"
      references={references}
      canWrite={canWrite}
      onAdd={onAdd}
    />,
  )
  return { ...result, onAdd }
}

describe('ReferenceRail', () => {
  it('lists outgoing links with the forward phrasing', () => {
    renderRail([outgoing])

    expect(screen.getByText('Depends on')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kafka topic conventions' })).toBeInTheDocument()
  })

  /**
   * The point of the panel: an incoming edge is described from the reader's side,
   * so "this implements X" appears on X as "implemented by".
   */
  it('describes a backlink with the inverse phrasing', () => {
    renderRail([backlink])

    expect(screen.getByText('Implemented by')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Order service design' })).toBeInTheDocument()
  })

  it('separates outgoing links from backlinks', () => {
    renderRail([outgoing, backlink])

    expect(screen.getByText('This document')).toBeInTheDocument()
    expect(screen.getByText('Referenced by')).toBeInTheDocument()
  })

  it('says what is missing when there are no links', () => {
    renderRail([])

    expect(screen.getByText('No outgoing links yet.')).toBeInTheDocument()
    expect(screen.getByText('Nothing points here yet.')).toBeInTheDocument()
  })

  it('links to the far end of each edge', () => {
    renderRail([outgoing])

    expect(screen.getByRole('link', { name: 'Kafka topic conventions' })).toHaveAttribute(
      'href',
      '/workspaces/workspace-1/documents/doc-2',
    )
  })

  it('offers to add a link when the caller can write', async () => {
    const { onAdd } = renderRail([])

    await userEvent.click(screen.getByRole('button', { name: 'Link document' }))

    expect(onAdd).toHaveBeenCalledTimes(1)
  })

  it('hides write controls from a viewer', () => {
    renderRail([outgoing], false)

    expect(screen.queryByRole('button', { name: 'Link document' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove reference/ })).not.toBeInTheDocument()
  })

  /** A backlink belongs to the other document, matching what the API permits. */
  it('offers removal only for edges this document declared', () => {
    renderRail([outgoing, backlink])

    expect(
      screen.getByRole('button', { name: 'Remove reference to Kafka topic conventions' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Remove reference to Order service design' }),
    ).not.toBeInTheDocument()
  })

  it('copes with a reference whose target has been deleted', () => {
    renderRail([{ ...outgoing, relatedDocumentTitle: null, relatedDocumentType: null }])

    expect(screen.getByRole('link', { name: 'Untitled document' })).toBeInTheDocument()
  })
})
