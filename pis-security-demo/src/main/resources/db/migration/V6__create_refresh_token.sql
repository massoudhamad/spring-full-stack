-- Refresh tokens: long-lived, so they must be revocable, so they live in the database.
--
-- token_hash is SHA-256 of the token, never the token itself: a leaked table
-- must not hand out working tokens.
-- family_id links every token produced by rotation from one login. If a token
-- that was already exchanged is presented again, the whole family is revoked.

CREATE TABLE refresh_token (
    id          UUID          PRIMARY KEY,
    token_hash  VARCHAR(64)   NOT NULL,
    user_id     UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id   UUID          NOT NULL,
    expires_at  TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    used_at     TIMESTAMPTZ,              -- exchanged for a new pair (rotation)
    revoked_at  TIMESTAMPTZ,              -- logout, disabled account, or reuse detected
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
