import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../shared/api/client'
import { syncApi } from '../../shared/api/endpoints'
import { queryKeys } from '../../shared/api/queryKeys'
import { Badge } from '../../shared/components/Badge'
import { Button } from '../../shared/components/Button'
import { SelectField, TextField } from '../../shared/components/Field'
import { ErrorState, LoadingState } from '../../shared/components/Feedback'
import { Modal } from '../../shared/components/Modal'
import { describeError } from '../../shared/components/describeError'
import { useToast } from '../../shared/components/useToast'
import { useInstance } from '../../shared/instance/useInstance'
import { formatRelative } from '../../shared/utils/slugify'
import type { DeletionPolicy, DocumentType, SyncSettings } from '../../shared/types'
import './GitSyncPanel.css'

const DOCUMENT_TYPES: DocumentType[] = [
  'GENERAL', 'CODE', 'PROCEDURE', 'TECHNOLOGY', 'TECH_STACK',
  'ARCHITECTURE', 'API', 'RUNBOOK', 'DECISION',
]

const POLICY_LABELS: Record<DeletionPolicy, string> = {
  ARCHIVE: 'Mark it internal — withdrawn from published docs, history kept',
  DELETE: 'Delete it, along with its history',
  IGNORE: 'Leave it alone — the repository holds only some of the documentation',
}

interface FormState {
  repositoryUrl: string
  branch: string
  documentPath: string
  defaultType: DocumentType
  deletionPolicy: DeletionPolicy
  enabled: boolean
  /** Empty means "no change"; the form cannot echo a stored token back. */
  accessToken: string
}

function toForm(settings: SyncSettings): FormState {
  return {
    repositoryUrl: settings.repositoryUrl,
    branch: settings.branch || 'main',
    documentPath: settings.documentPath,
    defaultType: settings.defaultType,
    deletionPolicy: settings.deletionPolicy,
    enabled: settings.enabled,
    accessToken: '',
  }
}

/**
 * Points a workspace's documentation at a git repository.
 *
 * <p>Two triggers on purpose. A webhook keeps the workspace current, but nobody
 * should have to push a commit to find out whether their settings are right — so
 * "Sync now" runs one immediately and reports exactly what it did.
 */
