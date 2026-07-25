import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SelectField, TextAreaField, TextField } from './Field'

describe('TextField', () => {
  it('associates the label with the control', () => {
    render(<TextField label="URL slug" value="" onChange={() => {}} />)

    expect(screen.getByLabelText('URL slug')).toBeInTheDocument()
  })

  it('shows a hint and links it to the control for assistive tech', () => {
    render(<TextField label="Name" hint="Shown to your team." value="" onChange={() => {}} />)

    const input = screen.getByLabelText('Name')
    expect(screen.getByText('Shown to your team.')).toBeInTheDocument()
    expect(input).toHaveAccessibleDescription('Shown to your team.')
  })

  it('announces an error and marks the control invalid', () => {
    render(<TextField label="Slug" error="must be lowercase" value="" onChange={() => {}} />)

    expect(screen.getByRole('alert')).toHaveTextContent('must be lowercase')
    expect(screen.getByLabelText('Slug')).toHaveAttribute('aria-invalid', 'true')
  })

  it('replaces the hint with the error when both are supplied', () => {
    render(
      <TextField
        label="Slug"
        hint="Lowercase letters and hyphens."
        error="must be lowercase"
        value=""
        onChange={() => {}}
      />,
    )

    expect(screen.queryByText('Lowercase letters and hyphens.')).not.toBeInTheDocument()
    expect(screen.getByText('must be lowercase')).toBeInTheDocument()
  })

  it('reports what the user typed', async () => {
    const onChange = vi.fn()
    render(<TextField label="Name" value="" onChange={onChange} />)

    await userEvent.type(screen.getByLabelText('Name'), 'Platform')

    expect(onChange).toHaveBeenCalled()
  })
})

describe('TextAreaField', () => {
  it('renders a multi-line control', () => {
    render(<TextAreaField label="Content" value="" onChange={() => {}} />)

    expect(screen.getByLabelText('Content').tagName).toBe('TEXTAREA')
  })

  it('uses a monospace control when asked', () => {
    render(<TextAreaField label="Content" mono value="" onChange={() => {}} />)

    expect(screen.getByLabelText('Content')).toHaveClass('field__control--mono')
  })
})

describe('SelectField', () => {
  it('renders its options and reflects the value', () => {
    render(
      <SelectField label="Type" value="RUNBOOK" onChange={() => {}}>
        <option value="ARCHITECTURE">Architecture</option>
        <option value="RUNBOOK">Runbook</option>
      </SelectField>,
    )

    expect(screen.getByLabelText('Type')).toHaveValue('RUNBOOK')
    expect(screen.getAllByRole('option')).toHaveLength(2)
  })
})
