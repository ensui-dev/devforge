import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// jsdom does not implement the modal parts of <dialog>, which the Modal component
// drives directly. Stubbing them keeps dialog-bearing components testable without
// pushing a non-standard fallback into the component itself.
if (typeof HTMLDialogElement !== 'undefined') {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.open = true
    }
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.open = false
    }
  }
}

/**
 * Node 26 ships its own `localStorage` global that is inert unless the process is
 * started with `--localstorage-file`. It collides with the one jsdom would
 * install, and the result is a window with `sessionStorage` but no `localStorage`
 * — so the auth provider has nowhere to persist a session.
 *
 * An in-memory Storage keeps the application using the standard API while the
 * tests get behaviour they can reset between cases.
 */
function createMemoryStorage(): Storage {
  let entries = new Map<string, string>()

  return {
    get length() {
      return entries.size
    },
    clear: () => {
      entries = new Map()
    },
    getItem: (key: string) => entries.get(key) ?? null,
    key: (index: number) => Array.from(entries.keys())[index] ?? null,
    removeItem: (key: string) => {
      entries.delete(key)
    },
    setItem: (key: string, value: string) => {
      entries.set(key, String(value))
    },
  }
}

if (typeof window !== 'undefined' && !window.localStorage) {
  Object.defineProperty(window, 'localStorage', {
    value: createMemoryStorage(),
    configurable: true,
  })
}

beforeEach(() => {
  window.localStorage.clear()
})

afterEach(() => {
  cleanup()
})
