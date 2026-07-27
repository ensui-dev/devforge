-- Credentials for git over HTTP.
--
-- Git authenticates with HTTP Basic and has no notion of a session, so the
-- 12-hour access token the application issues is unusable as a password: it would
-- expire mid-afternoon and the remedy would be to sign in again somewhere else.
CREATE TABLE git_access_tokens (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL,

    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- What the owner called it, so revoking the right one is possible.
    name            VARCHAR(120) NOT NULL,

    -- SHA-256 of the token, hex. Not bcrypt: the token is 256 random bits, so
    -- there is nothing to brute-force and no reason to pay a work factor on every
    -- git request — a clone makes several. Work factors exist for passwords, which
    -- are low-entropy; they buy nothing here.
    --
    -- A plain digest is also directly indexable, so verifying is one lookup rather
    -- than a scan of a user's tokens comparing salted hashes.
    -- VARCHAR, not CHAR: CHAR blank-pads on comparison, which is not a property
    -- you want on a hash. The same mistake cost a migration in V6.
    token_hash      VARCHAR(64) NOT NULL UNIQUE,

    -- The leading characters, shown so an owner can tell their tokens apart
    -- without any of them being recoverable.
    token_hint      VARCHAR(16) NOT NULL,

    last_used_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ
);

CREATE INDEX idx_git_tokens_user ON git_access_tokens (user_id);
