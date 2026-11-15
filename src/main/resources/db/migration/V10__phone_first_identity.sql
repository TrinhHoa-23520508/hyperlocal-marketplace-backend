-- Module: Identity + Admin. Phone-first identity with SMS OTP (ADR-18, OI-01; FR-ID-01..03),
-- optional email (FR-ID-09), purpose-based consent ledger (FR-ID-15, OI-02) and refresh token
-- revocation reasons (FR-ID-04, FR-ID-05).

-- Accounts never verified by email could not log in and have no phone number to fall back on.
DELETE FROM users WHERE status = 'PENDING';

ALTER TABLE users DROP CONSTRAINT users_status_chk;
ALTER TABLE users
    ADD CONSTRAINT users_status_chk CHECK (status IN ('ACTIVE', 'BANNED'));
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE users ADD COLUMN phone_e164 VARCHAR(16);
ALTER TABLE users ADD COLUMN phone_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN phone_changed_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;
ALTER TABLE users ADD CONSTRAINT users_phone_e164_key UNIQUE (phone_e164);
ALTER TABLE users
    ADD CONSTRAINT users_phone_e164_format_chk CHECK (phone_e164 ~ '^\+84[0-9]{9}$');
ALTER TABLE users
    ADD CONSTRAINT users_phone_verified_chk CHECK (phone_e164 IS NULL OR phone_verified_at IS NOT NULL);

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(254);

-- Accounts created before phone-first identity keep logging in with their verified email.
ALTER TABLE users
    ADD CONSTRAINT users_login_identifier_chk CHECK (phone_e164 IS NOT NULL OR email_verified_at IS NOT NULL);

CREATE TABLE user_consents
(
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose        VARCHAR(20) NOT NULL
        CONSTRAINT user_consents_purpose_chk CHECK (purpose IN ('TERMS', 'PRIVACY', 'PRECISE_LOCATION', 'PUSH_MARKETING')),
    policy_version VARCHAR(20) NOT NULL,
    granted_at     TIMESTAMPTZ NOT NULL,
    withdrawn_at   TIMESTAMPTZ,
    channel        VARCHAR(10) NOT NULL
        CONSTRAINT user_consents_channel_chk CHECK (channel IN ('ANDROID', 'ADMIN_WEB'))
);
CREATE INDEX user_consents_user_purpose_idx ON user_consents (user_id, purpose, granted_at DESC);

INSERT INTO user_consents (id, user_id, purpose, policy_version, granted_at, withdrawn_at, channel)
SELECT gen_random_uuid(), id, 'PRECISE_LOCATION', consent_terms_version, consent_at, consent_withdrawn_at, 'ANDROID'
FROM users
WHERE consent_at IS NOT NULL;

ALTER TABLE users DROP CONSTRAINT users_consent_terms_version_chk;
ALTER TABLE users DROP COLUMN consent_at;
ALTER TABLE users DROP COLUMN consent_terms_version;
ALTER TABLE users DROP COLUMN consent_withdrawn_at;

ALTER TABLE refresh_tokens ADD COLUMN revoke_reason VARCHAR(20);
ALTER TABLE refresh_tokens
    ADD CONSTRAINT refresh_tokens_revoke_reason_chk CHECK (revoke_reason IN
        ('LOGOUT', 'ROTATED', 'REUSE_DETECTED', 'PASSWORD_CHANGED', 'SANCTION', 'USER_REVOKED', 'ADMIN'));

INSERT INTO app_parameters (key, value, description)
VALUES ('otp.ttl_s', '300', 'Seconds a one-time SMS code stays valid'),
       ('otp.max_attempts', '5', 'Wrong entries allowed per one-time code before it is cancelled'),
       ('otp.resend_s', '60', 'Seconds before another code can be sent to the same number for the same purpose'),
       ('otp.max_per_day', '5', 'One-time codes sent per phone number per day'),
       ('consent.terms_version', '1.0', 'Current version of the Terms of Service'),
       ('consent.privacy_version', '1.0', 'Current version of the Privacy Policy');
