-- Module: Area Verification, rebuilt to the SRS data dictionary (section 4.2, module area).
-- The V5 tables followed an earlier design and were never used by any code, so they hold no data.

DROP TABLE device_signals;
DROP TABLE area_verifications;
DROP TABLE user_areas;

CREATE TABLE user_areas
(
    id                   UUID PRIMARY KEY,
    user_id              UUID                   NOT NULL, -- logical -> users
    slot                 SMALLINT               NOT NULL
        CONSTRAINT user_areas_slot_chk CHECK (slot BETWEEN 1 AND 2),
    label                VARCHAR(30)            NOT NULL,
    locality_name        VARCHAR(100),
    center               geography(Point, 4326) NOT NULL,
    grid_cell            VARCHAR(20)            NOT NULL,
    range_level          VARCHAR(6)             NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT user_areas_range_level_chk CHECK (range_level IN ('NEAR', 'MEDIUM', 'FAR')),
    is_active            BOOLEAN                NOT NULL DEFAULT FALSE,
    status               VARCHAR(10)            NOT NULL
        CONSTRAINT user_areas_status_chk CHECK (status IN ('VERIFIED', 'EXPIRING', 'EXPIRED', 'REMOVED')),
    verified_at          TIMESTAMPTZ            NOT NULL,
    expires_at           TIMESTAMPTZ            NOT NULL,
    reminder_sent_at     TIMESTAMPTZ,
    last_verification_id UUID,
    removed_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ            NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ            NOT NULL DEFAULT now(),
    version              BIGINT                 NOT NULL DEFAULT 0,
    CONSTRAINT user_areas_removed_at_chk CHECK ((status = 'REMOVED') = (removed_at IS NOT NULL))
);
-- FR-AR-02: at most area.max_count (2) live areas per user, guaranteed even for concurrent requests
CREATE UNIQUE INDEX user_areas_live_slot_uq ON user_areas (user_id, slot) WHERE status <> 'REMOVED';
-- FR-AR-08: at most one area drives the feed
CREATE UNIQUE INDEX user_areas_active_uq ON user_areas (user_id) WHERE is_active AND status <> 'REMOVED';
CREATE INDEX user_areas_status_expires_at_idx ON user_areas (status, expires_at) WHERE status <> 'REMOVED';
CREATE INDEX user_areas_center_gist_idx ON user_areas USING GIST (center);

-- Every verification or renewal attempt, accepted or rejected (FR-AR-10). Insert-only; the purge job
-- (UC-SY-11) may only clear reported_location and set location_purged_at.
CREATE TABLE area_verifications
(
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMPTZ            NOT NULL DEFAULT now(),
    user_id            UUID                   NOT NULL, -- logical -> users
    user_area_id       UUID REFERENCES user_areas (id) ON DELETE SET NULL, -- areas are deleted on anonymisation (SRS 4.5)
    kind               VARCHAR(12)            NOT NULL
        CONSTRAINT area_verifications_kind_chk CHECK (kind IN ('INITIAL', 'RENEWAL', 'REPLACEMENT')),
    result             VARCHAR(10)            NOT NULL
        CONSTRAINT area_verifications_result_chk CHECK (result IN ('ACCEPTED', 'REJECTED')),
    reason_codes       VARCHAR(30)[]          NOT NULL DEFAULT '{}',
    reported_location  geography(Point, 4326),
    grid_cell          VARCHAR(20)            NOT NULL,
    accuracy_m         REAL                   NOT NULL
        CONSTRAINT area_verifications_accuracy_chk CHECK (accuracy_m >= 0),
    fix_time           TIMESTAMPTZ            NOT NULL,
    distance_m         REAL,
    implied_speed_kmh  REAL,
    mock_flag          BOOLEAN                NOT NULL,
    is_mock_suspected  BOOLEAN                NOT NULL DEFAULT FALSE,
    idempotency_key    UUID                   NOT NULL,
    app_version        VARCHAR(20),
    location_purged_at TIMESTAMPTZ,
    CONSTRAINT area_verifications_purge_chk CHECK (location_purged_at IS NULL OR reported_location IS NULL)
);
CREATE INDEX area_verifications_user_created_idx ON area_verifications (user_id, created_at DESC);
CREATE INDEX area_verifications_user_area_id_idx ON area_verifications (user_area_id);
CREATE INDEX area_verifications_unpurged_idx ON area_verifications (created_at) WHERE reported_location IS NOT NULL;

-- Hard and soft device signals per attempt (FR-AR-05); soft ones never change the result
CREATE TABLE device_signals
(
    id                   UUID PRIMARY KEY,
    area_verification_id UUID        NOT NULL REFERENCES area_verifications (id) ON DELETE CASCADE,
    user_id              UUID        NOT NULL, -- logical -> users
    signal_code          VARCHAR(30) NOT NULL
        CONSTRAINT device_signals_code_chk CHECK (signal_code IN
                                                  ('MOCK_FLAG', 'MOCK_APP', 'EMULATOR', 'ROOT', 'DEV_OPTIONS',
                                                   'TIME_DRIFT', 'IMPOSSIBLE_SPEED', 'IP_MISMATCH',
                                                   'PLAY_INTEGRITY')),
    severity             VARCHAR(4)  NOT NULL
        CONSTRAINT device_signals_severity_chk CHECK (severity IN ('HARD', 'SOFT')),
    raw_value            VARCHAR(200),
    detected_at          TIMESTAMPTZ NOT NULL
);
CREATE INDEX device_signals_area_verification_id_idx ON device_signals (area_verification_id);
CREATE INDEX device_signals_user_id_idx ON device_signals (user_id, detected_at DESC);

-- Parameters per SRS table tab:params (resolves OI-06 for the area keys)
DELETE FROM app_parameters
WHERE key IN ('area.verification_validity_days', 'area.retention_days');

INSERT INTO app_parameters (key, value, description)
VALUES ('area.validity_days', '30', 'Days a verified area stays valid'),
       ('area.renewal_reminder_days', '3', 'Days before expiry when the area becomes EXPIRING and a reminder is sent'),
       ('area.renew_radius_m', '1500', 'Maximum distance (meters) between the fix and the area center to renew'),
       ('area.max_fix_age_s', '120', 'Maximum age (seconds) of a GPS fix accepted for verification'),
       ('area.max_speed_kmh', '300', 'Maximum implied speed (km/h) since the previous accepted fix'),
       ('area.max_attempts_per_day', '5', 'Verification or renewal attempts per user per day'),
       ('area.max_changes_per_30d', '3', 'Areas added or replaced per user per 30 days'),
       ('area.range_levels_km', '2,4,6', 'Feed and interaction range (km) for NEAR, MEDIUM and FAR'),
       ('area.grid_cell_m', '300', 'Side (Web Mercator meters) of the grid cell an area center is snapped to'),
       ('area.attempt_retention_months', '24', 'Months verification attempts are kept'),
       ('location.retention_days', '90', 'Days precise verification locations are kept before purge'),
       ('listing.pin_radius_m', '1500', 'Maximum distance (meters) between a listing pin and the seller area center');
