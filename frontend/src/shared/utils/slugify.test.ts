import { describe, expect, it, vi } from 'vitest'
import { formatRelative, initials, slugify } from './slugify'

describe('slugify', () => {
  it('lowercases and hyphenates words', () => {
    expect(slugify('Service Architecture')).toBe('service-architecture')
  })

  it('collapses runs of punctuation into a single hyphen', () => {
    expect(slugify('Auth  &&  Tokens!!')).toBe('auth-tokens')
  })

  it('trims leading and trailing separators', () => {
    expect(slugify('  --Overview--  ')).toBe('overview')
  })

  it('strips diacritics rather than dropping the letters', () => {
    expect(slugify('Café Déploiement')).toBe('cafe-deploiement')
  })

  it('keeps digits', () => {
    expect(slugify('ADR 0042 Retry policy')).toBe('adr-0042-retry-policy')
  })

  it('produces an empty string when nothing usable remains', () => {
    expect(slugify('!!!')).toBe('')
  })

  it('caps the length and never ends with a hyphen', () => {
    const result = slugify('word '.repeat(50))

    expect(result.length).toBeLessThanOrEqual(100)
    expect(result.endsWith('-')).toBe(false)
  })

  it('matches the pattern the backend accepts', () => {
    const pattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
    for (const input of ['Service Architecture', 'ADR 42: Retry!', 'Café']) {
      expect(slugify(input)).toMatch(pattern)
    }
  })
})

describe('formatRelative', () => {
  it('describes recent times in minutes', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-12T12:00:00Z'))

    expect(formatRelative('2026-03-12T11:45:00Z')).toBe('15m ago')

    vi.useRealTimers()
  })

  it('describes the last minute as just now', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-12T12:00:00Z'))

    expect(formatRelative('2026-03-12T11:59:40Z')).toBe('just now')

    vi.useRealTimers()
  })

  it('switches to hours and days as time passes', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-03-12T12:00:00Z'))

    expect(formatRelative('2026-03-12T06:00:00Z')).toBe('6h ago')
    expect(formatRelative('2026-03-08T12:00:00Z')).toBe('4d ago')

    vi.useRealTimers()
  })

  it('returns an empty string for an unparseable value', () => {
    expect(formatRelative('not a date')).toBe('')
  })
})

describe('initials', () => {
  it('takes the first letter of the first two words', () => {
    expect(initials('Ada Lovelace')).toBe('AL')
  })

  it('handles a single name', () => {
    expect(initials('Ada')).toBe('A')
  })

  it('ignores extra words and whitespace', () => {
    expect(initials('  ada  b  lovelace ')).toBe('AB')
  })
})
