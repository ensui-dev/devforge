import { useEffect, useState } from 'react'

/**
 * A value that settles before anyone acts on it.
 *
 * Search runs on every keystroke, which was tolerable while a query only matched
 * whole words — nothing was found until a word was finished, so the requests in
 * between returned nothing anyway. Now that a prefix matches, every one of those
 * keystrokes is a real query returning real results that are immediately thrown
 * away by the next.
 *
 * The delay is short on purpose. Long enough that a typed word is one request
 * rather than eight, short enough that it still reads as "as you type" rather
 * than as a pause.
 */
export function useDebounced<T>(value: T, delayMs = 200): T {
  const [settled, setSettled] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs)
    // Cleared on every change, so the timer only fires once typing stops.
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return settled
}
