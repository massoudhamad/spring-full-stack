-- Procurement Information System — initial schema.
--
-- Flyway owns the schema. Hibernate is set to 'validate' and will refuse to
-- start if the entities and these tables disagree.
--
-- PostgreSQL only. The LOWER() indexes below match the LOWER() comparisons in
-- SupplierRepository.search and RequisitionRepository.search — a plain index on
-- the column would not be used by those queries.

CREATE TABLE supplier (
    id                  UUID            PRIMARY KEY,
    name                VARCHAR(200)    NOT NULL,
    tin                 VARCHAR(20)     NOT NULL,
    registration_number VARCHAR(50)     NOT NULL,
    category            VARCHAR(32)     NOT NULL,
    status              VARCHAR(24)     NOT NULL,
    email               VARCHAR(150)    NOT NULL,
    phone               VARCHAR(30),
    address             VARCHAR(300),
    contact_person      VARCHAR(150),
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uk_supplier_tin    UNIQUE (tin),
    CONSTRAINT uk_supplier_reg_no UNIQUE (registration_number)
);

CREATE INDEX idx_supplier_status   ON supplier (status);
CREATE INDEX idx_supplier_category ON supplier (category);
CREATE INDEX idx_supplier_name     ON supplier (LOWER(name));

CREATE TABLE requisition (
    id                UUID          PRIMARY KEY,
    reference         VARCHAR(40)   NOT NULL,
    department        VARCHAR(150)  NOT NULL,
    requested_by      VARCHAR(150)  NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    justification     VARCHAR(1000),
    required_by_date  DATE,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ,
    CONSTRAINT uk_requisition_reference UNIQUE (reference)
);

CREATE INDEX idx_requisition_status     ON requisition (status);
CREATE INDEX idx_requisition_department ON requisition (LOWER(department));

CREATE TABLE requisition_item (
    id                   UUID           PRIMARY KEY,
    requisition_id       UUID           REFERENCES requisition (id) ON DELETE CASCADE,
    item_code            VARCHAR(40),
    description          VARCHAR(300)   NOT NULL,
    quantity             INTEGER        NOT NULL CHECK (quantity > 0),
    unit                 VARCHAR(20)    NOT NULL,
    estimated_unit_price NUMERIC(19,2)  NOT NULL CHECK (estimated_unit_price >= 0)
);

CREATE INDEX idx_requisition_item_parent ON requisition_item (requisition_id);

CREATE TABLE purchase_order (
    id                     UUID         PRIMARY KEY,
    order_number           VARCHAR(40)  NOT NULL,
    supplier_id            UUID         NOT NULL REFERENCES supplier (id),
    requisition_id         UUID         REFERENCES requisition (id),
    status                 VARCHAR(24)  NOT NULL,
    issued_date            DATE,
    expected_delivery_date DATE,
    notes                  VARCHAR(500),
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ,
    CONSTRAINT uk_po_order_number UNIQUE (order_number)
);

CREATE INDEX idx_po_status   ON purchase_order (status);
CREATE INDEX idx_po_supplier ON purchase_order (supplier_id);

CREATE TABLE purchase_order_item (
    id                UUID           PRIMARY KEY,
    purchase_order_id UUID           REFERENCES purchase_order (id) ON DELETE CASCADE,
    item_code         VARCHAR(40),
    description       VARCHAR(300)   NOT NULL,
    quantity          INTEGER        NOT NULL CHECK (quantity > 0),
    unit              VARCHAR(20)    NOT NULL,
    unit_price        NUMERIC(19,2)  NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_po_item_parent ON purchase_order_item (purchase_order_id);
