import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../shared/api/client'
import { instanceApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { useAuth } from '../../shared/auth/useAuth'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { SelectField, TextAreaField, TextField } from '../../shared/components/Field'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { useToast } from '../../shared/components/useToast'
import { describeError } from '../../shared/components/describeError'
import { InstanceMark } from '../../shared/instance/InstanceMark'
import { useInstance } from '../../shared/instance/useInstance'
import { readLogoImage, MAX_LOGO_BYTES } from '../setup/logoImage'
import { AddOperatorDialog } from './AddOperatorDialog'
import type { AdminInstance, InstanceSettingsPayload, RegistrationMode } from '../../shared/types'
import './InstanceSettingsPage.css'

/** The settings form, seeded from what the server currently holds. */
function toForm(settings: AdminInstance): InstanceSettingsPayload {
  return {
    name: settings.instance.name,
    tagline: settings.instance.tagline ?? '',
    logoMark: settings.instance.logoMark ?? '',
    logoImage: settings.instance.logoImage ?? '',
    accentColor: settings.instance.accentColor ?? '',
    registrationMode: settings.instance.registrationMode,
    allowedEmailDomains: settings.allowedEmailDomains ?? '',
    publicDocsEnabled: settings.instance.publicDocsEnabled,
    handbookPath: settings.instance.handbookPath ?? '',
    publicBaseUrl: settings.publicBaseUrl ?? '',
  }
}

/**
 * Everything the operator of a self-hosted instance controls.
 *
 * <p>Reachable only by an instance administrator; the server refuses the rest,
 * and the route below it is hidden rather than shown-and-denied.
 */
export function InstanceSettingsPage() {
  const { user } = useAuth()
  const { instance } = useInstance()
  const queryClient = useQueryClient()
  const { notify, notifyError } = useToast()

  const settings = useQuery({
    queryKey: queryKeys.instance.settings,
    queryFn: instanceApi.settings,
  })
  const admins = useQuery({
    queryKey: queryKeys.instance.admins,
    queryFn: instanceApi.administrators,
  })

  const [form, setForm] = useState<InstanceSettingsPayload | null>(null)
  const [logoError, setLogoError] = useState<string | null>(null)
  const [addOpen, setAddOpen] = useState(false)

  // Seeded once the server answers, and re-seeded if it is refetched from
  // elsewhere — but never while the operator is mid-edit, which is why this keys
  // on the fetched object rather than running on every render.
  useEffect(() => {
    if (settings.data) {
      setForm(toForm(settings.data))
    }
  }, [settings.data])

  const save = useMutation({
    mutationFn: (payload: InstanceSettingsPayload) => instanceApi.update(payload),
    onSuccess: async (updated) => {
      queryClient.setQueryData(queryKeys.instance.settings, updated)
      // Branding is read from the public endpoint by every page, including the
      // header rendering this one.
      await queryClient.invalidateQueries({ queryKey: queryKeys.instance.public })
      notify('Instance settings saved')
    },
  })

  const setAdmin = useMutation({
    mutationFn: ({ userId, isAdmin }: { userId: string; isAdmin: boolean }) =>
      instanceApi.setInstanceAdmin(userId, isAdmin),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.instance.admins })
      notify('Administrators updated')
    },
    onError: (error) => notifyError(describeError(error, 'Could not change that.')),
  })

  if (settings.isPending) {
    return <LoadingState label="Loading instance settings" />
  }
  if (settings.error) {
    return (
      <ErrorState
        title="Could not load the instance settings"
        error={settings.error}
        onRetry={settings.refetch}
      />
    )
  }
  if (!form) {
    return <LoadingState label="Loading instance settings" />
  }

  const set = <K extends keyof InstanceSettingsPayload>(
    key: K,
    value: InstanceSettingsPayload[K],
  ) => setForm((current) => (current ? { ...current, [key]: value } : current))

  const fieldError = (field: string) =>
    save.error instanceof ApiError ? save.error.fieldError(field) : undefined

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    save.mutate(form)
  }

  const administrators = admins.data ?? []
  const soleAdministrator = administrators.length <= 1

  return (
    <div className="instance">
      <div className="page-header">
        <div>
          <p className="mono-label">Instance</p>
          <h1 className="page-header__title">Settings for this deployment</h1>
          <p className="page-header__subtitle">
            These are stored in the database, so they survive a redeploy and apply to everyone using{' '}
            {instance.name}.
          </p>
        </div>
        <div className="page-header__actions">
          <Link className="instance__back" to="/app">
            Back to workspaces
          </Link>
        </div>
      </div>

      {save.error ? (
        <p className="instance__error" role="alert">
          {describeError(save.error, 'Could not save the settings.')}
        </p>
      ) : null}

      <form className="instance__form" onSubmit={handleSubmit} noValidate>
        <section className="panel">
          <div className="panel__head">
            <h2 className="panel__title">Identity</h2>
            <p className="panel__note">What this instance is called, wherever it names itself.</p>
          </div>

          <div className="instance__preview">
            <InstanceMark
              name={form.name || 'Your instance'}
              logoMark={form.logoMark}
              logoImage={form.logoImage || null}
              accentColor={form.accentColor || undefined}
            />
          </div>

          <TextField
            label="Name"
            required
            value={form.name}
            error={fieldError('name')}
            onChange={(event) => set('name', event.target.value)}
          />
          <TextField
            label="Tagline"
            value={form.tagline}
            error={fieldError('tagline')}
            hint="Appears beside the name in the browser tab."
            onChange={(event) => set('tagline', event.target.value)}
          />
          <TextField
            label="Mark"
            maxLength={8}
            value={form.logoMark}
            error={fieldError('logoMark')}
            hint="A character or two shown beside the name."
            onChange={(event) => set('logoMark', event.target.value)}
          />

          <div className="field">
            <span className="field__label mono-label">Logo image</span>
            <input
              className="instance__file"
              type="file"
              accept="image/png,image/jpeg,image/svg+xml,image/webp"
              onChange={async (event) => {
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
              }}
            />
            <p className={logoError ? 'field__message field__message--error' : 'field__message'}>
              {logoError ??
                `Replaces the mark. Under ${Math.round(MAX_LOGO_BYTES / 1024)}KB — it is stored in the database.`}
            </p>
            {form.logoImage ? (
              <Button variant="ghost" size="sm" onClick={() => set('logoImage', '')}>
                Remove image
              </Button>
            ) : null}
          </div>

          <TextField
            label="Accent colour"
            type="text"
            placeholder="#0e6b73"
            value={form.accentColor}
            error={fieldError('accentColor')}
            hint="Six-digit hex. Used for links, primary actions, and active state. Leave empty for the default."
            onChange={(event) => set('accentColor', event.target.value)}
          />
        </section>

        <section className="panel">
          <div className="panel__head">
            <h2 className="panel__title">Access</h2>
            <p className="panel__note">Who can create an account on this instance.</p>
          </div>

          <SelectField
            label="Registration"
            value={form.registrationMode}
            error={fieldError('registrationMode')}
            onChange={(event) => set('registrationMode', event.target.value as RegistrationMode)}
          >
            <option value="OPEN">Open — anyone can sign up</option>
            <option value="RESTRICTED">Restricted — only listed email domains</option>
            <option value="CLOSED">Closed — you create every account</option>
          </SelectField>

          {form.registrationMode === 'RESTRICTED' ? (
            <TextAreaField
              label="Allowed email domains"
              rows={3}
              mono
              value={form.allowedEmailDomains}
              error={fieldError('allowedEmailDomains')}
              hint="One per line, or comma separated."
              onChange={(event) => set('allowedEmailDomains', event.target.value)}
            />
          ) : null}

          {form.registrationMode === 'CLOSED' ? (
            <p className="instance__aside">
              The sign-up form refuses everyone. Add people with <strong>Add operator</strong> below,
              or create their accounts there and clear the administrator box.
            </p>
          ) : null}
        </section>

        <section className="panel">
          <div className="panel__head">
            <h2 className="panel__title">Public documentation</h2>
            <p className="panel__note">
              Whether workspaces on this instance may be published as documentation sites that need
              no sign-in.
            </p>
          </div>

          <label className="instance__toggle">
            {/* Named explicitly: the visible label carries a paragraph of
                consequence after it, which would otherwise become part of the
                control's name. */}
            <input
              type="checkbox"
              aria-label="Allow public documentation"
              aria-describedby="public-docs-consequence"
              checked={form.publicDocsEnabled}
              onChange={(event) => set('publicDocsEnabled', event.target.checked)}
            />
            <span>
              <strong>Allow public documentation</strong>
              <span className="instance__toggle-hint" id="public-docs-consequence">
                Switching this off takes every published site offline at once, including the one
                below. Nothing is deleted — publishing works again the moment it is switched back
                on.
              </span>
            </span>
          </label>

          <TextField
            label="Handbook path"
            placeholder="handle/workspace-slug"
            value={form.handbookPath}
            error={fieldError('handbookPath')}
            hint="The published workspace /docs opens by default — an owner handle and a workspace slug. Leave empty to show the directory of everything published here."
            onChange={(event) => set('handbookPath', event.target.value)}
          />

          <TextField
            label="Public address"
            type="url"
            placeholder="https://docs.example.com"
            value={form.publicBaseUrl}
            error={fieldError('publicBaseUrl')}
            hint="Where this instance is reachable, for building absolute links. Never shown to visitors."
            onChange={(event) => set('publicBaseUrl', event.target.value)}
          />
        </section>

        <div className="instance__actions">
          <Button type="submit" loading={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save settings'}
          </Button>
          <Button
            variant="secondary"
            onClick={() => settings.data && setForm(toForm(settings.data))}
            disabled={save.isPending}
          >
            Discard changes
          </Button>
        </div>
      </form>

      <section className="panel">
        <div className="panel__head panel__head--row">
          <div>
            <h2 className="panel__title">Operators</h2>
            <p className="panel__note">
              Accounts that can change everything on this page. Appoint a second one — an instance
              whose only operator loses their password cannot be reconfigured.
            </p>
          </div>
          <Button size="sm" onClick={() => setAddOpen(true)}>
            Add operator
          </Button>
        </div>

        {admins.isPending ? <LoadingState label="Loading operators" /> : null}
        {admins.error ? <ErrorState error={admins.error} onRetry={admins.refetch} /> : null}

        {administrators.length > 0 ? (
          <ul className="operators">
            {administrators.map((operator) => (
              <li className="operator" key={operator.id}>
                <div className="operator__who">
                  <span className="operator__name">{operator.displayName}</span>
                  <span className="operator__handle">@{operator.handle}</span>
                </div>
                <span className="operator__email">{operator.email}</span>
                {operator.id === user?.id ? <Badge tone="trace">You</Badge> : null}
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={soleAdministrator || setAdmin.isPending}
                  onClick={() => setAdmin.mutate({ userId: operator.id, isAdmin: false })}
                >
                  {operator.id === user?.id ? 'Step down' : 'Remove'}
                </Button>
              </li>
            ))}
          </ul>
        ) : null}

        {soleAdministrator ? (
          <p className="instance__aside">
            Nobody can be removed until there is a second one.
          </p>
        ) : null}
      </section>

      {/* Mounted only while open. A closed <dialog> still holds its fields in the
          document, and this one's "Name" and "Email" would otherwise sit
          alongside the identical labels in the form above. */}
      {addOpen ? <AddOperatorDialog open onClose={() => setAddOpen(false)} /> : null}
    </div>
  )
}
