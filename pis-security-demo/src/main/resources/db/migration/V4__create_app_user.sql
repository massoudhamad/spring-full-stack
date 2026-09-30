-- Users who can log in, and the roles each one holds.
--
-- The table is app_user, not user: USER is a reserved word in PostgreSQL.
-- password_hash holds a BCrypt hash with its {bcrypt} prefix, never a password.

CREATE TABLE app_user (
    id            UUID          PRIMARY KEY,
    username      VARCHAR(50)   NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    full_name     VARCHAR(150)  NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ,
    created_by    VARCHAR(50),
    updated_by    VARCHAR(50),
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

-- One row per role. A composite key stops the same role being granted twice.
CREATE TABLE app_user_role (
    user_id UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role)
);
