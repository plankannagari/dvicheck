CREATE TABLE budget_alert_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    threshold       INTEGER NOT NULL CHECK (threshold IN (80, 100)),
    period_month    VARCHAR(7) NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, threshold, period_month)
);

CREATE INDEX idx_budget_alert_log_user ON budget_alert_log(user_id);

COMMENT ON TABLE budget_alert_log
    IS 'Tracks which budget threshold push alerts (80% / 100%) have already been sent per user per month, to prevent duplicate notifications';
