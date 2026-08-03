-- V3: line_items.category was lowercase ('essential', 'reducible', 'avoidable', 'duplicate')
-- but the ItemCategory Java enum is uppercase (ESSENTIAL, REDUCIBLE, AVOIDABLE, DUPLICATE).
-- Any row relying on the DB default (or inserted with a lowercase value) fails to deserialize
-- via Hibernate's @Enumerated(EnumType.STRING) with IllegalArgumentException.

UPDATE line_items SET category = UPPER(category) WHERE category IS NOT NULL;

ALTER TABLE line_items DROP CONSTRAINT line_items_category_check;

ALTER TABLE line_items ALTER COLUMN category SET DEFAULT 'ESSENTIAL';

ALTER TABLE line_items ADD CONSTRAINT line_items_category_check
    CHECK (category IN ('ESSENTIAL', 'REDUCIBLE', 'AVOIDABLE', 'DUPLICATE'));
