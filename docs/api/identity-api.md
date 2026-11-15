# Identity API contract (Accounts and Authorization)

This contract covers SRS section 3.1: FR-ID-01 to FR-ID-05, FR-ID-07 to FR-ID-09, FR-ID-11, FR-ID-15 and FR-ID-19. The live OpenAPI document is at `/v3/api-docs`, and Swagger UI is at `/swagger-ui.html`. A Postman collection is in [`docs/postman/`](../postman/).

## Conventions

| Topic | Rule |
|---|---|
| Base path | `/api/v1` |
| Body | JSON (`application/json`); timestamps are ISO-8601 in UTC |
| Auth | `Authorization: Bearer <accessToken>` on every endpoint except `/api/v1/auth/**` |
| Errors | RFC 9457 `application/problem+json` with a stable `code` field. Validation errors add `errors: [{field, message}]` |
| Rate limits | `429` (`RATE_LIMITED`, `OTP_RATE_LIMITED` or `LOGIN_LOCKED`) with a `Retry-After` header in seconds |
| Pagination | Cursor based: `{items, nextCursor, hasMore}`. Pass `nextCursor` back as `cursor` |

Error example:

```json
{
  "type": "https://nearby.uit.edu.vn/problems/weak-password",
  "title": "Bad Request",
  "status": 400,
  "detail": "Password must be at least 8 characters. Password must contain both letters and digits.",
  "code": "WEAK_PASSWORD",
  "violations": [
    { "rule": "MIN_LENGTH", "message": "Password must be at least 8 characters." },
    { "rule": "LETTER_AND_DIGIT", "message": "Password must contain both letters and digits." }
  ]
}
```

## Endpoint summary

| # | Method and path | Auth | Success | Requirement |
|---|---|---|---|---|
| 1 | `POST /auth/register/otp` | public | 202 | FR-ID-02 |
| 2 | `POST /auth/register` | public | 201 | FR-ID-01 |
| 3 | `POST /auth/login` | public | 200 | FR-ID-03 |
| 4 | `POST /auth/refresh` | public | 200 | FR-ID-04 |
| 5 | `POST /auth/logout` | public | 204 | FR-ID-05 |
| 6 | `POST /auth/password/forgot` | public | 202 | FR-ID-07 |
| 7 | `POST /auth/password/verify-otp` | public | 200 | FR-ID-07 |
| 8 | `POST /auth/password/reset` | public | 204 | FR-ID-07 |
| 9 | `POST /auth/email-verification` | public | 204 | FR-ID-09 |
| 10 | `PUT /users/me/password` | USER | 204 | FR-ID-08 |
| 11 | `PUT /users/me/email` | USER | 202 | FR-ID-09 |
| 12 | `POST /users/me/email/verification` | USER | 202 | FR-ID-09 |
| 13 | `GET /users/me` | USER | 200 | FR-ID-11, FR-ID-12 |
| 14 | `PUT /users/me` | USER | 200 | FR-ID-11 |
| 15 | `POST /users/me/avatar/upload-url` | USER | 200 | FR-ID-11 |
| 16 | `PUT /users/me/avatar` | USER | 200 | FR-ID-11 |
| 17 | `DELETE /users/me/avatar` | USER | 204 | FR-ID-11 |
| 18 | `GET /users/me/location-consent` | USER | 200 | FR-ID-15 |
| 19 | `PUT /users/me/location-consent` | USER | 200 | FR-ID-15 |
| 20 | `DELETE /users/me/location-consent` | USER | 204 | FR-ID-15 |
| 21 | `GET /admin/users` | ADMIN | 200 | FR-ID-19 |
| 22 | `GET /admin/users/{userId}` | ADMIN | 200 | FR-ID-19 |
| 23 | `POST /admin/users/{userId}/lock` | ADMIN | 200 | FR-ID-19 |
| 24 | `POST /admin/users/{userId}/unlock` | ADMIN | 200 | FR-ID-19 |

Every path in the table is relative to `/api/v1`. Every authenticated endpoint can also return `401 UNAUTHENTICATED`, and every endpoint can return `400 VALIDATION_FAILED`. The per-endpoint tables below list only the other errors.

## Account lifecycle

```
send SMS code --> register with the code --> ACTIVE (phone verified)
ACTIVE --admin lock--> BANNED --admin unlock--> ACTIVE
```

