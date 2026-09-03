-- bills(user_id, purchase_date): covers the WHERE user_id = :userId AND
-- purchase_date BETWEEN ... pattern used by sumTotalBetween, sumTotalSince,
-- sumAvoidableSince, countBillsSince, findRecentByUserId, and the new
-- sumTotalsGroupedByWeek query (Step 4), all in one composite index.
CREATE INDEX IF NOT EXISTS idx_bills_user_purchase_date
    ON bills(user_id, purchase_date);

-- line_items(bill_id): Postgres does NOT automatically index foreign key
-- columns the way some other databases do — every lazy lineItems load and
-- the new countLineItemsByBillIds query (Step 3) depend on this FK column
-- being indexed. (Already present today as idx_line_items_bill_id from
-- V2 — IF NOT EXISTS keeps this migration a safe no-op for that index.)
CREATE INDEX IF NOT EXISTS idx_line_items_bill_id
    ON line_items(bill_id);
