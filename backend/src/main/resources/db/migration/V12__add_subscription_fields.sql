ALTER TABLE users
    ADD COLUMN subscription_expires_at TIMESTAMPTZ NULL,
    ADD COLUMN subscription_product_id VARCHAR(100) NULL;

COMMENT ON COLUMN users.subscription_expires_at
    IS 'End of current subscription entitlement, set from RevenueCat webhook events. Pro status is ALWAYS derived by comparing this to now() at read time, never cached as a separate boolean.';
