import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDebounced } from './useDebounced'

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useDebounced', () => {
  it('starts with the value it was given, so nothing waits for a first result', () => {
    const { result } = renderHook(() => useDebounced('auth'))

    expect(result.current).toBe('auth')
  })

  it('holds the previous value until typing stops', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounced(value), {
      initialProps: { value: 'a' },
    })

    rerender({ value: 'au' })
    expect(result.current).toBe('a')

    act(() => vi.advanceTimersByTime(200))
    expect(result.current).toBe('au')
  })

  /**
   * The point: a typed word is one query rather than one per keystroke. Each
   * change has to cancel the previous timer, or the intermediate values all
   * arrive late instead of not at all.
   */
  it('settles once on the last value, not once per keystroke', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounced(value), {
      initialProps: { value: 'a' },
    })

    for (const value of ['au', 'aut', 'auth', 'authe']) {
      rerender({ value })
      act(() => vi.advanceTimersByTime(50))
    }

    // 200ms of typing, none of it idle: still the first value.
    expect(result.current).toBe('a')

    act(() => vi.advanceTimersByTime(200))
    expect(result.current).toBe('authe')
  })

  it('honours a delay of its own', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounced(value, 1000), {
      initialProps: { value: 'a' },
    })

    rerender({ value: 'b' })
    act(() => vi.advanceTimersByTime(500))
    expect(result.current).toBe('a')

    act(() => vi.advanceTimersByTime(500))
    expect(result.current).toBe('b')
  })

  /** Clearing the box is a change like any other, including back to empty. */
  it('settles on an empty value too', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounced(value), {
      initialProps: { value: 'auth' },
    })

    rerender({ value: '' })
    act(() => vi.advanceTimersByTime(200))

    expect(result.current).toBe('')
  })
})
