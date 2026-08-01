-- V2: Core domain tables — bills, line items, shopping lists, shopping items, pantry memory

CREATE TABLE bills (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_name          VARCHAR(100) NOT NULL,
    bill_type           VARCHAR(20) NOT NULL DEFAULT 'GROCERY' CHECK (bill_type IN ('GROCERY', 'UTILITY', 'OTHER')),
    purchase_date       DATE NOT NULL,
    total_amount        NUMERIC(10,2) NOT NULL,
    avoidable_amount    NUMERIC(10,2) DEFAULT 0,
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    raw_ocr_text        TEXT,
    ai_summary          TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bills_user_id ON bills(user_id);
CREATE INDEX idx_bills_purchase_date ON bills(purchase_date);
CREATE INDEX idx_bills_bill_type ON bills(bill_type);

COMMENT ON TABLE bills IS 'Receipt/utility bills scanned by user via OCR';

CREATE TABLE line_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id             UUID NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    quantity            NUMERIC(8,2) NOT NULL DEFAULT 1,
    unit_price          NUMERIC(10,2) NOT NULL,
    total_price         NUMERIC(10,2) NOT NULL,
    category            VARCHAR(20) DEFAULT 'essential' CHECK (category IN ('essential', 'reducible', 'avoidable', 'duplicate')),
    flag_reason         VARCHAR(300),
    suggestion          VARCHAR(500),
    saving_estimate     NUMERIC(10,2),
    confidence          NUMERIC(3,2) DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_line_items_bill_id ON line_items(bill_id);
CREATE INDEX idx_line_items_category ON line_items(category);

COMMENT ON TABLE line_items IS 'Individual items within a scanned bill, with AI-flagged avoidable purchases';

CREATE TABLE shopping_lists (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL DEFAULT 'My List',
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED')),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shopping_lists_user_id ON shopping_lists(user_id);
CREATE INDEX idx_shopping_lists_status ON shopping_lists(status);

COMMENT ON TABLE shopping_lists IS 'Pre-shop lists created by a user';

CREATE TABLE shopping_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_id                 UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
    name                    VARCHAR(200) NOT NULL,
    quantity                VARCHAR(50) DEFAULT '1',
    is_checked              BOOLEAN NOT NULL DEFAULT FALSE,
    is_duplicate            BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_warning       VARCHAR(300),
    last_purchased_date     DATE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shopping_items_list_id ON shopping_items(list_id);
CREATE INDEX idx_shopping_items_is_checked ON shopping_items(is_checked);

COMMENT ON TABLE shopping_items IS 'Items within a shopping list, flagged when they duplicate pantry memory';

CREATE TABLE pantry_memory (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_name                   VARCHAR(200) NOT NULL,
    normalised_name             VARCHAR(200) NOT NULL,
    last_bought_date            DATE NOT NULL,
    typical_quantity            VARCHAR(50),
    estimated_remaining_days    INTEGER DEFAULT 0,
    purchase_count              INTEGER DEFAULT 1,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, normalised_name)
);

CREATE INDEX idx_pantry_memory_user_id ON pantry_memory(user_id);
CREATE INDEX idx_pantry_memory_normalised_name ON pantry_memory(normalised_name);

COMMENT ON TABLE pantry_memory IS 'Learned purchase history per user, used for duplicate-purchase detection';
