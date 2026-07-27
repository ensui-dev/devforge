-- Documentation synced from a git repository.
--
-- One configuration per workspace: a workspace is the unit of documentation, with
-- its own owner and members, so that is the natural place to point at a repository.
-- An instance-level connection would mean registering an application with a git
-- host before anyone could self-host, which cuts against the point of the project.

CREATE TABLE sync_configurations (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL,

    -- One per workspace. Deleting the workspace takes its configuration with it,
    -- including the stored credentials.
    workspace_id        UUID NOT NULL UNIQUE REFERENCES workspaces (id) ON DELETE CASCADE,

    repository_url      VARCHAR(500) NOT NULL,
    branch              VARCHAR(255) NOT NULL DEFAULT 'main',
    -- Subdirectory holding the markdown, '' for the repository root.
    document_path       VARCHAR(500) NOT NULL DEFAULT '',
    -- Applied to files that do not declare a type in their front matter.
    default_type        VARCHAR(32) NOT NULL DEFAULT 'GENERAL',

    -- What happens to a document whose file has disappeared upstream.
    -- ARCHIVE marks it internal, DELETE removes it, IGNORE leaves it alone.
    deletion_policy     VARCHAR(16) NOT NULL DEFAULT 'ARCHIVE',

    -- Encrypted with AES-GCM, keyed from the instance signing secret. A leaked
    -- database dump therefore does not hand over live third-party credentials.
    -- Rotating DEVFORGE_JWT_SECRET makes these unreadable, which surfaces as
    -- "reconnect the repository" rather than as a failure to start.
    access_token        TEXT,
    webhook_secret      TEXT,

    -- Unguessable path segment, so the webhook URL is not derivable from the
    -- workspace id it belongs to. Rotating it invalidates the old URL.
    webhook_id          UUID NOT NULL UNIQUE,

    enabled             BOOLEAN NOT NULL DEFAULT TRUE,

    -- The outcome of the last attempt. Denormalised onto the configuration rather
    -- than kept as a history table: an operator wants to know whether it is
    -- working now, and every attempt already leaves entries in the audit log.
    last_attempted_at   TIMESTAMPTZ,
    last_succeeded_at   TIMESTAMPTZ,
    last_ref            VARCHAR(255),
    last_status         VARCHAR(16),
    last_message        TEXT,
    last_created        INTEGER NOT NULL DEFAULT 0,
    last_updated        INTEGER NOT NULL DEFAULT 0,
    last_archived       INTEGER NOT NULL DEFAULT 0,
    last_unchanged      INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT chk_sync_deletion_policy
        CHECK (deletion_policy IN ('ARCHIVE', 'DELETE', 'IGNORE')),
    CONSTRAINT chk_sync_status
        CHECK (last_status IS NULL OR last_status IN ('OK', 'PARTIAL', 'FAILED'))
);

-- The webhook arrives knowing only its own id, so that is the lookup path.
CREATE INDEX idx_sync_webhook ON sync_configurations (webhook_id);
