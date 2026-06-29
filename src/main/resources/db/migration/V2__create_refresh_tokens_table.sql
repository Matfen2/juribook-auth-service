-- ═══════════════════════════════════════════════════════════
--  V2__create_refresh_tokens_table.sql
--  Table des refresh tokens : gestion du renouvellement JWT
--  Ne jamais modifier ce fichier après le premier déploiement
-- ═══════════════════════════════════════════════════════════
 
CREATE TABLE refresh_tokens (
    id          BIGSERIAL       PRIMARY KEY,
    token       VARCHAR(255)    NOT NULL UNIQUE,  -- UUID opaque
    user_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP       NOT NULL,         -- expiration 7 jours
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
 
CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);