-- Areas live in user_areas (module area). The unverified default area on users is not in the
-- SRS data dictionary (section 4.6 marks it "not applied"), so it is dropped (decision DEC-AR-4).
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_verified_area_count_chk,
    DROP COLUMN IF EXISTS home_area,
    DROP COLUMN IF EXISTS verified_area_count;
