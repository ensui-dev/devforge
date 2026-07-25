import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Button } from './Button'
import { Modal } from './Modal'

// Read from disk rather than importing. Vite resolves `./Modal.css` to injected
// styles and `?raw` to an empty string under this config, either of which would
// make the stylesheet assertions below pass without inspecting anything.
const modalCss = readFileSync(resolve(process.cwd(), 'src/shared/components/Modal.css'), 'utf8')

afterEach(() => {
  vi.restoreAllMocks()
})

function renderModal(open: boolean, onClose = vi.fn()) {
  render(
    <Modal
      title="Delete this document?"
      open={open}
      onClose={onClose}
      footer={<Button>Confirm</Button>}
    >
      <p>This cannot be undone.</p>
    </Modal>,
  )
  return { onClose }
}

describe('Modal', () => {
  it('leaves the dialog closed when it is not open', () => {
    renderModal(false)

    // Visibility follows from this attribute: the browser hides a <dialog>
    // without it. jsdom applies its own user-agent stylesheet, so asserting on
    // computed styles here would measure jsdom rather than this component.
    expect(document.querySelector('dialog')).not.toHaveAttribute('open')
  })

  it('opens the dialog when asked', () => {
    renderModal(true)

    expect(document.querySelector('dialog')).toHaveAttribute('open')
  })

  it('shows its title, body, and actions when open', () => {
    renderModal(true)

    expect(screen.getByRole('heading', { name: 'Delete this document?' })).toBeInTheDocument()
    expect(screen.getByText('This cannot be undone.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeInTheDocument()
  })

  it('labels the dialog for assistive technology', () => {
    renderModal(true)

    expect(screen.getByRole('dialog', { name: 'Delete this document?' })).toBeInTheDocument()
  })

  it('closes when the close control is used', async () => {
    const { onClose } = renderModal(true)

    await userEvent.click(screen.getByRole('button', { name: 'Close' }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('routes the platform cancel event through onClose', () => {
    const { onClose } = renderModal(true)

    // Escape fires `cancel`; handling it keeps React state and the element's own
    // open state from diverging.
    document.querySelector('dialog')!.dispatchEvent(new Event('cancel', { cancelable: true }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('renders without a footer', () => {
    render(
      <Modal title="Plain" open onClose={vi.fn()}>
        <p>Body only.</p>
      </Modal>,
    )

    expect(screen.getByText('Body only.')).toBeInTheDocument()
  })
})

/**
 * Guards a CSS-only regression that no rendering test in this suite can catch.
 *
 * A browser hides a closed `<dialog>` through `dialog:not([open]) { display: none }`
 * in its user-agent stylesheet, and author styles always outrank user-agent
 * styles. So an unconditional `display` on the base `.modal` rule silently
 * un-hid every closed dialog, and screens that mount several modals rendered all
 * of their contents inline at once.
 *
 * jsdom cannot reproduce this: it applies its own user-agent stylesheet and does
 * not evaluate this stylesheet's compound selectors, so a computed-style
 * assertion would pass either way. Asserting on the stylesheet text is therefore
 * the only honest guard available at this level; the fix itself was confirmed in
 * a real browser.
 */
describe('Modal stylesheet', () => {
  /** The declarations of a top-level rule, by exact selector. */
  function declarationsFor(selector: string): string {
    const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const match = new RegExp(`(?:^|\\})\\s*${escaped}\\s*\\{([^}]*)\\}`, 'm').exec(modalCss)
    if (!match) {
      throw new Error(`No rule found for selector "${selector}"`)
    }
    return match[1]
  }

  // Without this, an unreadable or empty stylesheet would let every assertion
  // below pass while inspecting nothing.
  it('reads the stylesheet it asserts on', () => {
    expect(modalCss).toContain('.modal')
    expect(modalCss.length).toBeGreaterThan(200)
  })

  it('does not set display on the base rule', () => {
    expect(declarationsFor('.modal')).not.toMatch(/(^|;)\s*display\s*:/)
  })

  it('hides the dialog while closed', () => {
    expect(declarationsFor('.modal:not([open])')).toMatch(/display\s*:\s*none/)
  })

  it('applies its layout only while open', () => {
    expect(declarationsFor('.modal[open]')).toMatch(/display\s*:\s*flex/)
  })
})
