-- Who created and who last changed each record.
--
-- Nullable: rows that existed before this migration have no known author.
-- A username, not a foreign key: the audit trail must survive a user being deleted.

ALTER TABLE supplier       ADD COLUMN created_by VARCHAR(50), ADD COLUMN updated_by VARCHAR(50);
ALTER TABLE requisition    ADD COLUMN created_by VARCHAR(50), ADD COLUMN updated_by VARCHAR(50);
ALTER TABLE purchase_order ADD COLUMN created_by VARCHAR(50), ADD COLUMN updated_by VARCHAR(50);
ALTER TABLE invoice        ADD COLUMN created_by VARCHAR(50), ADD COLUMN updated_by VARCHAR(50);