The phone number (E.164, `+84` and 9 digits) is the login identifier. An email address is optional; it can be used to log in and to reset the password only once it is verified.

## One-time SMS codes (FR-ID-02)

| Rule | Value (`app_parameters`) |
|---|---|
| Code | 6 digits, stored in Redis only as an HMAC-SHA256 hash |
| Lifetime | `otp.ttl_s` = 300 seconds |
| Wrong entries per code | `otp.max_attempts` = 5; the 5th wrong entry cancels the code |
| Resend interval (same number, same purpose) | `otp.resend_s` = 60 seconds |
| Codes per number per day | `otp.max_per_day` = 5 |

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_PHONE` | Not a Vietnamese mobile number |
| 422 | `OTP_INVALID` | Wrong code; `remainingAttempts` tells how many tries are left |
| 422 | `OTP_EXPIRED` | The code expired, was already used or never existed |
| 422 | `OTP_ATTEMPTS_EXCEEDED` | Too many wrong codes; request a new one |
| 429 | `OTP_RATE_LIMITED` | Inside the resend interval or over the daily quota; `Retry-After` header |
| 503 | `SMS_UNAVAILABLE` | The SMS gateway failed; the quota is not used up |

---

## 1. Authentication

### 1.1 `POST /auth/register/otp`

Request: `{ "phone": "0912 345 678" }`. Accepted forms: `0912345678`, `84912345678`, `+84912345678`, with spaces, dots or dashes.

Response `202`:

```json
{ "challengeId": "5c1f...", "expiresInSeconds": 300, "resendAfterSeconds": 60 }
```

The code is sent even when the number already has an account; that is revealed only after the code is verified.

### 1.2 `POST /auth/register`

Request:

```json
{
  "phone": "0912345678",
  "challengeId": "5c1f...",
  "otp": "123456",
  "password": "Secret123",
  "displayName": "An Nguyen",
  "termsVersion": "1.0",
  "privacyVersion": "1.0",
  "pushMarketing": false
}
```

| Field | Rules |
|---|---|
| `phone` | Required, the number the code was sent to |
| `challengeId` / `otp` | From step 1.1 and the SMS; `otp` is 6 digits |
| `password` | Required, max 200. At least 8 characters, at most 72 UTF-8 bytes, letters and digits |
| `displayName` | Required, 2-30 characters, trimmed |
| `termsVersion` / `privacyVersion` | Versions of the Terms and the Privacy Policy the user accepted (`consent.terms_version`, `consent.privacy_version`). Leave out when not accepted |
| `pushMarketing` | Optional consent to marketing pushes |

Response `201`: the account is `ACTIVE`, TERMS and PRIVACY consents are recorded with their versions, `UserRegistered` is published and a session starts.

```json
{
  "userId": "1b0c...",
  "phone": "+84912345678",
  "tokens": { "tokenType": "Bearer", "accessToken": "...", "accessTokenExpiresAt": "...", "refreshToken": "...", "refreshTokenExpiresAt": "..." }
}
```

| Status | Code | When |
|---|---|---|
| 400 | `WEAK_PASSWORD` | The password breaks the policy. `violations` lists every rule that failed |
| 409 | `POLICY_VERSION_STALE` | The accepted versions are not the current ones |
| 409 | `PHONE_ALREADY_REGISTERED` | The number already has an account (reported only after a correct code). Log in or reset the password |
| 422 | `CONSENT_REQUIRED` | Terms or Privacy Policy not accepted |
| 422 | `OTP_*` | See the OTP table |
| 429 | `RATE_LIMITED` | More than `auth.registrations_per_hour_per_ip` registrations from the same IP |

### 1.3 `POST /auth/login`

Request: `{ "identifier": "0912345678", "password": "..." }`. `identifier` is the phone number or a **verified** email address.

Response `200`:

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresAt": "2026-10-18T03:15:00Z",
  "refreshToken": "q3nV...",
  "refreshTokenExpiresAt": "2026-11-17T03:00:00Z"
}
```

The access token is an HS256 JWT. `sub` is the user id, `roles` holds `USER` or `ADMIN` and `sid` is the session (refresh token family). It lives 15 minutes (`JWT_ACCESS_TTL`), and the refresh token lives 30 days (`JWT_REFRESH_TTL`). Only the SHA-256 hash of the refresh token is stored.

