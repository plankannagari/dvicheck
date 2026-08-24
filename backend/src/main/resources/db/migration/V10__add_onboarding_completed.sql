ALTER TABLE users
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE users
    ALTER COLUMN onboarding_completed SET DEFAULT FALSE;

COMMENT ON COLUMN users.onboarding_completed
    IS 'Whether the user has completed the first-time onboarding carousel — existing users backfilled to true, default false for new signups going forward';
