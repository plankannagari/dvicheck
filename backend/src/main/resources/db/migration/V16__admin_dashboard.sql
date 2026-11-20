-- V16: admin dashboard
-- is_admin is set manually by SQL only; no API can change it.
ALTER TABLE users
    ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE admin_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id        UUID REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(40) NOT NULL,
    target_user_id  UUID,                          -- deliberately no FK: the log must outlive the target
    details         VARCHAR(500),                  -- short, never contains phone numbers or amounts
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_created ON admin_audit_log(created_at DESC);
CREATE INDEX idx_admin_audit_admin   ON admin_audit_log(admin_id);

COMMENT ON TABLE admin_audit_log
    IS 'Append-only record of every admin dashboard action';