| Status | Code | When |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | Unknown identifier, unverified email or wrong password. The response is identical byte for byte |
| 403 | `ACCOUNT_LOCKED` | Correct password, but the account is `BANNED` |
| 429 | `LOGIN_LOCKED` | `auth.login_max_failed_attempts` (5) failures within `auth.login_lockout_minutes` (15). The block holds even for the right password until the window ends. A successful login resets the counter |

### 1.4 `POST /auth/refresh`

Request: `{ "refreshToken": "..." }`. Response `200` with the same body as login. The presented token is revoked (`ROTATED`), and the new one belongs to the same family.

| Status | Code | When |
|---|---|---|
| 401 | `INVALID_REFRESH_TOKEN` | Unknown or expired token. The client must log in again |
| 401 | `REFRESH_TOKEN_REUSED` | The token was already rotated or revoked. **The whole family is revoked** (`REUSE_DETECTED`) and a security event is logged |
| 403 | `ACCOUNT_LOCKED` | The account was locked |

### 1.5 `POST /auth/logout`

Request: `{ "refreshToken": "..." }`. The response is always `204`. An unknown, expired or already revoked token counts as already logged out.

### 1.6 `POST /auth/password/forgot`

Request: `{ "identifier": "0912345678" }` or `{ "identifier": "an@example.com" }`. Response `202`:

- Phone number: `{ "channel": "SMS", "challengeId": "...", "expiresInSeconds": 300, "resendAfterSeconds": 60 }`. The answer and the OTP limits are the same whether or not the number has an account; the SMS is sent only when it has one.
- Email: `{ "channel": "EMAIL" }`. A link `${PASSWORD_RESET_URL}?token=...` (valid `auth.password_reset_ttl_minutes`, 30 minutes) is sent only when an account **verified** that address.

### 1.7 `POST /auth/password/verify-otp`

Request: `{ "challengeId": "...", "phone": "0912345678", "otp": "123456" }`. Response `200`: `{ "resetToken": "...", "expiresAt": "..." }`. Errors: see the OTP table.

### 1.8 `POST /auth/password/reset`

Request: `{ "resetToken": "...", "newPassword": "NewSecret456" }`. `resetToken` comes from step 1.7 or from the email link. Response `204`. The password is changed, `password_changed_at` is set and every refresh token of the account is revoked (`PASSWORD_CHANGED`). Using the email link also verifies that email.

| Status | Code | When |
|---|---|---|
| 400 | `WEAK_PASSWORD` | The new password breaks the policy. The token stays valid |
| 422 | `RESET_TOKEN_INVALID` | The token is unknown, already used or expired |

### 1.9 `POST /auth/email-verification`

Request: `{ "token": "<token from the link>" }`. Response `204`. The token is single use, valid for `auth.email_verification_ttl_hours` (24 hours), and only for the address it was sent to.

| Status | Code | When |
|---|---|---|
| 422 | `INVALID_VERIFICATION_TOKEN` | Unknown, used or expired token, or the account changed its email since. Request a new link |

### 1.10 `PUT /users/me/password`

Request: `{ "currentPassword": "...", "newPassword": "..." }`. Response `204`. Every other session is logged out (`PASSWORD_CHANGED`); the session of the access token used for the request stays.

| Status | Code | When |
|---|---|---|
| 400 | `CURRENT_PASSWORD_INVALID` | Wrong current password |
| 400 | `PASSWORD_REUSED` | The new password equals the current one |
| 400 | `WEAK_PASSWORD` | The new password breaks the policy |

### 1.11 `PUT /users/me/email` and `POST /users/me/email/verification`

`PUT` request: `{ "email": "an@example.com" }`. Response `202`. The address is stored lowercase and unverified, and a link `${EMAIL_VERIFICATION_URL}?token=...` is emailed. Saving the address that is already verified changes nothing. `POST .../verification` sends a new link to the unverified address. Each address gets at most `auth.account_emails_per_hour` (5) emails per hour; extra requests are dropped silently.

| Status | Code | When |
|---|---|---|
| 400 | `EMAIL_NOT_SET` | Resend requested, but the account has no email |
| 409 | `EMAIL_UNAVAILABLE` | Another account uses the address (generic message) |

---

## 2. Profile (`/users/me`)

### 2.1 `GET /users/me`

Response `200`:

