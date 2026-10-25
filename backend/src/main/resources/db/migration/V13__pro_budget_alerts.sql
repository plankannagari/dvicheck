-- Day 33: Pro budget alerts — custom thresholds + spending-pace alerts.

-- Comma-separated percentages (e.g. '50,75,90,100'); NULL = use the defaults (80,100).
ALTER TABLE users
    ADD COLUMN alert_thresholds VARCHAR(40) NULL,
    ADD COLUMN pace_alerts_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- V9 hard-coded threshold IN (80, 100) and deduped on (user_id, threshold, period_month).
-- Custom thresholds and a second alert type need both relaxed. Names verified via \d on Day 33.
ALTER TABLE budget_alert_log
    DROP CONSTRAINT budget_alert_log_threshold_check,
    DROP CONSTRAINT budget_alert_log_user_id_threshold_period_month_key;

-- Existing rows backfill to 'THRESHOLD' via the default.
ALTER TABLE budget_alert_log
    ADD COLUMN alert_type VARCHAR(10) NOT NULL DEFAULT 'THRESHOLD';

-- PACE rows store threshold 0.
ALTER TABLE budget_alert_log
    ADD CONSTRAINT budget_alert_log_alert_type_check CHECK (alert_type IN ('THRESHOLD', 'PACE')),
    ADD CONSTRAINT budget_alert_log_threshold_check CHECK (threshold BETWEEN 0 AND 100),
    ADD CONSTRAINT budget_alert_log_dedupe_key UNIQUE (user_id, alert_type, threshold, period_month);
