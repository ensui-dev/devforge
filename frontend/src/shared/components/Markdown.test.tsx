import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Markdown } from './Markdown'

describe('Markdown', () => {
  it('renders headings as headings', () => {
    render(<Markdown content={'# Overview\n\nSome prose.'} />)

    expect(screen.getByRole('heading', { level: 1, name: 'Overview' })).toBeInTheDocument()
    expect(screen.getByText('Some prose.')).toBeInTheDocument()
  })

  it('renders lists', () => {
    render(<Markdown content={'- first\n- second'} />)

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('renders fenced code blocks', () => {
    const { container } = render(<Markdown content={'```java\nvar x = 1;\n```'} />)

    expect(container.querySelector('pre code')).toBeTruthy()
    expect(screen.getByText(/var x = 1;/)).toBeInTheDocument()
  })

  it('renders tables', () => {
    render(<Markdown content={'| a | b |\n| - | - |\n| 1 | 2 |'} />)

    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  it('invites the reader to write when the body is empty', () => {
    render(<Markdown content="" />)

    expect(screen.getByText('This document has no content yet.')).toBeInTheDocument()
  })

  it('treats whitespace-only content as empty', () => {
    render(<Markdown content={'   \n  '} />)

    expect(screen.getByText('This document has no content yet.')).toBeInTheDocument()
  })

  it('resolves the page count when the body is part of a site', () => {
    render(<Markdown content="All {{handbook.pages}} pages." pages={31} />)

    expect(screen.getByText('All 31 pages.')).toBeInTheDocument()
  })

  /** Read inside the app, a document is not part of a site and has no count. */
  it('leaves the page count visible when there is none to resolve', () => {
    render(<Markdown content="All {{handbook.pages}} pages." />)

    expect(screen.getByText('All {{handbook.pages}} pages.')).toBeInTheDocument()
  })

  /**
   * Document bodies are written by teammates and rendered as HTML, so this is the
   * test that keeps a document from becoming a stored-XSS vector.
   */
  it('strips script tags from document content', () => {
    const { container } = render(
      <Markdown content={'Safe text\n\n<script>window.hacked = true</script>'} />,
    )

    expect(container.querySelector('script')).toBeNull()
    expect(screen.getByText('Safe text')).toBeInTheDocument()
  })

  it('strips inline event handlers', () => {
    const { container } = render(<Markdown content={'<img src="x" onerror="window.hacked = true">'} />)

    expect(container.querySelector('img')?.getAttribute('onerror')).toBeNull()
  })

  it('removes javascript: URLs from links', () => {
    const { container } = render(<Markdown content={'[click](javascript:alert(1))'} />)

    const href = container.querySelector('a')?.getAttribute('href')
    expect(href ?? '').not.toContain('javascript:')
  })

  it('keeps ordinary links intact', () => {
    render(<Markdown content={'[docs](https://example.com/docs)'} />)

    expect(screen.getByRole('link', { name: 'docs' })).toHaveAttribute(
      'href',
      'https://example.com/docs',
    )
  })
})
