import { describe, expect, it } from 'vitest'
import { applyInstanceVariables } from './instanceVariables'

const vars = { url: 'https://devforge.example.com', name: 'Acme Docs' }

describe('applyInstanceVariables', () => {
  it('resolves the instance address so a page is right on every deployment', () => {
    expect(applyInstanceVariables('Open {{instance.url}} and sign in.', vars)).toBe(
      'Open https://devforge.example.com and sign in.',
    )
  })

  it('resolves the instance name', () => {
    expect(applyInstanceVariables('Welcome to {{instance.name}}.', vars)).toBe(
      'Welcome to Acme Docs.',
    )
  })

  it('replaces every occurrence, not just the first', () => {
    const out = applyInstanceVariables('{{instance.url}}/docs and {{instance.url}}/app', vars)
    expect(out).toBe('https://devforge.example.com/docs and https://devforge.example.com/app')
  })

  it('tolerates whitespace inside the braces', () => {
    expect(applyInstanceVariables('{{ instance.url }}', vars)).toBe('https://devforge.example.com')
  })

  /** `{{instance.url}}/api` must not become `https://host//api`. */
  it('does not produce a double slash when a path follows', () => {
    const out = applyInstanceVariables('{{instance.url}}/api/public/instance', {
      ...vars,
      url: 'https://devforge.example.com/',
    })
    expect(out).toBe('https://devforge.example.com/api/public/instance')
  })

  /**
   * Document bodies are user-written prose. Braces appear in code samples, and
   * eating them would corrupt the document.
   */
  it('leaves unrelated braces alone', () => {
    const content = 'Use `${VAR}` in bash, and `{{ mustache }}` renders literally.'
    expect(applyInstanceVariables(content, vars)).toBe(content)
  })

  it('leaves an unknown instance variable visible rather than blanking it', () => {
    expect(applyInstanceVariables('{{instance.nope}}', vars)).toBe('{{instance.nope}}')
  })

  it('returns content with no variables untouched and cheaply', () => {
    const content = '# Heading\n\nOrdinary prose with no substitution.'
    expect(applyInstanceVariables(content, vars)).toBe(content)
  })

  it('handles empty content', () => {
    expect(applyInstanceVariables('', vars)).toBe('')
  })
})