export function GitSyncPanel({ workspaceId }: { workspaceId: string }) {
  const queryClient = useQueryClient()
  const { notify, notifyError } = useToast()
  const { instance } = useInstance()

  const [form, setForm] = useState<FormState | null>(null)
  const [confirmDisconnect, setConfirmDisconnect] = useState(false)
  /** Held in memory only: the server will never return it again. */
  const [freshSecret, setFreshSecret] = useState<string | null>(null)

  const settings = useQuery({
    queryKey: queryKeys.sync.settings(workspaceId),
    queryFn: () => syncApi.get(workspaceId),
  })

  useEffect(() => {
    if (settings.data) {
      setForm(toForm(settings.data))
    }
  }, [settings.data])

  const store = (updated: SyncSettings) => {
    queryClient.setQueryData(queryKeys.sync.settings(workspaceId), updated)
  }

  const save = useMutation({
    mutationFn: (state: FormState) =>
      syncApi.save(workspaceId, {
        repositoryUrl: state.repositoryUrl.trim(),
        branch: state.branch.trim(),
        documentPath: state.documentPath.trim(),
        defaultType: state.defaultType,
        deletionPolicy: state.deletionPolicy,
        enabled: state.enabled,
        // Only sent when the operator typed something, so saving other settings
        // does not wipe a stored token.
        ...(state.accessToken ? { accessToken: state.accessToken } : {}),
      }),
    onSuccess: (updated) => {
      store(updated)
      setForm(toForm(updated))
      notify('Sync settings saved')
    },
  })

  const run = useMutation({
    mutationFn: () => syncApi.run(workspaceId),
    onSuccess: async (updated) => {
      store(updated)
      // Documents may have appeared, changed, or been withdrawn.
      await queryClient.invalidateQueries({ queryKey: queryKeys.documents.all(workspaceId) })
      notify(
        updated.lastStatus === 'FAILED'
          ? 'Sync failed — see the details below'
          : `Sync finished: ${updated.lastMessage}`,
      )
    },
    onError: (error) => notifyError(describeError(error, 'Could not run the sync.')),
  })

  const generateSecret = useMutation({
    mutationFn: () => syncApi.generateSecret(workspaceId),
    onSuccess: async ({ webhookSecret }) => {
      setFreshSecret(webhookSecret)
      await queryClient.invalidateQueries({ queryKey: queryKeys.sync.settings(workspaceId) })
    },
    onError: (error) => notifyError(describeError(error, 'Could not generate a secret.')),
  })

  const rotateUrl = useMutation({
    mutationFn: () => syncApi.rotateUrl(workspaceId),
    onSuccess: (updated) => {
      store(updated)
      notify('New webhook URL generated — update it in your git host')
    },
  })

  const disconnect = useMutation({
    mutationFn: () => syncApi.disconnect(workspaceId),
    onSuccess: async () => {
      setConfirmDisconnect(false)
      setFreshSecret(null)
      await queryClient.invalidateQueries({ queryKey: queryKeys.sync.settings(workspaceId) })
      notify('Repository disconnected')
    },
  })

  if (settings.isPending) {
    return <LoadingState label="Loading sync settings" />
  }
  if (settings.error) {
    return <ErrorState title="Could not load the sync settings" error={settings.error} onRetry={settings.refetch} />
  }
  if (!settings.data || !form) {
    return <LoadingState label="Loading sync settings" />
  }

  const current = settings.data
  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((state) => (state ? { ...state, [key]: value } : state))

  const fieldError = (field: string) =>
    save.error instanceof ApiError ? save.error.fieldError(field) : undefined

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    save.mutate(form)
  }

  // The webhook path is instance-relative; a git host needs the whole address.
  const absoluteWebhookUrl = current.webhookUrl
    ? `${typeof window === 'undefined' ? '' : window.location.origin}${current.webhookUrl}`
    : null

  return (
    <section className="panel gitsync">
      <div className="panel__head">
        <h2 className="panel__title">Sync from git</h2>
        <p className="panel__note">
          Write documentation as markdown in a repository and let {instance.name} follow it. Files
          become documents; a file that disappears is withdrawn. Edits made here are never lost —
          the repository wins, and the previous version stays in the document's history.
        </p>
        <p className="panel__note">
          Typed links can be declared in front matter too, so the reference graph is reviewed in a
          pull request along with the change that made it true:{' '}
          <code>depends_on: kafka-conventions, event-schema</code>. A file that declares none keeps
          whatever links were made here.
        </p>
      </div>

      {save.error && !(save.error instanceof ApiError && Object.keys(save.error.fieldErrors).length) ? (
        <p className="form-error" role="alert">
          {describeError(save.error, 'Could not save the sync settings.')}
        </p>
      ) : null}

      <form className="gitsync__form" onSubmit={handleSubmit} noValidate>
        <TextField
          label="Repository URL"
          type="url"
          required
          placeholder="https://github.com/you/handbook"
          value={form.repositoryUrl}
          error={fieldError('repositoryUrl')}
          hint="The web address of the repository. GitHub, GitLab, Gitea, and Forgejo all work."
          onChange={(event) => set('repositoryUrl', event.target.value)}
        />

        <div className="gitsync__row">
          <TextField
            label="Branch"
            value={form.branch}
            error={fieldError('branch')}
            onChange={(event) => set('branch', event.target.value)}
          />
          <TextField
            label="Documentation folder"
            placeholder="docs"
            value={form.documentPath}
            error={fieldError('documentPath')}
            hint="Leave empty to use the whole repository."
            onChange={(event) => set('documentPath', event.target.value)}
          />
        </div>

        <SelectField
          label="Default document type"
          value={form.defaultType}
          onChange={(event) => set('defaultType', event.target.value as DocumentType)}
          hint="Applied to files that do not set a type in their front matter."
        >
          {DOCUMENT_TYPES.map((type) => (
            <option key={type} value={type}>
              {type.replace('_', ' ')}
            </option>
          ))}
        </SelectField>

        <SelectField
          label="When a file is deleted upstream"
          value={form.deletionPolicy}
          onChange={(event) => set('deletionPolicy', event.target.value as DeletionPolicy)}
        >
          {(Object.keys(POLICY_LABELS) as DeletionPolicy[]).map((policy) => (
            <option key={policy} value={policy}>
              {POLICY_LABELS[policy]}
            </option>
          ))}
        </SelectField>

        <TextField
          label={current.hasAccessToken ? 'Replace access token' : 'Access token'}
          type="password"
          autoComplete="off"
          placeholder={current.hasAccessToken ? 'A token is stored — type to replace it' : ''}
          value={form.accessToken}
          error={fieldError('accessToken')}
          hint="Only needed for a private repository. Read access is enough. Stored encrypted."
          onChange={(event) => set('accessToken', event.target.value)}
        />

        <label className="gitsync__toggle">
          <input
            type="checkbox"
            aria-label="Syncing enabled"
            aria-describedby="gitsync-enabled-hint"
            checked={form.enabled}
            onChange={(event) => set('enabled', event.target.checked)}
          />
          <span>
            <strong>Syncing enabled</strong>
            <span className="gitsync__hint" id="gitsync-enabled-hint">
              Switch this off to stop webhook deliveries being applied without forgetting the
              settings.
            </span>
          </span>
        </label>

        <div className="gitsync__actions">
          <Button type="submit" loading={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save settings'}
          </Button>
          {current.configured ? (
            <Button variant="secondary" loading={run.isPending} onClick={() => run.mutate()}>
              {run.isPending ? 'Syncing…' : 'Sync now'}
            </Button>
          ) : null}
        </div>
      </form>

      {current.configured ? (
        <>
          <div className="gitsync__divider" />

          <div className="gitsync__webhook">
            <h3 className="gitsync__subtitle">Keep it up to date automatically</h3>
            <p className="gitsync__hint">
              Add this as a webhook in your git host, with content type <code>application/json</code>,
              and paste the secret below into its secret field. Every push then syncs.
            </p>

            <label className="gitsync__url">
              <span className="mono-label">Webhook URL</span>
              <input className="field__control" readOnly value={absoluteWebhookUrl ?? ''} />
            </label>

            {freshSecret ? (
              <div className="gitsync__secret" role="status">
                <p className="mono-label">Webhook secret — copy it now</p>
                <code className="gitsync__secret-value">{freshSecret}</code>
                <p className="gitsync__hint">
                  This is the only time it can be read. It is stored encrypted, so nothing can show
                  it again — generate a new one if you lose it.
                </p>
              </div>
            ) : null}

            <div className="gitsync__actions">
              <Button
                variant="secondary"
                size="sm"
                loading={generateSecret.isPending}
                onClick={() => generateSecret.mutate()}
              >
                {current.hasWebhookSecret ? 'Generate a new secret' : 'Generate secret'}
              </Button>
              <Button
                variant="ghost"
                size="sm"
                loading={rotateUrl.isPending}
                onClick={() => rotateUrl.mutate()}
              >
                Change the URL
              </Button>
            </div>

            {!current.hasWebhookSecret ? (
              <p className="gitsync__warning">
                Until a secret is generated, webhook deliveries are refused — a delivery that cannot
                be verified is not accepted.
              </p>
            ) : null}
          </div>

          <div className="gitsync__divider" />

          <section className="gitsync__status" aria-label="Last sync">
            <h3 className="gitsync__subtitle">Last sync</h3>
            {current.lastAttemptedAt ? (
              <>
                <p className="gitsync__outcome">
                  <StatusBadge status={current.lastStatus} />{' '}
                  <span className="gitsync__hint">
                    {formatRelative(current.lastAttemptedAt)}
                    {current.lastRef ? ` · ${current.lastRef}` : ''}
                  </span>
                </p>
                {current.lastMessage ? (
                  <p className="gitsync__message">{current.lastMessage}</p>
                ) : null}
                {current.lastStatus === 'FAILED' && current.lastSucceededAt ? (
                  <p className="gitsync__hint">
                    Last succeeded {formatRelative(current.lastSucceededAt)}.
                  </p>
                ) : null}
                {current.problems.length > 0 ? (
                  <ul className="gitsync__problems">
                    {current.problems.map((problem) => (
                      <li key={problem}>{problem}</li>
                    ))}
                  </ul>
                ) : null}
              </>
            ) : (
              <p className="gitsync__hint">
                Nothing yet. Press <strong>Sync now</strong> to try the settings.
              </p>
            )}
          </section>

          <div className="gitsync__divider" />

          <div className="gitsync__danger">
            <div>
              <p className="gitsync__subtitle">Disconnect</p>
              <p className="gitsync__hint">
                Forgets the repository and its stored credentials. Documentation it already brought
                in stays where it is.
              </p>
            </div>
            <Button variant="danger" size="sm" onClick={() => setConfirmDisconnect(true)}>
              Disconnect
            </Button>
          </div>
        </>
      ) : null}

      <Modal
        title="Disconnect this repository?"
        open={confirmDisconnect}
        onClose={() => setConfirmDisconnect(false)}
        width="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDisconnect(false)}>
              Keep it
            </Button>
            <Button variant="danger" loading={disconnect.isPending} onClick={() => disconnect.mutate()}>
              Disconnect
            </Button>
          </>
        }
      >
        <p>
          The repository URL, its access token, and its webhook secret are forgotten, and the webhook
          URL stops working. The documents already synced are not touched.
        </p>
      </Modal>
    </section>
  )
}

function StatusBadge({ status }: { status: SyncSettings['lastStatus'] }) {
  if (status === 'OK') {
    return <Badge tone="success">Succeeded</Badge>
  }
  if (status === 'PARTIAL') {
    return <Badge tone="signal">Partly applied</Badge>
  }
  if (status === 'FAILED') {
    return <Badge tone="danger">Failed</Badge>
  }
  return <Badge tone="neutral">Not run</Badge>
}
