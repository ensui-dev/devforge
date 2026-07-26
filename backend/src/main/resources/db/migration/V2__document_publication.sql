-- Lets any workspace publish its documentation as a public site.
--
-- Before this, exactly one workspace could be served publicly, named by the
-- devforge.handbook.slug property. That property keeps its narrower meaning —
-- which published workspace is *this instance's* own product documentation — while
-- publication itself becomes something a workspace admin controls.

-- A timestamp rather than a boolean: knowing *when* a workspace went public is
-- worth keeping, and NULL is an unambiguous "private".
ALTER TABLE workspaces
    ADD COLUMN published_at TIMESTAMPTZ;

-- Partial index: only published workspaces are ever looked up by this, and there
-- will be far fewer of them than private ones.
CREATE INDEX idx_workspaces_published ON workspaces(slug) WHERE published_at IS NOT NULL;

-- Holds a page back from the public site even while its workspace is published.
-- Defaults to false, so publishing a workspace exposes its existing pages; the
-- application makes that state loudly visible rather than implicit.
ALTER TABLE documents
    ADD COLUMN internal BOOLEAN NOT NULL DEFAULT FALSE;

-- Supports the public table of contents, which reads only non-internal pages.
CREATE INDEX idx_documents_public ON documents(workspace_id, title)
    WHERE internal = FALSE;
