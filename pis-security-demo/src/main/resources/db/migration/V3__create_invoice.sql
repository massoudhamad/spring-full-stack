-- Invoice and its line items.
--
-- Two status columns by design:
--   invoice_status — where the invoice sits in the approval/payment workflow
--   status         — whether the record is still in use (ACTIVE/INACTIVE)
-- A PAID invoice can be archived without losing the fact that it was paid.

CREATE TABLE invoice (
    id                UUID         PRIMARY KEY,
    invoice_number    VARCHAR(40)  NOT NULL,
    invoice_date      DATE         NOT NULL,
    supplier_id       UUID         NOT NULL REFERENCES supplier (id),
    purchase_order_id UUID         REFERENCES purchase_order (id),
    invoice_status    VARCHAR(24)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    due_date          DATE,
    paid_date         DATE,
    notes             VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ,
    CONSTRAINT uk_invoice_number UNIQUE (invoice_number)
);

CREATE INDEX idx_invoice_status   ON invoice (invoice_status);
CREATE INDEX idx_invoice_supplier ON invoice (supplier_id);
CREATE INDEX idx_invoice_date     ON invoice (invoice_date);

CREATE TABLE invoice_item (
    id          UUID           PRIMARY KEY,
    invoice_id  UUID           REFERENCES invoice (id) ON DELETE CASCADE,
    item_code   VARCHAR(40),
    description VARCHAR(300)   NOT NULL,
    quantity    INTEGER        NOT NULL,
    unit        VARCHAR(20)    NOT NULL,
    unit_price  NUMERIC(19,2)  NOT NULL
);

CREATE INDEX idx_invoice_item_parent ON invoice_item (invoice_id);
