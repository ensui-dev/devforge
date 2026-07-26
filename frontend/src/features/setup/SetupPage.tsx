import { useState } from 'react'
import type { ChangeEvent, CSSProperties, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../shared/api/client'
import { instanceApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { useAuth } from '../../shared/auth/useAuth'
import { Button } from '../../shared/components/Button'
import { SelectField, TextAreaField, TextField } from '../../shared/components/Field'
import { describeError } from '../../shared/components/describeError'
import { InstanceMark } from '../../shared/instance/InstanceMark'
import { readLogoImage, MAX_LOGO_BYTES } from './logoImage'
import type { RegistrationMode, SetupPayload } from '../../shared/types'
import './SetupPage.css'

/**
 * The four decisions, in the order they depend on each other: what this instance
 * is, how it looks, who may join it, and who runs it. The numbering is real —
 * each step is a prerequisite of the next, and the last one cannot be undone from
 * inside the product.
 */
const STEPS = [
  { key: 'identity', label: 'Identity', hint: 'Name and purpose' },
  { key: 'appearance', label: 'Appearance', hint: 'Mark and accent' },
  { key: 'access', label: 'Access', hint: 'Who may join' },
  { key: 'operator', label: 'Operator', hint: 'The first account' },
] as const

const ACCENTS = [
  { value: '#0e6b73', name: 'Plotted teal' },
  { value: '#2f5d9e', name: 'Blueprint' },
  { value: '#7a3ea1', name: 'Violet' },
  { value: '#a8442a', name: 'Rust' },
  { value: '#2c6a45', name: 'Pine' },
  { value: '#3f4a55', name: 'Graphite' },
]

interface FormState {
  name: string
  tagline: string
  logoMark: string
  logoImage: string
  accentColor: string
  registrationMode: RegistrationMode
  allowedEmailDomains: string
  publicDocsEnabled: boolean
  publicBaseUrl: string
  adminEmail: string
  adminName: string
  adminPassword: string
  adminPasswordConfirm: string
}

const INITIAL: FormState = {
  name: '',
  tagline: '',
  logoMark: '⌁',
  logoImage: '',
  accentColor: '#0e6b73',
  registrationMode: 'RESTRICTED',
  allowedEmailDomains: '',
  publicDocsEnabled: true,
  publicBaseUrl: '',
  adminEmail: '',
  adminName: '',
  adminPassword: '',
  adminPasswordConfirm: '',
}

/** What each step will not let you leave without. */
function problemsWith(step: number, form: FormState): Record<string, string> {
  const problems: Record<string, string> = {}
  if (step === 0 && !form.name.trim()) {
    problems.name = 'Give this instance a name.'
  }
  if (step === 2 && form.registrationMode === 'RESTRICTED' && !form.allowedEmailDomains.trim()) {
    problems.allowedEmailDomains = 'List at least one domain, or choose a different mode.'
  }
  if (step === 3) {
    if (!form.adminName.trim()) {
      problems.adminName = 'Enter a name for this account.'
    }
    if (!form.adminEmail.trim()) {
      problems.adminEmail = 'Enter an email address.'
    }
    if (form.adminPassword.length < 8) {
      problems.adminPassword = 'Use at least 8 characters.'
    } else if (form.adminPassword !== form.adminPasswordConfirm) {
      problems.adminPasswordConfirm = 'The two passwords do not match.'
    }
  }
  return problems
}

function toPayload(form: FormState): SetupPayload {
  return {
    instance: {
      name: form.name.trim(),
      tagline: form.tagline.trim(),
      logoMark: form.logoMark.trim() || '⌁',
      logoImage: form.logoImage,
      accentColor: form.accentColor,
      registrationMode: form.registrationMode,
      allowedEmailDomains: form.allowedEmailDomains.trim(),
      publicDocsEnabled: form.publicDocsEnabled,
      // Left empty on purpose: the handbook workspace does not exist yet, and the
      // operator sets it from the settings screen once it does.
      handbookPath: '',
      publicBaseUrl: form.publicBaseUrl.trim(),
    },
    admin: {
      email: form.adminEmail.trim(),
      displayName: form.adminName.trim(),
      password: form.adminPassword,
    },
  }
}

/**
 * First-run setup for a self-hosted instance.
 *
 * <p>Runs once, on a deployment that has no accounts. The final step creates the
 * operator's account and closes this screen permanently, which is why it is last
 * and why the password is confirmed before it is used.
 */
export function SetupPage() {
  const [step, setStep] = useState(0)
  const [form, setForm] = useState<FormState>(INITIAL)
  const [touched, setTouched] = useState(false)
  const [logoError, setLogoError] = useState<string | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [submitting, setSubmitting] = useState(false)

  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { logIn } = useAuth()

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((current) => ({ ...current, [key]: value }))

  const problems = problemsWith(step, form)
  const blocked = Object.keys(problems).length > 0
  const show = (field: string) => (touched ? problems[field] : undefined)

  // A server-side field error wins over the local one: it is the authoritative
  // answer and may name something the form could not check.
  const fieldError = (field: string) =>
    (error instanceof ApiError ? error.fieldError(field) : undefined) ?? show(field)

  const goNext = () => {
    if (blocked) {
      setTouched(true)
      return
    }
    setTouched(false)
    setStep((current) => Math.min(current + 1, STEPS.length - 1))
  }

  const goBack = () => {
    setTouched(false)
    setError(null)
    setStep((current) => Math.max(current - 1, 0))
  }

  const handleLogo = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    setLogoError(null)
    if (!file) {
      return
    }
    try {
      set('logoImage', await readLogoImage(file))
    } catch (caught) {
      setLogoError(describeError(caught, 'Could not read that image.'))
    }
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (blocked) {
      setTouched(true)
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      await instanceApi.setUp(toPayload(form))
      // The gate reads this; without the refresh it would still believe the
      // instance is unconfigured and bounce straight back here.
      await queryClient.invalidateQueries({ queryKey: queryKeys.instance.public })
      try {
        await logIn({ email: form.adminEmail.trim(), password: form.adminPassword })
        navigate('/app', { replace: true })
      } catch {
        // Setup succeeded, so never report a failure here — send them to sign in.
        navigate('/login', { replace: true })
      }
    } catch (caught) {
      setError(caught)
      setTouched(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="setup">
      <form className="setup__panel" onSubmit={handleSubmit} noValidate>
        <header className="setup__head">
          <p className="mono-label">DevForge · first run</p>
          <h1 className="setup__title">Set up this instance</h1>
          <p className="setup__lead">
            Nobody has claimed this deployment yet. What you choose here is stored in its database,
            not in the build, so every setting can be changed later — except the account you create
            at the end, which is the only way back into this screen&rsquo;s settings.
          </p>
        </header>

        <ol className="setup__steps">
          {STEPS.map((entry, index) => (
            <li
              key={entry.key}
              className={
                index === step
                  ? 'setup-step is-current'
                  : index < step
                    ? 'setup-step is-done'
                    : 'setup-step'
              }
              aria-current={index === step ? 'step' : undefined}
            >
              <span className="setup-step__n">{index + 1}</span>
              <span className="setup-step__label">{entry.label}</span>
              <span className="setup-step__hint">{entry.hint}</span>
            </li>
          ))}
        </ol>

        {error ? (
          <p className="setup__error" role="alert">
            {describeError(error, 'Could not complete setup.')}
          </p>
        ) : null}

        <div className="setup__body">
          {step === 0 ? (
            <>
              <TextField
                label="Instance name"
                required
                autoFocus
                value={form.name}
                error={fieldError('instance.name') ?? fieldError('name')}
                hint="Shown in the header, on the sign-in screen, and in the browser tab."
                onChange={(event) => set('name', event.target.value)}
              />
              <TextField
                label="Tagline"
                value={form.tagline}
                error={fieldError('instance.tagline')}
                hint="One line describing what this instance is for. Optional."
                onChange={(event) => set('tagline', event.target.value)}
              />
              <TextField
                label="Public address"
                type="url"
                placeholder="https://docs.example.com"
                value={form.publicBaseUrl}
                error={fieldError('instance.publicBaseUrl')}
                hint="Where this instance is reachable. Used to build absolute links to published documentation."
                onChange={(event) => set('publicBaseUrl', event.target.value)}
              />
            </>
          ) : null}

          {step === 1 ? (
            <>
              <div className="setup__preview">
                <InstanceMark
                  name={form.name || 'Your instance'}
                  logoMark={form.logoMark}
                  logoImage={form.logoImage || null}
                  accentColor={form.accentColor}
                />
                <p className="setup__preview-note">How the header will read.</p>
              </div>

              <TextField
                label="Mark"
                maxLength={8}
                value={form.logoMark}
                error={fieldError('instance.logoMark')}
                hint="A character or two shown beside the name. An emoji works."
                onChange={(event) => set('logoMark', event.target.value)}
              />

              <div className="field">
                <span className="field__label mono-label">Logo image</span>
                <input
                  className="setup__file"
                  type="file"
                  accept="image/png,image/jpeg,image/svg+xml,image/webp"
                  onChange={handleLogo}
                />
                <p className={logoError ? 'field__message field__message--error' : 'field__message'}>
                  {logoError ??
                    `Optional, and replaces the mark. Stored in the database, so keep it under ${Math.round(MAX_LOGO_BYTES / 1024)}KB.`}
                </p>
                {form.logoImage ? (
                  <Button variant="ghost" size="sm" onClick={() => set('logoImage', '')}>
                    Remove image
                  </Button>
                ) : null}
              </div>

              <fieldset className="setup__accents">
                <legend className="field__label mono-label">Accent</legend>
                <p className="field__message">
                  Used for links, primary actions, and active state. Everything else in the interface
                  stays as it is.
                </p>
                <div className="setup__swatches">
                  {ACCENTS.map((accent) => (
                    <button
                      key={accent.value}
                      type="button"
                      className={
                        form.accentColor === accent.value ? 'swatch is-selected' : 'swatch'
                      }
                      style={{ '--swatch': accent.value } as CSSProperties}
                      aria-pressed={form.accentColor === accent.value}
                      onClick={() => set('accentColor', accent.value)}
                    >
                      <span className="visually-hidden">{accent.name}</span>
                    </button>
                  ))}
                  <label className="swatch swatch--custom">
                    <span className="visually-hidden">Custom accent colour</span>
                    <input
                      type="color"
                      value={form.accentColor}
                      onChange={(event) => set('accentColor', event.target.value)}
                    />
                  </label>
                </div>
              </fieldset>
            </>
          ) : null}

          {step === 2 ? (
            <>
              <SelectField
                label="Registration"
                value={form.registrationMode}
                error={fieldError('instance.registrationMode')}
                onChange={(event) => set('registrationMode', event.target.value as RegistrationMode)}
              >
                <option value="OPEN">Open — anyone with the address can sign up</option>
                <option value="RESTRICTED">Restricted — only listed email domains</option>
                <option value="CLOSED">Closed — you create every account</option>
              </SelectField>

              <p className="setup__aside">
                {form.registrationMode === 'OPEN'
                  ? 'Suits a public instance. Anyone who reaches this deployment can create an account and their own workspaces.'
                  : form.registrationMode === 'RESTRICTED'
                    ? 'Suits a company instance behind a public address: colleagues sign themselves up, nobody else can.'
                    : 'Suits a private instance. You add people from the settings screen; the sign-up form refuses everyone.'}
              </p>

              {form.registrationMode === 'RESTRICTED' ? (
                <TextAreaField
                  label="Allowed email domains"
                  rows={3}
                  mono
                  placeholder={'example.com\nexample.org'}
                  value={form.allowedEmailDomains}
                  error={fieldError('instance.allowedEmailDomains') ?? show('allowedEmailDomains')}
                  hint="One per line, or comma separated. Subdomains are not included."
                  onChange={(event) => set('allowedEmailDomains', event.target.value)}
                />
              ) : null}

              <label className="setup__toggle">
                <input
                  type="checkbox"
                  aria-label="Allow public documentation"
                  aria-describedby="setup-public-docs-consequence"
                  checked={form.publicDocsEnabled}
                  onChange={(event) => set('publicDocsEnabled', event.target.checked)}
                />
                <span>
                  <strong>Allow public documentation</strong>
                  <span className="setup__toggle-hint" id="setup-public-docs-consequence">
                    Lets people publish a workspace as a documentation site that needs no sign-in.
                    Switching this off later takes every published site offline at once.
                  </span>
                </span>
              </label>
            </>
          ) : null}

          {step === 3 ? (
            <>
              <p className="setup__aside">
                This account administers the instance: it owns these settings, and on a closed
                instance it is the only way to add anyone else. Setup closes for good once it is
                created.
              </p>
              <TextField
                label="Name"
                autoComplete="name"
                required
                value={form.adminName}
                error={fieldError('admin.displayName') ?? show('adminName')}
                onChange={(event) => set('adminName', event.target.value)}
              />
              <TextField
                label="Email"
                type="email"
                autoComplete="email"
                required
                value={form.adminEmail}
                error={fieldError('admin.email') ?? show('adminEmail')}
                onChange={(event) => set('adminEmail', event.target.value)}
              />
              <TextField
                label="Password"
                type="password"
                autoComplete="new-password"
                required
                value={form.adminPassword}
                hint="At least 8 characters."
                error={fieldError('admin.password') ?? show('adminPassword')}
                onChange={(event) => set('adminPassword', event.target.value)}
              />
              <TextField
                label="Confirm password"
                type="password"
                autoComplete="new-password"
                required
                value={form.adminPasswordConfirm}
                error={show('adminPasswordConfirm')}
                onChange={(event) => set('adminPasswordConfirm', event.target.value)}
              />
            </>
          ) : null}
        </div>

        <footer className="setup__actions">
          {step > 0 ? (
            <Button variant="secondary" onClick={goBack}>
              Back
            </Button>
          ) : (
            <span />
          )}
          {step < STEPS.length - 1 ? (
            <Button onClick={goNext}>Continue</Button>
          ) : (
            <Button type="submit" loading={submitting}>
              {submitting ? 'Setting up…' : 'Finish setup'}
            </Button>
          )}
        </footer>
      </form>
    </div>
  )
}
