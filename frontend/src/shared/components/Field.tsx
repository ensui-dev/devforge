import { useId } from 'react'
import type { ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes, InputHTMLAttributes } from 'react'
import './Field.css'

interface FieldShellProps {
  label: string
  /** Shown below the control; replaced by {@link error} when validation fails. */
  hint?: string
  error?: string
  children: (controlId: string, describedBy: string | undefined) => ReactNode
}

/**
 * Wires a label, hint, and error message to a control with the right ARIA
 * attributes, so every form in the app announces itself consistently.
 */
function FieldShell({ label, hint, error, children }: FieldShellProps) {
  const controlId = useId()
  const messageId = `${controlId}-message`
  const message = error ?? hint

  return (
    <div className="field">
      <label className="field__label mono-label" htmlFor={controlId}>
        {label}
      </label>
      {children(controlId, message ? messageId : undefined)}
      {message ? (
        <p
          id={messageId}
          className={error ? 'field__message field__message--error' : 'field__message'}
          role={error ? 'alert' : undefined}
        >
          {message}
        </p>
      ) : null}
    </div>
  )
}

type TextFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className'> & {
  label: string
  hint?: string
  error?: string
}

export function TextField({ label, hint, error, ...rest }: TextFieldProps) {
  return (
    <FieldShell label={label} hint={hint} error={error}>
      {(id, describedBy) => (
        <input
          {...rest}
          id={id}
          className="field__control"
          aria-describedby={describedBy}
          aria-invalid={error ? true : undefined}
        />
      )}
    </FieldShell>
  )
}

type TextAreaFieldProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id' | 'className'> & {
  label: string
  hint?: string
  error?: string
  /** Renders the control in monospace, for markdown and code. */
  mono?: boolean
}

export function TextAreaField({ label, hint, error, mono = false, ...rest }: TextAreaFieldProps) {
  return (
    <FieldShell label={label} hint={hint} error={error}>
      {(id, describedBy) => (
        <textarea
          {...rest}
          id={id}
          className={mono ? 'field__control field__control--mono' : 'field__control'}
          aria-describedby={describedBy}
          aria-invalid={error ? true : undefined}
        />
      )}
    </FieldShell>
  )
}

type SelectFieldProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id' | 'className'> & {
  label: string
  hint?: string
  error?: string
  children: ReactNode
}

export function SelectField({ label, hint, error, children, ...rest }: SelectFieldProps) {
  return (
    <FieldShell label={label} hint={hint} error={error}>
      {(id, describedBy) => (
        <select
          {...rest}
          id={id}
          className="field__control field__control--select"
          aria-describedby={describedBy}
          aria-invalid={error ? true : undefined}
        >
          {children}
        </select>
      )}
    </FieldShell>
  )
}
