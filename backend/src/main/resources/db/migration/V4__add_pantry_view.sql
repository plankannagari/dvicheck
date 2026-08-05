CREATE VIEW pantry_recent_purchases AS
SELECT
  pm.user_id,
  pm.item_name,
  pm.normalised_name,
  pm.last_bought_date,
  pm.estimated_remaining_days,
  pm.purchase_count,
  CASE
    WHEN pm.last_bought_date >= CURRENT_DATE - INTERVAL '5 days' THEN 'HIGH'
    WHEN pm.last_bought_date >= CURRENT_DATE - INTERVAL '10 days' THEN 'MEDIUM'
    ELSE 'LOW'
  END AS duplicate_risk
FROM pantry_memory pm;

COMMENT ON VIEW pantry_recent_purchases IS 'Simplifies duplicate-purchase detection by pre-computing a HIGH/MEDIUM/LOW risk tier from how recently each pantry item was last bought';
