-- Module: Identity + Admin. Account states of SRS table 14 (FR-ID-19), account deletion (FR-ID-16),
-- display name change limit (FR-ID-11) and login lockout per client IP (FR-ID-03).

ALTER TABLE users ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE users DROP CONSTRAINT users_status_chk;
ALTER TABLE users
    ADD CONSTRAINT users_status_chk CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'PENDING_DELETION', 'DELETED'));

ALTER TABLE users ADD COLUMN suspended_until TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN anonymised_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN display_name_changed_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT users_suspended_until_chk CHECK ((status = 'SUSPENDED') = (suspended_until IS NOT NULL));
ALTER TABLE users
    ADD CONSTRAINT users_deletion_requested_at_chk
        CHECK (deletion_requested_at IS NULL OR status IN ('PENDING_DELETION', 'DELETED'));
ALTER TABLE users
    ADD CONSTRAINT users_anonymised_at_chk CHECK ((status = 'DELETED') = (anonymised_at IS NOT NULL));

-- An anonymised account keeps no contact data, so its phone number can register again.
ALTER TABLE users DROP CONSTRAINT users_login_identifier_chk;
ALTER TABLE users
    ADD CONSTRAINT users_login_identifier_chk
        CHECK (phone_e164 IS NOT NULL OR email_verified_at IS NOT NULL OR status = 'DELETED');

CREATE INDEX users_suspended_until_idx ON users (suspended_until) WHERE status = 'SUSPENDED';
CREATE INDEX users_deletion_requested_at_idx ON users (deletion_requested_at) WHERE status = 'PENDING_DELETION';

ALTER TABLE refresh_tokens DROP CONSTRAINT refresh_tokens_revoke_reason_chk;
ALTER TABLE refresh_tokens
    ADD CONSTRAINT refresh_tokens_revoke_reason_chk CHECK (revoke_reason IN
        ('LOGOUT', 'ROTATED', 'REUSE_DETECTED', 'PASSWORD_CHANGED', 'SANCTION', 'USER_REVOKED', 'ADMIN',
         'ACCOUNT_DELETION'));

INSERT INTO app_parameters (key, value, description)
VALUES ('account.deletion_grace_days', '7', 'Days a deletion request can still be cancelled by logging in'),
       ('profile.display_name_change_days', '30', 'Minimum days between two display name changes'),
       ('auth.login_max_failed_per_ip', '20',
        'Failed logins per client IP within auth.login_lockout_minutes before login is blocked for that IP');
