ALTER TABLE users
    ADD COLUMN budget_amount DECIMAL(10,2);

COMMENT ON COLUMN users.budget_amount
    IS 'Monthly spending budget target set by user — null means no budget set';
