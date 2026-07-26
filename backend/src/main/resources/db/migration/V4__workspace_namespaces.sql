-- Namespaces workspaces under the handle of the user who owns them.
--
-- Workspace slugs were unique across the whole instance, so the first team to take
-- "platform" or "nokia" blocked every other team from ever using that name. Public
-- documentation made that visible: the URL was /docs/{slug}, a single flat space.
--
-- Slugs are now unique per owner, and the public address carries the owner's
-- handle: /docs/{handle}/{slug}. Two teams may each have a "nokia".
--
-- In-app routes are unaffected: they address workspaces by id, not by slug.

-- --------------------------------------------------------------------------
-- 1. Give every user a handle.
-- --------------------------------------------------------------------------

ALTER TABLE users
    ADD COLUMN handle VARCHAR(39);

-- Derived from the local part of the address: the closest thing to a name the
-- account already has. Truncated before de-duplication so the numeric suffix
-- cannot itself be cut off, and de-duplicated deterministically by id.
WITH candidate AS (
    SELECT id,
           COALESCE(
               NULLIF(
                   left(
                       trim(both '-' from
                           regexp_replace(lower(split_part(email, '@', 1)), '[^a-z0-9]+', '-', 'g')
                       ),
                       32
                   ),
                   ''
               ),
               'user'
           ) AS base
    FROM users
),
numbered AS (
    SELECT id, base, row_number() OVER (PARTITION BY base ORDER BY id) AS position
    FROM candidate
)
UPDATE users u
SET handle = CASE WHEN n.position = 1 THEN n.base ELSE n.base || '-' || n.position END
FROM numbered n
WHERE n.id = u.id;

ALTER TABLE users
    ALTER COLUMN handle SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT users_handle_key UNIQUE (handle),
    ADD CONSTRAINT chk_users_handle CHECK (handle ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$');

-- --------------------------------------------------------------------------
-- 2. Give every workspace an owning user, which is its namespace.
-- --------------------------------------------------------------------------

ALTER TABLE workspaces
    ADD COLUMN owner_user_id UUID;

-- The earliest OWNER, falling back to the earliest member of any role. Creating a
-- workspace enrols its creator as OWNER, so in practice this is the creator.
UPDATE workspaces w
SET owner_user_id = (
    SELECT m.user_id
    FROM workspace_members m
    WHERE m.workspace_id = w.id
    ORDER BY (m.role = 'OWNER') DESC, m.created_at ASC
    LIMIT 1
);

-- Fails loudly rather than silently if a workspace somehow has no members at all,
-- because such a workspace is unreachable and its namespace undefined.
ALTER TABLE workspaces
    ALTER COLUMN owner_user_id SET NOT NULL;

-- RESTRICT, not CASCADE: deleting a user must not silently take their team's
-- workspaces with them. Reassigning the namespace has to be a deliberate act.
ALTER TABLE workspaces
    ADD CONSTRAINT fk_workspaces_owner
        FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT;

-- --------------------------------------------------------------------------
-- 3. Scope slug uniqueness to the namespace.
-- --------------------------------------------------------------------------

ALTER TABLE workspaces
    DROP CONSTRAINT workspaces_slug_key;

ALTER TABLE workspaces
    ADD CONSTRAINT workspaces_owner_slug_key UNIQUE (owner_user_id, slug);

-- Resolves /docs/{handle}/{slug}: published workspaces looked up by owner and slug.
DROP INDEX idx_workspaces_published;
CREATE INDEX idx_workspaces_published ON workspaces(owner_user_id, slug)
    WHERE published_at IS NOT NULL;
