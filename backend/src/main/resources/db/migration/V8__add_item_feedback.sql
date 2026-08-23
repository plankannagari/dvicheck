CREATE TABLE item_feedback (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    line_item_id    UUID NOT NULL REFERENCES line_items(id) ON DELETE CASCADE,
    feedback        VARCHAR(10) NOT NULL CHECK (feedback IN ('HELPFUL', 'UNHELPFUL')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, line_item_id)
);

CREATE INDEX idx_item_feedback_user ON item_feedback(user_id);
CREATE INDEX idx_item_feedback_line_item ON item_feedback(line_item_id);

COMMENT ON TABLE item_feedback
    IS 'User thumbs up/down on AI category suggestions — used to improve future prompts';
