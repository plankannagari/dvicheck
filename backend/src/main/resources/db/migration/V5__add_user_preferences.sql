-- V5: Add user preference columns
-- household_size feeds into pantry depletion estimates
-- currency controls display format throughout the app

ALTER TABLE users
    ADD COLUMN household_size        INTEGER  NOT NULL DEFAULT 1,
    ADD COLUMN currency              VARCHAR(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN notifications_enabled BOOLEAN  NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN users.household_size
    IS 'People in household — affects pantry depletion estimates';
COMMENT ON COLUMN users.currency
    IS 'ISO 4217 currency code for display (USD, AUD, GBP, EUR, INR)';
COMMENT ON COLUMN users.notifications_enabled
    IS 'Whether push notifications are enabled for this user';