```json
{
  "id": "1b0c...",
  "phone": "+84912345678",
  "email": "an.nguyen@example.com",
  "displayName": "An Nguyen",
  "avatarUrl": "https://storage.example.com/nearby-images/avatars/1b0c.../7f3e....jpg?X-Amz-...",
  "role": "USER",
  "status": "ACTIVE",
  "homeArea": { "latitude": 10.87, "longitude": 106.803 },
  "phoneVerified": true,
  "emailVerified": true,
  "createdAt": "2026-10-18T03:00:00Z"
}
```

`avatarUrl` is a presigned GET URL that is valid for `MINIO_PRESIGN_TTL`. When a field has no value (`email`, `avatarUrl`, `homeArea`), it is left out of the response.

### 2.2 `PUT /users/me`

Request:

```json
{ "displayName": "An Nguyen", "homeArea": { "latitude": 10.870149, "longitude": 106.803151 } }
```

Response `200` with the profile. `homeArea` is the default area. It is optional: leave it out or send `null` to clear it. Latitude must be in [-90, 90] and longitude in [-180, 180]. The point is **rounded to 3 decimals** (about 110 m) before it is stored, so a precise home location is never saved.

### 2.3 Avatar upload (two steps, the image never passes through the backend)

**Step 1:** `POST /users/me/avatar/upload-url`

```json
{ "contentType": "image/jpeg", "sizeBytes": 204800 }
```

Response `200`:

```json
{
  "objectKey": "avatars/1b0c.../7f3e....jpg",
  "uploadUrl": "https://storage.example.com/nearby-images/avatars/...?X-Amz-...",
  "method": "PUT",
  "expiresAt": "2026-10-18T03:10:00Z",
  "requiredHeaders": { "Content-Type": "image/jpeg" }
}
```

The client then sends `PUT uploadUrl` with the raw image bytes and every header listed in `requiredHeaders`.

