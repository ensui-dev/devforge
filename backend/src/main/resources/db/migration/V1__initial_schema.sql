-- DevForge initial schema.
--
-- Conventions applied consistently across every table:
--   * UUID primary keys assigned by the application
--   * created_at / updated_at audit columns (mapped by BaseEntity)
--   * version column for JPA optimistic locking (mapped by BaseEntity)
--
-- Cross-module references (documents -> workspaces, tasks -> documents) are
-- enforced as foreign keys here, but are NOT mapped as JPA associations. Each
-- module holds only the UUID, so the object model does not couple modules.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    display_name  VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE workspaces (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT NOT NULL DEFAULT 0
);

-- Team membership. A workspace is only reachable by its members; the creator is
-- enrolled as OWNER at creation time.
CREATE TABLE workspace_members (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role         VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version      BIGINT NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_workspace_members_workspace ON workspace_members(workspace_id);
CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);

CREATE TABLE documents (
    id            UUID PRIMARY KEY,
    workspace_id  UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    title         VARCHAR(500) NOT NULL,
    slug          VARCHAR(200) NOT NULL,
    content       TEXT NOT NULL DEFAULT '',
    document_type VARCHAR(50) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    -- Maintained by PostgreSQL so search can never drift from the content.
    -- to_tsvector with a literal config is IMMUTABLE, so it is generated-safe.
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
    ) STORED,
    UNIQUE (workspace_id, slug)
);

CREATE INDEX idx_documents_workspace ON documents(workspace_id);
CREATE INDEX idx_documents_type ON documents(document_type);
CREATE INDEX idx_documents_search ON documents USING GIN (search_vector);

-- Typed graph edges between documents. Direction matters, so the uniqueness key
-- includes the type: A DEPENDS_ON B and A RELATED B may coexist.
CREATE TABLE document_references (
    id                 UUID PRIMARY KEY,
    source_document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    target_document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    reference_type     VARCHAR(50) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version            BIGINT NOT NULL DEFAULT 0,
    UNIQUE (source_document_id, target_document_id, reference_type),
    CONSTRAINT chk_document_reference_not_self CHECK (source_document_id <> target_document_id)
);

CREATE INDEX idx_doc_refs_source ON document_references(source_document_id);
CREATE INDEX idx_doc_refs_target ON document_references(target_document_id);

CREATE TABLE boards (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_boards_workspace ON boards(workspace_id);

-- position is deliberately NOT unique per board. Reordering rewrites a
-- contiguous run of positions within one transaction, and a unique constraint
-- would reject the legal intermediate states unless it were DEFERRABLE.
CREATE TABLE board_columns (
    id         UUID PRIMARY KEY,
    board_id   UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    position   INT NOT NULL,
    wip_limit  INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_board_column_position CHECK (position >= 0),
    CONSTRAINT chk_board_column_wip_limit CHECK (wip_limit IS NULL OR wip_limit > 0)
);

CREATE INDEX idx_board_columns_board ON board_columns(board_id, position);

CREATE TABLE tasks (
    id          UUID PRIMARY KEY,
    board_id    UUID NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    column_id   UUID NOT NULL REFERENCES board_columns(id) ON DELETE CASCADE,
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    title       VARCHAR(500) NOT NULL,
    description TEXT,
    position    INT NOT NULL,
    priority    VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_task_position CHECK (position >= 0)
);

CREATE INDEX idx_tasks_board ON tasks(board_id);
CREATE INDEX idx_tasks_column ON tasks(column_id, position);
CREATE INDEX idx_tasks_assignee ON tasks(assignee_id);

-- A task may cite many documents (spec, ADR, runbook), and a document may be
-- cited by many tasks. This is the join that keeps delivery work attached to
-- source knowledge instead of duplicating it.
CREATE TABLE task_document_links (
    id          UUID PRIMARY KEY,
    task_id     UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (task_id, document_id)
);

CREATE INDEX idx_task_document_links_task ON task_document_links(task_id);
CREATE INDEX idx_task_document_links_document ON task_document_links(document_id);
