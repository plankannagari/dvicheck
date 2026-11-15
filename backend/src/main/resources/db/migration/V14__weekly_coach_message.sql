-- V14: Pro weekly AI coach messages
CREATE TABLE weekly_coach_message (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start    DATE NOT NULL,                       -- Monday of the week being coached
    status        VARCHAR(12) NOT NULL DEFAULT 'GENERATING'
                  CHECK (status IN ('GENERATING', 'READY')),
    source        VARCHAR(10) NOT NULL DEFAULT 'AI'
                  CHECK (source IN ('AI', 'FALLBACK')),
    headline      VARCHAR(120),
    win           VARCHAR(300),
    watch         VARCHAR(300),
    action        VARCHAR(300),
    facts_json    TEXT,                                -- exactly what the model was shown
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    push_sent_at  TIMESTAMP WITH TIME ZONE,
    UNIQUE (user_id, week_start)
);

COMMENT ON TABLE weekly_coach_message
    IS 'One AI (or deterministic fallback) coach message per Pro user per week; UNIQUE doubles as the job idempotency lock';
