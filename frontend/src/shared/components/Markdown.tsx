import { useContext, useMemo } from 'react'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { InstanceContext } from '../instance/InstanceContext'
import { applyInstanceVariables } from './instanceVariables'
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
  // Read defensively rather than through `useInstance`: this component is the
  // single place every document body passes through, including in tests that
  // render it without the provider. Substitution is a nicety; failing to render
  // the document would not be.
  const instance = useContext(InstanceContext)

  const html = useMemo(() => {
    const resolved = applyInstanceVariables(content ?? '', {
      // The origin actually serving the page, which is what a reader needs to
      // paste into a terminal — not a value configured somewhere else.
      url: typeof window === 'undefined' ? '' : window.location.origin,
      name: instance?.instance.name ?? 'DevForge',
    })
    const parsed = marked.parse(resolved, { async: false, gfm: true, breaks: false })
    return DOMPurify.sanitize(parsed as string, {
      // target/rel are needed so external links open safely.
      ADD_ATTR: ['target', 'rel'],
    })
  }, [content, instance?.instance.name])

  if (!content?.trim()) {
    return <p className="markdown__empty">This document has no content yet.</p>
  }

  return <div className="markdown" dangerouslySetInnerHTML={{ __html: html }} />
}
