-- Local dev seed data. Auto-runs on startup. Resets on app restart (ddl-auto: create-drop).

INSERT INTO artist_profile (agency_id, name, fan_count, joined_at, bio)
VALUES (999, 'Test Starlight', 0, '2024-01-01 00:00:00', 'Local dev test artist');

INSERT INTO product (artist_id, name, price, status)
SELECT id, 'Test Photocard Set', 1, 'ON_SALE'
FROM artist_profile WHERE name = 'Test Starlight' LIMIT 1;

INSERT INTO product (artist_id, name, price, status)
SELECT id, 'Test Light Stick', 35000, 'ON_SALE'
FROM artist_profile WHERE name = 'Test Starlight' LIMIT 1;

INSERT INTO inventory (product_id, total_qty, reserved_qty, available_qty, version)
SELECT id, 100, 0, 100, 0
FROM product WHERE name LIKE 'Test%';