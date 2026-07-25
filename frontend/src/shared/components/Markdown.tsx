import { useMemo } from 'react'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import './Markdown.css'

/**
 * Renders document bodies.
 *
 * Document content is written by teammates and stored verbatim, so it is untrusted
 * input as far as the browser is concerned: `marked` parses it and DOMPurify
 * strips anything executable before it reaches the DOM. Skipping the sanitiser
 * would make every document body a stored-XSS vector against the whole team.
 */
export function Markdown({ content }: { content: string }) {
  const html = useMemo(() => {
    const parsed = marked.parse(content ?? '', { async: false, gfm: true, breaks: false })
    return DOMPurify.sanitize(parsed as string, {
      // target/rel are needed so external links open safely.
      ADD_ATTR: ['target', 'rel'],
    })
  }, [content])

  if (!content?.trim()) {
    return <p className="markdown__empty">This document has no content yet.</p>
  }

  return <div className="markdown" dangerouslySetInnerHTML={{ __html: html }} />
}
