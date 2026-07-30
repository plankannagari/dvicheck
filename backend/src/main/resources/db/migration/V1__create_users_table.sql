-- V1: Create users table
-- Identified by phone number only — no email, no social login

CREATE TABLE users (
                       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       phone       VARCHAR(20) UNIQUE NOT NULL,
                       created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users(phone);

COMMENT ON TABLE users IS 'App users — identified by phone number only';