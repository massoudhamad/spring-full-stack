-- Sample data so the API returns something on first run.
-- Delete this migration before deploying anywhere real.

INSERT INTO supplier (id, name, tin, registration_number, category, status,
                      email, phone, address, contact_person, created_at)
VALUES
 ('11111111-1111-1111-1111-111111111111', 'Zanzibar Office Supplies Ltd', '101-201-301',
  'BRELA-2019-0451', 'GOODS', 'ACTIVE', 'sales@zos.co.tz', '+255 777 100 200',
  'Mlandege, Zanzibar City', 'Asha Khamis', now()),

 ('22222222-2222-2222-2222-222222222222', 'Pemba Construction Works Co', '102-202-302',
  'BRELA-2020-1187', 'WORKS', 'ACTIVE', 'info@pembaworks.co.tz', '+255 777 300 400',
  'Chake Chake, Pemba', 'Juma Salum', now()),

 ('33333333-3333-3333-3333-333333333333', 'Kisiwa ICT Consultants', '103-203-303',
  'BRELA-2021-0902', 'CONSULTANCY_SERVICES', 'PENDING_APPROVAL', 'hello@kisiwaict.co.tz',
  '+255 778 500 600', 'Mazizini, Zanzibar', 'Fatma Ali', now());

INSERT INTO requisition (id, reference, department, requested_by, status,
                         justification, required_by_date, created_at)
VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'REQ-2026-000001', 'ICT Department',
  'Head of ICT', 'APPROVED',
  'Replacement of end-of-life workstations in the records office.',
  CURRENT_DATE + 45, now());

INSERT INTO requisition_item (id, requisition_id, item_code, description, quantity, unit, estimated_unit_price)
VALUES
 ('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'IT-WS-001', 'Desktop workstation, 16GB RAM, 512GB SSD', 12, 'PIECE', 1450000.00),
 ('aaaaaaaa-0000-0000-0000-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'IT-MON-002', 'Monitor 24 inch IPS', 12, 'PIECE', 385000.00),
 ('aaaaaaaa-0000-0000-0000-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'IT-UPS-003', 'UPS 1000VA line interactive', 6, 'PIECE', 520000.00);
