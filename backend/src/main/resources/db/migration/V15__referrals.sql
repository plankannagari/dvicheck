-- V15: referral system
-- Invite code is generated lazily on first request, so existing users need no backfill.
ALTER TABLE users
    ADD COLUMN referral_code      VARCHAR(12) UNIQUE,
    ADD COLUMN referral_pro_until TIMESTAMP WITH TIME ZONE;   -- separate from subscription_expires_at (owned by RevenueCat webhook)

CREATE TABLE referral (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    referee_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code         VARCHAR(12) NOT NULL,
    status       VARCHAR(12) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'QUALIFIED')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    qualified_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (referee_id),                       -- one redemption per account (also indexes referee_id)
    CHECK (referrer_id <> referee_id)          -- no self-referral
);

CREATE INDEX idx_referral_referrer ON referral(referrer_id);

COMMENT ON TABLE referral
    IS 'Invite relationship. PENDING at redeem, QUALIFIED when the referee completes a first scan; both users then receive referral_pro_until days';
