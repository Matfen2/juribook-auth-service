-- ═══════════════════════════════════════════════════════════
--  V1__create_users_table.sql - Schéma initial auth-service
-- ═══════════════════════════════════════════════════════════
-- ── Table users ─────────────────────────────────────────────
CREATE TABLE users (
    id            BIGSERIAL       PRIMARY KEY,
    name          VARCHAR(100)    NOT NULL,
    email         VARCHAR(255)    NOT NULL UNIQUE,
    phone         VARCHAR(20),
    password      VARCHAR(255)    NOT NULL,
    role          VARCHAR(10)     NOT NULL CHECK (role IN ('CLIENT', 'LAWYER', 'ADMIN')),
    enabled       BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Champs avocats (NULL pour CLIENT et ADMIN)
    bar_number    VARCHAR(20)     UNIQUE,
    specialty     VARCHAR(100),
    city          VARCHAR(100),
    lawyer_status VARCHAR(10)     CHECK (lawyer_status IN ('PENDING', 'APPROVED', 'REJECTED')),

    -- Audit
    created_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ── Contrainte métier ────────────────────────────────────────
-- Un avocat doit avoir un lawyer_status
-- Un client/admin ne doit pas en avoir
ALTER TABLE users
    ADD CONSTRAINT chk_lawyer_status
    CHECK (
        (role = 'LAWYER' AND lawyer_status IS NOT NULL)
        OR
        (role != 'LAWYER' AND lawyer_status IS NULL)
    );