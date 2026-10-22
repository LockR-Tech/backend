-- Switch drone receiving from the demo cabinet to the real TU01 cabinet.
-- Keep the demo cabinet's data; maintenance status prevents new bookings.
UPDATE locker_schema.lockers
SET status = 'MAINTENANCE',
    maintenance_source = 'ADMIN',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1
  AND code = 'CAB-DEMO-01';

-- TU01 has two configured DRONE cells and is online. Its landing-pad marker
-- is intentionally left unset until the physical pad marker is provisioned.
UPDATE locker_schema.lockers
SET status = 'ACTIVE',
    maintenance_source = NULL,
    landing_pad = TRUE,
    landing_pad_status = 'OK',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 7
  AND code = 'CAB-TU01';