**Step 2:** `PUT /users/me/avatar` with `{ "objectKey": "avatars/1b0c.../7f3e....jpg" }`. Response `200` with the profile. The backend reads the stored object's real size and type, and any previous avatar is deleted after the change is committed.

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_IMAGE` | Step 1 or 2: the type is not JPEG, PNG or WebP, or the size is over `image.max_bytes` (5 MB). In step 2 the uploaded object is deleted |
| 400 | `INVALID_AVATAR_KEY` | Step 2: the key was not issued for the caller's avatar |
| 400 | `AVATAR_NOT_UPLOADED` | Step 2: nothing was uploaded under the key |

### 2.4 `DELETE /users/me/avatar`

Response `204`. The image is deleted from storage.

---

## 3. Location consent (`/users/me/location-consent`)

Consent must be given before the first precise location is collected. Area verification and listings with a precise location check it through the `identity.LocationConsents` facade, which throws `403 LOCATION_CONSENT_REQUIRED` when there is no consent.

### 3.1 `GET`

Response `200`:

```json
{
  "granted": true,
  "termsVersion": "1.0",
  "consentedAt": "2026-10-18T03:00:00Z",
  "currentTermsVersion": "1.0"
}
```

`granted` is true only while the consent is active **and** was given to `currentTermsVersion` (`consent.location_terms_version`). When the terms version is raised, every user must consent again. A withdrawn consent also has `withdrawnAt`.

### 3.2 `PUT`

Request: `{ "termsVersion": "1.0" }`. Response `200` with the consent. The backend records the time and the terms version.

| Status | Code | When |
|---|---|---|
| 400 | `OUTDATED_TERMS_VERSION` | `termsVersion` is not the current version |

### 3.3 `DELETE`

Response `204`. This publishes `LocationConsentWithdrawn`, after which modules stop collecting precise locations. Data already collected is kept and then disposed of per SRS section 4.4. Withdrawing when there is no active consent does nothing.

---

## 4. Admin: users (`/admin/users`, ADMIN only)

A `USER` token gets `403 ACCESS_DENIED`, and the attempt is logged with the user id and path (FR-ID-04). The first admin account is created on startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

### 4.1 `GET /admin/users?query=&status=&cursor=&limit=`

| Param | Meaning |
|---|---|
| `query` | Optional. Case-insensitive substring match on email or display name. `%` and `_` are matched literally |
| `status` | Optional. `PENDING`, `ACTIVE` or `BANNED` |
| `cursor` | Optional. The `nextCursor` value from the previous page |
| `limit` | Optional. Default 20, max 100 |

Results are sorted newest first. Response `200`:

```json
{
  "items": [
    {
      "id": "1b0c...",
      "email": "an.nguyen@example.com",
      "displayName": "An Nguyen",
      "role": "USER",
      "status": "ACTIVE",
      "emailVerified": true,
      "createdAt": "2026-10-18T03:00:00Z",
      "updatedAt": "2026-10-18T03:00:00Z"
    }
  ],
  "nextCursor": "MTc2MDc1NjQwMDAwMHwxYjBj...",
  "hasMore": true
}
```

| Status | Code | When |
|---|---|---|
| 400 | `INVALID_CURSOR` | The cursor is malformed |

### 4.2 `GET /admin/users/{userId}`

Response `200` with one item in the format above.

| Status | Code | When |
|---|---|---|
| 404 | `USER_NOT_FOUND` | No account has that id |

### 4.3 `POST /admin/users/{userId}/lock`

Request: `{ "reason": "Repeated scam reports" }`. The reason is required, max 200 characters. Response `200` with the user, whose `status` is now `BANNED`.

In one transaction, the backend:

1. sets the account to `BANNED`, so it cannot log in or refresh;
2. revokes every refresh token of the account;
3. writes `audit_log` (`USER_LOCKED`, actor, target, reason). If that write fails, the lock is rejected (FR-MD-04);
4. publishes `UserBanned` through the outbox.

After the commit, access tokens issued before the lock are rejected right away (`401`), not only when they expire.

| Status | Code | When |
|---|---|---|
| 403 | `CANNOT_LOCK_OWN_ACCOUNT` | The admin tries to lock their own account |
| 403 | `CANNOT_LOCK_ADMIN` | The target is another admin |
| 404 | `USER_NOT_FOUND` | No account has that id |
| 409 | `ACCOUNT_ALREADY_LOCKED` | The account is already locked |
| 409 | `CONCURRENT_MODIFICATION` | Another admin changed the same account at the same time |

### 4.4 `POST /admin/users/{userId}/unlock`

Request: `{ "reason": "Appeal accepted" }`. Response `200`. The status goes back to `ACTIVE`, or to `PENDING` if the email was never verified. The backend writes `audit_log` (`USER_UNLOCKED`) and publishes `UserUnbanned`.

| Status | Code | When |
|---|---|---|
| 404 | `USER_NOT_FOUND` | No account has that id |
| 409 | `ACCOUNT_NOT_LOCKED` | The account is not locked |

---

## Domain events (module public API)

| Event | Published when | Expected consumers |
|---|---|---|
| `identity.UserRegistered(userId, registeredAt)` | A phone-verified account is created | Reputation creates the starting snapshot (50 / NEW) |
| `identity.UserBanned(userId, lockedAt)` | An admin locks an account | Listing hides the user's listings. Transaction cancels open transactions without penalizing the other party (FR-ID-05) |
| `identity.UserUnbanned(userId, unlockedAt)` | An admin unlocks an account | Listing may show the hidden listings again |
| `identity.LocationConsentWithdrawn(userId, withdrawnAt)` | The user withdraws location consent | Area Verification stops collecting locations |

These events go through the `event_publication` outbox, so consumers must be idempotent. The Listing and Transaction consumers belong to those modules and are not part of this change.

## Runtime parameters (`app_parameters`)

| Key | Default | Used by |
|---|---|---|
| `auth.login_max_failed_attempts` | 5 | Login lockout |
| `auth.login_lockout_minutes` | 15 | Login lockout window |
| `auth.registrations_per_hour_per_ip` | 10 | Registration rate limit |
| `auth.account_emails_per_hour` | 5 | Verification and reset emails per address |
| `auth.email_verification_ttl_hours` | 24 | Verification link lifetime |
| `auth.password_reset_ttl_minutes` | 30 | Reset link lifetime |
| `consent.location_terms_version` | 1.0 | Current location consent terms |
| `consent.terms_version` / `consent.privacy_version` | 1.0 | Current Terms and Privacy Policy versions accepted at registration |
| `otp.ttl_s` / `otp.max_attempts` / `otp.resend_s` / `otp.max_per_day` | 300 / 5 / 60 / 5 | SMS one-time codes |
| `image.max_bytes` | 5242880 | Avatar size limit |
