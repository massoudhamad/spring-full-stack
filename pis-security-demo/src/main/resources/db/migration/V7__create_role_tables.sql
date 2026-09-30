-- Roles move from code (the Role enum, Lesson 3C) into the database, so an
-- admin can create a role or change its permissions without a release.
--
-- Permissions stay in code (the Permission enum): a permission is what the code
-- CHECKS, so a new one always arrives with new code. role_permission.permission
-- holds the enum constant's name, e.g. SUPPLIER_READ.

CREATE TABLE role (
    name        VARCHAR(20)   PRIMARY KEY,           -- what app_user_role.role refers to
    description VARCHAR(200)  NOT NULL,
    built_in    BOOLEAN       NOT NULL DEFAULT FALSE, -- shipped with PIS; cannot be deleted
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(50),
    updated_by  VARCHAR(50)
);

CREATE TABLE role_permission (
    role       VARCHAR(20) NOT NULL REFERENCES role (name) ON DELETE CASCADE,
    permission VARCHAR(40) NOT NULL,
    PRIMARY KEY (role, permission)
);

-- The three roles from Lesson 3C, with exactly the same permissions.
INSERT INTO role (name, description, built_in, created_at, created_by) VALUES
    ('OFFICER',  'Raises requisitions, registers suppliers, prepares orders and invoices', TRUE, now(), 'system'),
    ('APPROVER', 'Approves what officers raise. Reads everything, writes nothing',        TRUE, now(), 'system'),
    ('ADMIN',    'Everything, including users and roles',                                 TRUE, now(), 'system');

INSERT INTO role_permission (role, permission) VALUES
    ('OFFICER', 'SUPPLIER_READ'), ('OFFICER', 'SUPPLIER_WRITE'),
    ('OFFICER', 'REQUISITION_READ'), ('OFFICER', 'REQUISITION_WRITE'),
    ('OFFICER', 'PURCHASE_ORDER_READ'), ('OFFICER', 'PURCHASE_ORDER_WRITE'),
    ('OFFICER', 'INVOICE_READ'), ('OFFICER', 'INVOICE_WRITE'),

    ('APPROVER', 'SUPPLIER_READ'), ('APPROVER', 'SUPPLIER_APPROVE'),
    ('APPROVER', 'REQUISITION_READ'), ('APPROVER', 'REQUISITION_APPROVE'),
    ('APPROVER', 'PURCHASE_ORDER_READ'), ('APPROVER', 'INVOICE_READ'),

    ('ADMIN', 'SUPPLIER_READ'), ('ADMIN', 'SUPPLIER_WRITE'), ('ADMIN', 'SUPPLIER_APPROVE'),
    ('ADMIN', 'REQUISITION_READ'), ('ADMIN', 'REQUISITION_WRITE'), ('ADMIN', 'REQUISITION_APPROVE'),
    ('ADMIN', 'PURCHASE_ORDER_READ'), ('ADMIN', 'PURCHASE_ORDER_WRITE'),
    ('ADMIN', 'INVOICE_READ'), ('ADMIN', 'INVOICE_WRITE'),
    ('ADMIN', 'RECORD_DELETE'), ('ADMIN', 'USER_MANAGE'), ('ADMIN', 'ROLE_MANAGE');

-- A user can only hold a role that exists. The existing rows already match.
ALTER TABLE app_user_role
    ADD CONSTRAINT fk_app_user_role_role FOREIGN KEY (role) REFERENCES role (name);
