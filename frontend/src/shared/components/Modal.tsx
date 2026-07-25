import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import './Modal.css'

interface ModalProps {
  title: string
  open: boolean
  onClose: () => void
  children: ReactNode
  /** Footer actions, laid out right-aligned. */
  footer?: ReactNode
  width?: 'sm' | 'md' | 'lg'
}

/**
 * A dialog built on the native `<dialog>` element, so focus trapping, the
 * backdrop, and Escape handling come from the platform rather than from bespoke
 * key listeners.
 */
export function Modal({ title, open, onClose, children, footer, width = 'md' }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    if (open && !dialog.open) {
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) {
      return
    }
    // `cancel` fires on Escape; routing it through onClose keeps React state and
    // the element's open state from diverging.
    const handleCancel = (event: Event) => {
      event.preventDefault()
      onClose()
    }
    dialog.addEventListener('cancel', handleCancel)
    return () => dialog.removeEventListener('cancel', handleCancel)
  }, [onClose])

  return (
    <dialog ref={dialogRef} className={`modal modal--${width}`} aria-label={title}>
      <div className="modal__header">
        <h2 className="modal__title">{title}</h2>
        <button type="button" className="modal__close" onClick={onClose} aria-label="Close">
          &times;
        </button>
      </div>
      <div className="modal__body">{children}</div>
      {footer ? <div className="modal__footer">{footer}</div> : null}
    </dialog>
  )
}
