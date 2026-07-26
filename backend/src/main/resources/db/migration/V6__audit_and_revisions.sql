-- Attribution and history.
--
-- Until now a change left only `updated_at` and `version` behind: you could tell
-- that something moved, never who moved it or what it said before. Two tables
-- close that gap — one records that a change happened and who made it, the other
-- retains what documents actually said.

-- ---------------------------------------------------------------------------
-- Audit events
-- ---------------------------------------------------------------------------
-- Append-only. Nothing in the application updates or deletes a row here, which
-- is what makes the log worth consulting.
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- The account responsible. Nullable because some events have no signed-in
    -- actor: first-run setup happens before any account exists.
    actor_id        UUID REFERENCES users (id) ON DELETE SET NULL,
    -- Denormalised so the log still reads correctly after an account is deleted
    -- or renamed. An audit trail that rewrites itself when history changes is
    -- not an audit trail.
    actor_label     VARCHAR(320),

    action          VARCHAR(48) NOT NULL,
    target_type     VARCHAR(32) NOT NULL,
    target_id       UUID,
    -- Likewise denormalised: the name the target had at the time.
    target_label    VARCHAR(255),

    -- Scopes the event to a workspace so a team can read its own history without
    -- being shown the rest of the instance. NULL means instance-level.
    --
    -- Deliberately NOT a foreign key, for two reasons that point the same way:
    --
    --   1. An audit row must outlive its subject. With ON DELETE CASCADE, deleting
    --      a workspace would delete the evidence that it was deleted.
    --   2. Rows are written in their own transaction, so that an attempt is
    --      recorded even if the change it describes is later rolled back. That
    --      transaction cannot see a workspace the caller has not committed yet, so
    --      a foreign key would reject the very first event about a new workspace.
    workspace_id    UUID,

    -- Small JSON payload for the specifics: which role, which field, old and new
    -- values. Deliberately unstructured — the shape differs per action, and
    -- forcing a column per variant would make every new action a migration.
    detail          JSONB
);

-- The two access patterns: one workspace's history, and the whole instance's.
CREATE INDEX idx_audit_workspace_time ON audit_events (workspace_id, occurred_at DESC)
    WHERE workspace_id IS NOT NULL;
CREATE INDEX idx_audit_time ON audit_events (occurred_at DESC);
CREATE INDEX idx_audit_actor ON audit_events (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_target ON audit_events (target_type, target_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- Document content, addressed by hash
-- ---------------------------------------------------------------------------
-- Borrowed from git, which stores each distinct blob once under the hash of its
-- content. Git's model is snapshots rather than deltas — deltas are a packfile
-- concern applied later — and snapshots are what this keeps. What the hash buys
-- is that identical content is stored once however many revisions share it.
--
-- That is not a hypothetical saving here: restoring a revision produces content
-- byte-identical to one already stored, so every restore would otherwise
-- duplicate a whole document body. Reverting an edit by hand does the same.
--
-- One deliberate divergence: git's object store is global, which is why git needs
-- `gc` to collect blobs nothing points at any more. Scoping the store to a single
-- document means deletion cascades and there is nothing to collect — and
-- duplication happens *within* one document's history anyway, which is where the
-- dedup applies.
--
-- Compression is left to PostgreSQL: TEXT over ~2KB goes to TOAST, which
-- compresses it. That is the layer git implements as packfile zlib.
CREATE TABLE document_contents (
    id              UUID PRIMARY KEY,
    document_id     UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    -- SHA-256 of the body, hex encoded. VARCHAR rather than CHAR: CHAR
    -- blank-pads on comparison, which is not a property you want on a hash.
    content_hash    VARCHAR(64) NOT NULL,
    body            TEXT NOT NULL,

    CONSTRAINT uq_document_content UNIQUE (document_id, content_hash)
);

-- ---------------------------------------------------------------------------
-- Document revisions
-- ---------------------------------------------------------------------------
-- Metadata per revision, with the body referenced by hash. Reading one revision
-- is still a single indexed lookup; nothing has to be replayed.
CREATE TABLE document_revisions (
    id              UUID PRIMARY KEY,
    document_id     UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,

    -- 1-based and contiguous per document, so a reader can say "revision 4"
    -- without exposing an internal id.
    revision        INTEGER NOT NULL,

    title           VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    document_type   VARCHAR(32) NOT NULL,
    internal        BOOLEAN NOT NULL DEFAULT FALSE,

    -- Why this revision exists: CREATED, UPDATED, or RESTORED.
    reason          VARCHAR(16) NOT NULL,
    -- When a restore produced this revision, the one it was taken from.
    restored_from   INTEGER,

    author_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    author_label    VARCHAR(320),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_document_revision UNIQUE (document_id, revision),
    CONSTRAINT chk_revision_positive CHECK (revision >= 1),
    -- Points at a body that exists, for this document. A composite foreign key to
    -- a unique constraint, so integrity holds without a surrogate join column.
    CONSTRAINT fk_revision_content FOREIGN KEY (document_id, content_hash)
        REFERENCES document_contents (document_id, content_hash) ON DELETE CASCADE
);

CREATE INDEX idx_revisions_document ON document_revisions (document_id, revision DESC);

-- ---------------------------------------------------------------------------
-- Backfill
-- ---------------------------------------------------------------------------
-- Every existing document gets revision 1 from its current state, so history is
-- never empty and "restore" has something to restore to on day one. The author
-- is unknown — nothing recorded it at the time — and saying so is better than
-- attributing it to whoever happens to be earliest in the users table.
-- Content first, so the revisions have something to point at.
INSERT INTO document_contents (id, document_id, content_hash, body)
SELECT gen_random_uuid(), d.id, encode(sha256(d.content::bytea), 'hex'), d.content
FROM documents d;

INSERT INTO document_revisions (
    id, document_id, revision, title, slug, content_hash, document_type, internal,
    reason, author_id, author_label, created_at
)
SELECT
    gen_random_uuid(), d.id, 1, d.title, d.slug,
    encode(sha256(d.content::bytea), 'hex'), d.document_type, d.internal,
    'CREATED', NULL, NULL, d.created_at
FROM documents d;
