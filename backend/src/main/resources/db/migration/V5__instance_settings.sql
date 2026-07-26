-- Makes an instance configurable by whoever runs it, rather than by whoever built it.
--
-- DevForge is self-hosted: the name, the branding, who may register, and whether
-- documentation may be published are decisions for the operator, not constants in
-- a properties file. They live in the database so they can be changed from the
-- application without a redeploy.

CREATE TABLE instance_settings (
    -- A single row. The constant primary key makes that structural rather than a
    -- convention someone has to remember.
    id                 BOOLEAN PRIMARY KEY DEFAULT TRUE,

    -- Identity
    name               VARCHAR(80)  NOT NULL,
    tagline            VARCHAR(200),
    logo_mark          VARCHAR(8)   NOT NULL DEFAULT '⌁',
    -- Optional image, held inline as a data URI so a self-hosted instance needs no
    -- object storage or CDN to have a logo.
    logo_image         TEXT,
    accent_color       VARCHAR(7),

    -- Who may create an account.
    --   OPEN       anyone
    --   RESTRICTED only addresses in allowed_email_domains
    --   CLOSED     nobody; an instance admin creates accounts
    registration_mode  VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    allowed_email_domains TEXT,

    -- Whether workspaces on this instance may publish documentation publicly, and
    -- which published workspace /docs opens by default, as "handle/slug".
    public_docs_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    handbook_path      VARCHAR(210),

    -- Where this instance is reachable, for absolute links.
    public_base_url    VARCHAR(200),

    -- Null until the setup wizard completes. Its presence is what closes the
    -- one-shot bootstrap endpoint, so an unconfigured instance cannot be claimed
    -- twice.
    setup_completed_at TIMESTAMPTZ,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_instance_singleton CHECK (id IS TRUE),
    CONSTRAINT chk_instance_registration_mode
        CHECK (registration_mode IN ('OPEN', 'RESTRICTED', 'CLOSED')),
    CONSTRAINT chk_instance_accent
        CHECK (accent_color IS NULL OR accent_color ~ '^#[0-9a-f]{6}$')
);

-- Instance administration is separate from workspace roles: an instance admin
-- configures the deployment, and has no special access to anyone's content.
ALTER TABLE users
    ADD COLUMN instance_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- --------------------------------------------------------------------------
-- Existing instances
-- --------------------------------------------------------------------------

-- An instance that already has users predates the setup wizard, so it is treated
-- as already configured rather than being sent back through it. The defaults
-- match how it behaved before this migration.
INSERT INTO instance_settings (
    id, name, tagline, registration_mode, public_docs_enabled,
    handbook_path, setup_completed_at
)
SELECT
    TRUE,
    'DevForge',
    'Documentation and delivery, connected.',
    'OPEN',
    TRUE,
    'handbook/devforge-handbook',
    CASE WHEN EXISTS (SELECT 1 FROM users) THEN NOW() ELSE NULL END;

-- The earliest account bootstrapped this instance, so it becomes the first
-- instance admin. Operators can promote others afterwards; a fresh instance skips
-- this entirely because the setup wizard names its own admin.
UPDATE users
SET instance_admin = TRUE
WHERE id = (SELECT id FROM users ORDER BY created_at ASC LIMIT 1);
