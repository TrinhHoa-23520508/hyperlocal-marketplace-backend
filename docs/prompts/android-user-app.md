# Prompt: build the Nearby Android app (accounts part)

Read [`README.md`](README.md) first for the context to attach and the workflow. Copy everything below the line into your AI coding tool, together with the business flows F1–F12 of [`docs/api/identity-api.md`](../api/identity-api.md#business-flows-per-screen) and [`docs/api/openapi.yml`](../api/openapi.yml).

---

## Role and goal

You are a senior Android engineer. Build the account part of **Nearby**, a hyperlocal second-hand marketplace for Vietnam: onboarding, registration with an SMS code, login, session handling, forgot password, profile, settings (password, email, consents, blocked users, account deletion), public profile and the sanction notice. The backend REST API exists; you build the Android client only.

Deliver a working, tested app. UI copy is **Vietnamese** with full diacritics; code and identifiers are English. Only build what the flows describe; listings, chat, transactions, areas and payments come later and need no placeholder screens beyond a simple home (A06) stub.

## Tech stack (fixed)

- Kotlin 2.x, Jetpack Compose with Material 3, single activity, Navigation Compose. `minSdk` 26, `targetSdk` latest stable.
- MVVM: one ViewModel per screen exposing an immutable `StateFlow<UiState>`. Events are function calls on the ViewModel.
- Hilt for dependency injection.
- Retrofit + OkHttp + kotlinx.serialization.
- Coroutines and Flow. Inject `CoroutineDispatcher`; never use `GlobalScope`.
- DataStore encrypted with Tink for the refresh token. Coil for images. Firebase Cloud Messaging for the device push token.
- Tests: JUnit 5, MockK or hand-written fakes, Turbine, `kotlinx-coroutines-test`, MockWebServer, `compose-ui-test`.

## Architecture rules (from the project's clean-code skill, mandatory)

- Packages:
  - `ui`: screens, navigation, theme.
  - `domain`: plain Kotlin use cases and models, **no Android imports**.
  - `data`: Retrofit, repositories, token storage, error mapping.
  - `di`: Hilt modules.
  - `fcm`: push token handling.
- One use case does one thing, through `operator fun invoke`. Examples: `RegisterUseCase`, `LoginUseCase`, `RefreshSessionUseCase`, `ChangeAvatarUseCase`.
- Composables have no business logic and never call repositories.
  - Split each screen into a stateful `XxxRoute` (connects to the ViewModel) and a stateless `XxxScreen(state, callbacks)` with previews.
  - Every composable takes `modifier: Modifier = Modifier` as its first optional parameter.
- RFC 9457 errors are mapped **once** in `data` into a sealed `DomainError` (for example `InvalidCredentials`, `OtpInvalid(remainingAttempts)`, `RateLimited(retryAfter)`, `AccountSuspended(reason, until)`, `Validation(fieldErrors)`, `Unknown(detail)`). `ui` only sees domain errors.
  - Always branch on the problem `code`, never on `detail` or the HTTP status alone.
- No `!!`. Use `Result` or a sealed type for operations that can fail. Never log tokens, OTPs, passwords or exact coordinates.
- No comments that repeat the code.

## Backend facts

- Base URL from `BuildConfig.API_BASE_URL`:
  - debug on the emulator: `http://10.0.2.2:8080`;
  - debug on a real phone: `http://<LAN-IP>:8080`, and allow cleartext for debug only;
  - release: HTTPS.
- Every path is under `/api/v1`. JSON timestamps are ISO-8601 UTC; show them in `Asia/Ho_Chi_Minh`.
- Auth: `Authorization: Bearer <accessToken>` (JWT, 15 min). The refresh token lives 30 days and **rotates on every refresh**.
- Errors: `application/problem+json` with `code`. Some codes add members:
  - `remainingAttempts` (`OTP_INVALID`);
  - `reason`, `suspendedUntil` (`ACCOUNT_SUSPENDED`, `ACCOUNT_BANNED`);
  - `nextAllowedAt` (`NAME_CHANGE_TOO_SOON`);
  - `blockers` (`ACCOUNT_DELETION_BLOCKED`);
  - `violations` (`WEAK_PASSWORD`);
  - `errors[{field, message}]` (`VALIDATION_FAILED`).
- `429` responses carry `Retry-After` in seconds.
- The OpenAPI file lists every endpoint, schema and error per endpoint. The flows F1–F12 say when to call what and how each error looks on screen. **Follow the flow tables exactly**; each row is an acceptance test.

## Session handling (the part that most often goes wrong)

- `TokenStore` keeps the access token in memory and the refresh token in encrypted DataStore.
- An OkHttp `Authenticator` handles a `401` by calling `POST /auth/refresh` **once**, behind a `Mutex`:
  - Requests that fail while a refresh is running wait for it and reuse the new token. Two parallel refreshes with the same token make the backend revoke the whole session (`REFRESH_TOKEN_REUSED`).
  - Always save **both** returned tokens.
- If the refresh itself fails with `INVALID_REFRESH_TOKEN` or `REFRESH_TOKEN_REUSED`: clear the store, emit a "session ended" event and navigate to A02 with "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại".
- A `403 ACCOUNT_SUSPENDED` or `ACCOUNT_BANNED` from login or refresh navigates to A36 (sanction notice).
- Logout sends `{refreshToken, fcmToken}` and always clears local data, even when offline.
- Exclude `/auth/**` and the storage upload URL from the bearer interceptor.

## Screens (SRS codes) and the flows they implement

| Screen | Purpose | Flows |
|---|---|---|
| Splash | Restore the session | F1 |
| A01 Giới thiệu và lý do xin quyền | Explain the location consent, then ask the Android permission | F9 |
| A02 Đăng nhập | Phone or verified email + password | F3 |
| A03 Đăng ký | Phone, Terms and Privacy checkboxes (show versions), marketing switch | F2 |
| A04 Nhập OTP | 6-digit field with SMS autofill (SMS User Consent API), resend countdown; reused by registration and forgot password | F2, F5 |
| A05 Quên mật khẩu | Phone or email, then new password | F5 |
| A06 Trang chủ | Stub only, entry to the profile | — |
| A11 Hồ sơ công khai | Name, avatar, phone badge, join date, block action | F10 |
| A31 Hồ sơ của tôi | Own profile, entry to edit and settings, logout | F6, F7 |
| A32 Sửa hồ sơ | Display name (30-day rule), default area, avatar upload | F7 |
| A33 Cài đặt và quyền riêng tư | Change password, email and verification, consents, blocked users, delete account | F8–F11 |
| A36 Thông báo xử phạt | Reason, end date, appeal placeholder | F12 |

Deep links (declare them in the manifest and route them in Navigation):

- `nearby://verify-email?token=...` → F8 step 3.
- `nearby://reset-password?token=...` → F5 step 2'.

## Details that must be right

- **Phone input**: accept `0912 345 678`, `84…`, `+84…`; show it as `0912 345 678`. Let the backend normalise and answer `INVALID_PHONE`.
- **OTP**: 6 digits only. The resend button stays disabled for `resendAfterSeconds`, or for `Retry-After` on `429`. After `OTP_EXPIRED` or `OTP_ATTEMPTS_EXCEEDED`, go back to the request step.
- **Password rules**: at least 8 characters, letters and digits, at most 72 UTF-8 bytes. Show them live as hints, but treat the server's `violations` as the truth.
- **Avatar upload (F7)**:
  1. Read the MIME type with `ContentResolver.getType`, defaulting to `image/jpeg`, and the byte size. Refuse anything other than JPEG, PNG or WebP, or over 5 MB, before calling the API.
  2. Call `upload-url`.
  3. `PUT` the raw bytes to `uploadUrl` with a separate OkHttp client that has no auth interceptor. Set `Content-Type` exactly from `requiredHeaders`, and use `RequestBody` with a null media type so OkHttp adds no charset.
  4. Confirm with the `objectKey`.
- **Avatar display**: `avatarUrl` expires after about 10 minutes. Load it with Coil, and refetch the profile instead of persisting the URL.
- **Display name**: if `displayNameChangeAllowedAt` is present, the name field is read-only with the date shown.
- **Account deletion**: explain the 7-day grace period before asking for the password. After `202`, log out locally and show the date from `deleteAfter`.
- **Blocking**: never show the other user that they are blocked. Hide the block button on your own profile and on deleted accounts.
- **Accessibility**: content descriptions on icons, touch targets ≥ 48 dp, Vietnamese TalkBack labels, support font scaling up to 200 %.

## Tests (required)

- Unit tests:
  - The error mapper: every `code` listed in the flows maps to a `DomainError`, including the extra members.
  - Each use case.
  - Each ViewModel: initial, loading, success, and every error row of its flow, with a fake repository and Turbine.
- MockWebServer tests:
  - The `Authenticator`: three parallel `401`s lead to exactly one refresh call; `REFRESH_TOKEN_REUSED` clears the session.
  - The avatar upload: the PUT carries exactly `Content-Type: image/jpeg` and no `Authorization` header.
- Compose UI tests:
  - Registration happy path.
  - OTP resend countdown.
  - Login error messages.
  - Name field locked when `displayNameChangeAllowedAt` is set.
- Report JaCoCo coverage for `domain` and the ViewModels (target 80 %).

## Acceptance criteria

- Every row of flows F1–F12 behaves as its table says, against the real backend on `10.0.2.2:8080`. Use `SMS_PROVIDER=log` and read the code from the backend log.
- No token, OTP or password appears in Logcat; the refresh token is only in encrypted storage.
- Rotating the screen or killing the process keeps the session and the form state (`SavedStateHandle`).
- Lint passes, all tests pass, and a release build with R8 works (keep rules for kotlinx.serialization models).

## Out of scope for this prompt

Listings, search, map, chat, offers, transactions, payments, reviews, area verification (A06–A30 except the A06 stub), reports and disputes (A34, A35), help centre (A37), change phone number (FR-ID-10, backend not built), active sessions list (FR-ID-06, backend not built).
