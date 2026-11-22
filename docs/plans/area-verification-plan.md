# Plan: Identity audit and the Area Verification module

Sources: SRS `docs-local/SRS_Hyperlocal_Marketplace_LaTeX` (section 3.2 *Xác minh khu vực*, table `tab:sm-area`, appendix C UC-AR-01..04, appendix D *Kiểm tra xác minh khu vực*, section 4.2 *Module area*, table `tab:params`, appendix F OI-06), and the rules in `.claude/clean-code-skill` (SKILL.md and references 01–08). Every step below follows the skill workflow: find what exists, write the test first, put code in the right layer, run `./gradlew build`, then update the API contract.

---

## Part 1. Is Identity and Account complete?

**Short answer:** everything Identity can do alone is done and tested. The one R1 Must still missing, FR-ID-20 (trading gate), needs verified areas, so it can only be finished together with the Area module. A few parts of FR-ID-12/13 are waiting on Reputation and Listing.

Build: `./gradlew build` passes (format check, `ApplicationModules.verify()`, JaCoCo gate). Coverage of the current code: 97.6 % of lines, 91.4 % of branches. Docker was off during this audit, so Gradle reused the last green test run. Run the build again with Docker on before merging.

| FR | Priority / release | Status | Notes |
|---|---|---|---|
| FR-ID-01 Register by phone | M / R1 | Done | `POST /auth/register/otp`, `POST /auth/register` |
| FR-ID-02 OTP policy | M / R1 | Done | Hashed in Redis; all `otp.*` limits |
| FR-ID-03 Login | M / R1 | Done, one gap | Lockout per account and per IP. **Gap:** the SRS says login also registers the device's FCM token. Nothing registers it yet (logout already removes it through `DeviceSignedOut`). This belongs with Notification (FR-NT). |
| FR-ID-04 Rotating refresh | M / R1 | Done | Family revoke on reuse |
| FR-ID-05 Logout | M / R1 | Done | `DeviceSignedOut` → `chat/DeviceTokenCleanup` |
| FR-ID-06 Sessions | S / R2 | Not built | R2, fine |
| FR-ID-07 Reset password | M / R1 | Done | |
| FR-ID-08 Change password | S / R1 | Done | Keeps the current family |
| FR-ID-09 Email | C / R2 | Done early | |
| FR-ID-10 Change phone | S / R2 | Not built | R2, fine |
| FR-ID-11 Edit profile | M / R1 | Done | Presigned avatar, 30-day name rule |
| FR-ID-12 Own profile + trust panel | M / R1 | **Partial** | Needs reputation score/tier, verified areas with expiry, and reviews. Add the areas part in this plan (Phase 6). Reputation and reviews come with the Reputation module. |
| FR-ID-13 Public profile | M / R1 | **Partial** | Name, avatar, badge, join date and deleted state are done. Tier/score, area label (`locality_name`), active listings and positive reviews come with Area (label), Listing and Reputation. |
| FR-ID-14 Block | S / R1 | Done | `UserBlocks` public API |
| FR-ID-15 Consents | M / R1 | Done | `LocationConsents` public API |
| FR-ID-16 Delete account | S / R1 | Done | `AccountDeletionBlocker` SPI for later modules |
| FR-ID-17 Data export | C / R2 | Not built | R2, fine |
| FR-ID-18 Roles, object-level auth | M / R1 | Done for USER/ADMIN | Fine-grained `user_roles` belongs to FR-AD-02 (Admin Console). Every later module must return 404 for other users' private resources. |
| FR-ID-19 Account status | M / R1 | Done | Same state machine as SRS table `tab:sm-account`, plus SUSPENDED → BANNED. |
| FR-ID-20 Trading gate | **M / R1** | **Missing** | Needs areas. Phase 4 of this plan. |

Leftovers found during the audit:

1. `users.home_area`, `users.verified_area_count` (V2) and `HomeArea` / `homeArea` on `GET/PUT /users/me` are not in the SRS (SRS section 4.6 marks them "not applied"). Once the Area module exists, users' areas live in `user_areas`, so this field is a second, unverified copy. Remove it in Phase 6. This is a **breaking change** to `ProfileResponse`/`UpdateProfileRequest`.
2. `shared/web/idempotency/IdempotencyInterceptor` answers a repeated key with `409 DUPLICATE_REQUEST`. The SRS (UC-AR-01 A2, section 5.4) says a repeated key with the same body must get the **original response**, and a different body must get `409 IDEMPOTENCY_CONFLICT`. No endpoint uses `@Idempotent` yet, so fixing it now breaks nothing (Phase 0).
3. The V5 tables `user_areas`, `area_verifications` and `device_signals` are from the old design. They have no status, slot, expiry, range, kind, result or reason codes. The seed sets `area.verification_validity_days = 180` where the SRS says 30 (OI-06). Phase 0 replaces them.
4. Scheduled jobs use plain `@Scheduled` with no ShedLock. With more than one instance (NFR-20) they would run twice. All jobs here are written to be idempotent, so this is safe for now. Adding ShedLock is a separate change.

**Why Area Verification is next:** FR-ID-20 cannot be finished without it. Listing (UC-LS-01), Chat (UC-CH-01) and Transaction (UC-TX-01) all call the trading gate and the range rule (FR-AR-09). The SRS business flow is also identity → area → listing. Building Listing first would mean stubbing the gate.

---

## Part 2. Area Verification module

Scope: FR-AR-01..10 (R1). FR-AR-11 Play Integrity is R2: keep the `PLAY_INTEGRITY` signal code but do not call Google. UC-AR-01..04, UC-SY-05, UC-SY-11, plus FR-ID-20 and the area half of FR-ID-12.

Module: `vn.edu.uit.nearby.nearby_backend.areaverification`. `allowedDependencies = { "shared", "identity" }` is already declared. Layers: `web`, `application`, `domain`, `infrastructure`. Public API types sit at the module root.

### Decisions to confirm (recommended option first)

| # | Question | Recommendation | Why |
|---|---|---|---|
| D1 | Where does the trading gate (FR-ID-20) live? | In **identity** as the `TradingGate` public API. Identity also defines an SPI `TradingAreaStatus` that areaverification implements, the same pattern as `AccountDeletionBlocker`. | Area already depends on identity. If identity listened to `AreaVerified`, the two modules would depend on each other, and `ApplicationModules.verify()` would fail. The SPI keeps the gate where the SRS puts it without that cycle. |
| D2 | Use the Redis gate cache that the SRS mentions? | **Not in the first version.** Compute the gate on each call (one user row, one consent row, one indexed area query). Add the cache only if NFR-06 load tests show a need. | Fewer moving parts. With no cache there is nothing to invalidate, and FR-AR-03 ("evaluated without it within 1 hour") holds right away. Record this as a deviation in `area-api.md`. |
| D3 | Grid cell size for area centres ("gắn tâm vào ô lưới"; the SRS gives no size) | A new parameter `area.grid_cell_m` = 300. Use the same Web Mercator snapping as the listing blur (appendix D), through one shared `GridSnapper` in `shared/geo`. | One rule for both modules (reference 03: "Làm mờ toạ độ công khai → một lớp"). Area and Listing both need it, so it may go in `shared`. |
| D4 | Remove `home_area` from profile | Yes, in Phase 6, recorded as a breaking change. | Removes the second copy of a user's area. |
| D5 | Count area changes for FR-AR-06 in Redis or in the DB? | Daily attempts in Redis (`RateLimiter`, counts rejected attempts too). The 3-changes-per-30-days limit from `area_verifications` (accepted INITIAL/REPLACEMENT in the last 30 days). | A 30-day window should survive a Redis restart, and the rows already exist. |

### Phase 0. Groundwork (shared + migration)

> **Status: done (2026-11-22).** Delivered: `V12__area_verification_v2.sql`; the idempotency upgrade (`IdempotencyBufferingFilter`, `CachedBodyRequest`, `IdempotencyRecord`, rewritten `IdempotencyInterceptor`, header `Idempotent-Replayed`; only `2xx` and `422` are kept for replay); `GeoPoint.distanceMetersTo`, `GridSnapper`/`GridCell`; the new `ErrorCodes`. Tests: `IdempotencyIntegrationTests`, `IdempotencyRecordTests`, `CachedBodyRequestTests`, `GeoPointTests`, `GridSnapperTests`, `AreaSchemaIntegrationTests`. Changes from the steps below: the database has no `slot`/`version` race test yet (Phase 2), and the cell code is `"<size>:<column>:<row>"` with a minimum cell size of 10 m so it fits in `varchar(20)`.

1. **Migration `V12__area_verification_v2.sql`**. The V5 tables hold no data and no code uses them, so drop and recreate them following SRS section 4.2:
   - `user_areas`: `id, user_id, slot smallint CHECK 1..2, label varchar(30) NOT NULL, locality_name varchar(100), center geography(Point,4326) NOT NULL, grid_cell varchar(20) NOT NULL, range_level varchar(6) DEFAULT 'MEDIUM' CHECK (NEAR/MEDIUM/FAR), is_active boolean DEFAULT false, status varchar(10) CHECK (VERIFIED/EXPIRING/EXPIRED/REMOVED), verified_at, expires_at, reminder_sent_at, last_verification_id, removed_at, created_at, updated_at, version`.
   - Database guards: `UNIQUE (user_id, slot) WHERE status <> 'REMOVED'`, which enforces "at most 2 areas" even under concurrent requests (FR-AR-02), and `UNIQUE (user_id) WHERE is_active AND status <> 'REMOVED'`. Indexes: GIST on `center`, `(status, expires_at)` for the jobs.
   - `area_verifications` (insert-only): `id, created_at, user_id, user_area_id NULL, kind (INITIAL/RENEWAL/REPLACEMENT), result (ACCEPTED/REJECTED), reason_codes varchar(30)[] DEFAULT '{}', reported_location NULL, grid_cell, accuracy_m, fix_time, distance_m, implied_speed_kmh, mock_flag, is_mock_suspected, idempotency_key uuid, app_version, location_purged_at`. Indexes: `(user_id, created_at DESC)` and `(created_at) WHERE reported_location IS NOT NULL`. Leave out the SRS `(id, created_at)` partitioning key for now and note it.
   - `device_signals`: `id, verification_created_at, area_verification_id, user_id, signal_code` (CHECK list as in SRS, including `PLAY_INTEGRITY`, `EMULATOR`, `ROOT`), `severity (HARD/SOFT), raw_value varchar(200), detected_at`.
   - `app_parameters`: delete `area.verification_validity_days` and `area.retention_days`. Add `area.validity_days=30`, `area.renewal_reminder_days=3`, `area.renew_radius_m=1500`, `area.max_fix_age_s=120`, `area.max_speed_kmh=300`, `area.max_attempts_per_day=5`, `area.max_changes_per_30d=3`, `area.range_levels_km=2,4,6`, `area.attempt_retention_months=24`, `area.grid_cell_m=300`, `location.retention_days=90`, `listing.pin_radius_m=1500`. Keep `area.max_count=2` and `area.accuracy_threshold_m=100`. This closes OI-06.
2. **Idempotency upgrade** (`shared/web/idempotency`). Store `{requestBodyHash, status, body}` once the handler finishes.
   - Same key, same body: replay the stored response.
   - Same key, different body: `409 IDEMPOTENCY_CONFLICT`.
   - Same key while the first request is still running: `409 DUPLICATE_REQUEST`.
   - Missing key: `400 IDEMPOTENCY_KEY_REQUIRED`. The key must be a UUID (SRS table `tab:fieldrules` ARE row).
   - Tests: replay, conflict, in-flight, 5xx releases the key, and a 4xx such as 422 REJECTED is stored and replayed. The 4xx case matters because a rejected attempt is final.
3. **`shared/geo`**: add `GeoPoint.distanceMetersTo(GeoPoint)` (haversine, pure function) and `GridSnapper` (Web Mercator snapping plus a cell code). Test them with known distances and boundaries.
4. **`ErrorCodes`**: `AREA_VERIFICATION_REJECTED`, `AREA_RATE_LIMITED`, `AREA_LIMIT_REACHED` (409, the FR-AR-02 prompt to remove or replace), `AREA_NOT_FOUND`, `OUT_OF_RENEW_RADIUS`, `AREA_HAS_OPEN_TRANSACTIONS`, `AREA_STATUS_CONFLICT`, `INVALID_RANGE`, `OUT_OF_RANGE`, `PIN_OUTSIDE_AREA`, `TRADING_GATE_FAILED`, `IDEMPOTENCY_CONFLICT`.

### Phase 1. Domain (pure Java, written test-first, ≥ 90 % branch coverage)

> **Status: done (2026-11-22).** Code: `areaverification/domain` (`AreaStatus`, `UserArea` entity, `RangeLevel`, `RangeLevels`, `AreaLabel`, `AreaSlots`, `GpsFix`, `GpsReading`, `DeviceSignalReport`, `RenewalRadius`), `areaverification/domain/verification` (`LocationRiskSignal` with `MockLocationSignal`, `LowAccuracySignal`, `StaleFixSignal`, `ImpossibleSpeedSignal`, `DeviceReportSignal`, `TimeDriftSignal`; `AreaVerificationPolicy`, `Verdict`, `RiskFinding`, `DetectedSignal`, codes and severities), and `application/AreaParameters`. Tests: 135 unit tests. Coverage of the area domain is 100 % of lines and 100 % of branches.
>
> Readings of the SRS (SOT) made here:
> 1. **No VERIFIED → EXPIRED edge.** `tab:sm-area` and `sm_area.puml` only have EXPIRING → EXPIRED. To still meet the FR-AR-03 acceptance criterion ("EXPIRED at most 1 hour after `expires_at`"), the hourly job in Phase 2 moves an overdue VERIFIED area through EXPIRING to EXPIRED in one run.
> 2. **`is_mock_suspected`** is set by MOCK_FLAG and IMPOSSIBLE_SPEED (the data dictionary: "the mock flag and the hard signals combined") and by MOCK_APP (appendix D step 4: "signals that suggest spoofing"). Emulator, root, developer options and time drift are stored but don't set it.
> 3. **TIME_DRIFT** (the soft signal "lệch giờ"): the SRS gives no limit, so the fix-age window `area.max_fix_age_s` is reused in the other direction. A fix more than 120 s in the future is recorded as SOFT and never rejected.
> 4. **All hard reasons are returned**, in SRS order (mock, accuracy, fix age, speed), instead of only the first one.
> 5. **Only a VERIFIED or EXPIRING area can become the active area** (FR-AR-08 precondition: "at least one valid area"). An EXPIRED area can still be renamed, have its range changed, or be renewed.
> 6. **Distances** for renewal (1 500 m) and speed use the spherical haversine formula in `GeoPoint`. That is within about 0.5 % of PostGIS geodesic distance. The range rule FR-AR-09 will use PostGIS `ST_DWithin` (Phase 3).
> 7. **Not produced yet:** `IP_MISMATCH` needs a GeoIP source the SRS doesn't name, and `PLAY_INTEGRITY` is R2 (FR-AR-11). Both codes exist so they can be added later as new `LocationRiskSignal`s.
> 8. **Open question:** the SRS doesn't say how `user_areas.locality_name` (the public area name, FR-ID-13) is produced. It stays null until decided, at the latest in Phase 6.

| Class | Responsibility | Key tests |
|---|---|---|
| `AreaStatus` enum | Transitions from SRS table `tab:sm-area`; `grantsTrading()` is true only for VERIFIED and EXPIRING | `@ParameterizedTest` covering every allowed move, plus forbidden moves from REMOVED |
| `UserArea` (entity) | `verifiedAt(...)`, `renew(now, validity)`, `markExpiring(now)`, `expire(now)`, `remove(now)`, `activate()`, `deactivate()`, `changeRange(RangeLevel)`, `rename(label)` | State table; renewing an EXPIRED area brings it back; any move from REMOVED → `AREA_STATUS_CONFLICT` |
| `RangeLevel` enum | NEAR/MEDIUM/FAR, with km read from `area.range_levels_km` | Default MEDIUM; an unknown value → `INVALID_RANGE` |
| `GpsReading` record | location, `accuracyMeters`, `fixTime`, `isMockLocation` | Field validation |
| `DeviceSignals` record | emulator, rooted, devOptions, `clockSkewSeconds`, appVersion. All are nullable, and null means "unknown" (FR-AR-05). | |
| `LocationRiskSignal` (Strategy, reference 02) | `Optional<RiskFinding> inspect(GpsReading, VerificationContext)` | |
| Hard signals: `MockLocationSignal`, `LowAccuracySignal`, `StaleFixSignal`, `ImpossibleSpeedSignal` | Reason codes MOCK_LOCATION, LOW_ACCURACY, STALE_FIX, IMPOSSIBLE_SPEED | Boundaries: accuracy 100 m accepted, 100.1 rejected; fix age 120 s accepted, 121 s rejected; 300 km/h accepted, 301 rejected; no earlier accepted fix → speed check skipped |
| Soft signals: `EmulatorSignal`, `RootSignal`, `DevOptionsSignal`, `TimeDriftSignal` | Record only, never reject. `IP_MISMATCH` needs GeoIP, so it is deferred. | "A single soft signal never changes the result" |
| `AreaVerificationPolicy` | Runs every signal. Any hard finding → REJECTED with **all** hard reason codes; soft findings are attached. Sets `isMockSuspected`. | Accepted when clean; first-violation behaviour; several reasons; a soft-only finding stays accepted |
| `RenewalRule` | `distanceMetersTo(center) ≤ area.renew_radius_m` | 1 499 m accepted, 1 501 m rejected (FR-AR-04 acceptance criteria) |
| `AreaParameters` (application) | Typed wrapper over `AppParameters`, the same style as `IdentityParameters` | |

The time for fix age and speed comes from the injected `Clock`.

### Phase 2. Application services

> **Status: done (2026-11-22).** Added:
> - `application`: `AreaVerificationService` (verify, A4 renew-nearby, replace, renew), `AreaManagementService` (list, rename, select active, remove), `AttemptLimits`, `AttemptRecorder`, `VerificationEligibility`, `AreaRemovalGuard`, the jobs `AreaRenewalReminderJob` (09:00), `AreaExpiryJob` (hourly) and `VerificationDataRetentionJob` (02:00), all in `Asia/Ho_Chi_Minh`, and `AreaAccountDeletionListener`.
> - `domain/attempt`: the insert-only `AreaVerificationAttempt` and `RecordedDeviceSignal`.
> - `infrastructure`: the two repositories.
> - Module root: the events `AreaVerified`, `AreaExpiring`, `AreaExpired`, `AreaRemoved` and the SPI `AreaRemovalBlocker`.
> - Identity root: `AccountStanding`, pulled forward from Phase 4.
> - V12 change: `area_verifications.user_area_id` is `ON DELETE SET NULL`. V12 was edited in place because it has not been committed or released.
>
> Tests: 43 integration tests against PostGIS and Redis, plus unit tests. Coverage: 100 % of lines and 89 % of branches in `application`; 100 % in the domain packages.
>
> One bug was found and fixed by these tests: the scheduled `run()` methods called `runAt()` on the same object, which bypasses the `@Transactional` proxy, so the jobs would have failed in production with "No active transaction".
>
> Readings of the SRS (SOT) made in this phase:
> 1. **Order of checks** (appendix D steps 1, 2 and 6, with UC-AR-01 A4):
>    1. eligibility;
>    2. a fix within 1 500 m of a live area renews that area;
>    3. otherwise, for a new or replacing area: the area count (`409 AREA_LIMIT_REACHED` with `areaIds`), the replace target, the 30-day change limit and the removal guard;
>    4. the daily attempt counter;
>    5. the anti-spoofing policy.
>
>    Refusals at steps 1–3 are not attempts: nothing is stored and the daily counter is not used.
> 2. **Two counters:** `area.max_attempts_per_day` counts every evaluated attempt, rejected ones included (FR-AR-06, "đếm cả lần thử bị từ chối"). `area.max_changes_per_30d` counts only *accepted* INITIAL and REPLACEMENT attempts. Counting rejected GPS attempts there would lock a user out for 30 days after three bad fixes. ⚠ Product decision to confirm.
> 3. **"Per day"** is a rolling 24-hour window starting at the first attempt (the Redis counter), not a calendar day. ✅ Confirmed by the user on 2026-11-22 (DEC-AR-5).
> 4. **Failed account precondition:** the SRS has no error code for an account that isn't ACTIVE or has no verified phone when verifying. The service answers `403 ACCESS_DENIED`.
> 5. **Default label:** the area gets "Khu vực {slot}" until the user names it (UC-AR-01 step 9 asks for the label after verification; the column is NOT NULL).
> 6. **Removing the active area:** the user's other valid area becomes the active one, so the feed keeps a center.
> 7. **Renewal distance check:** `OUT_OF_RENEW_RADIUS` is checked only when the hard rules pass. A rejected renewal is stored with the area id and its distance.
> 8. **Account anonymisation** (SRS 4.5, "xoá khu vực"): the user's areas are deleted. Their attempts stay as evidence, with the precise fixes cleared.

1. **`AreaVerificationService.verify(VerifyAreaCommand)`** (UC-AR-01, following appendix D steps 1–7):
   1. Preconditions. Call the new identity API `AccountStanding.requireActiveWithVerifiedPhone(userId)` (Phase 4), then `LocationConsents.requireConsent(userId)` → `403 LOCATION_CONSENT_REQUIRED`.
   2. Limits. The daily attempt counter (5) → `429 AREA_RATE_LIMITED` with reason `DAILY_ATTEMPTS` and `Retry-After`. Then the 30-day change count (3) → `AREA_CHANGES`.
   3. Evaluate the policy.
   4. **Always save the attempt**, rejected ones included. The service returns an `AreaVerificationOutcome` and does not throw inside the transaction, so the attempt row commits. The controller turns REJECTED into `422 AREA_VERIFICATION_REJECTED` with `reasons[]` and Vietnamese guidance keys.
   5. Accepted and within `renew_radius_m` of an existing non-removed area: renew that area (alternate flow A4).
   6. Otherwise, if 2 areas exist and no `replaceAreaId` was given: `409 AREA_LIMIT_REACHED`, listing the existing areas so the client can offer UC-AR-03.
   7. Otherwise create the area in a free slot, snapped to the grid. The first area becomes active with range MEDIUM. Then publish `AreaVerified`.
2. **`renew(areaId, reading)`** (UC-AR-02): owner check (`404 AREA_NOT_FOUND`), limits, policy, `RenewalRule`. Too far → save a RENEWAL attempt with `OUT_OF_RENEW_RADIUS` and return 422 with the distance rounded to 100 m. Success → VERIFIED, new `expires_at`, `AreaVerified`.
3. **`remove(areaId)`** (UC-AR-03, FR-AR-07): ask an SPI `AreaRemovalBlocker` (public interface in areaverification, implemented later by Transaction; empty list until then). Blocked → `409 AREA_HAS_OPEN_TRANSACTIONS` with the listing ids. Otherwise mark the area REMOVED. If it was the active area, activate the other one. Publish `AreaRemoved`; Listing will hide that area's listings with `hidden_reason = AREA_REMOVED`.
4. **Replace** (UC-AR-03): `verify` with `replaceAreaId` removes the old area and creates the new one in **one transaction**, with kind REPLACEMENT. It counts toward the 30-day change limit.
5. **`updateLabel`**, and **`selectActive(areaId, rangeLevel)`** (UC-AR-04, FR-AR-08). The area must be VERIFIED or EXPIRING.
6. **Jobs** (all idempotent; times come from `Clock`):
   - `AreaReminderJob`: daily at 09:00 `Asia/Ho_Chi_Minh`. Moves VERIFIED areas with ≤ 3 days left to EXPIRING, sets `reminder_sent_at` once per cycle and publishes `AreaExpiring`.
   - `AreaExpiryJob`: hourly. Moves VERIFIED or EXPIRING areas past `expires_at` to EXPIRED and publishes `AreaExpired`.
   - `VerificationLocationPurgeJob`: daily at 02:00. Clears `reported_location` and sets `location_purged_at` after 90 days. Also deletes attempts older than 24 months.
7. **Listeners**: on `AccountDeleted`, remove all of the user's areas and purge their locations. Test that handling the same event twice gives the same result.

### Phase 3. Public API for other modules (facades at the module root)

> **Status: done (2026-11-22).** Added at the module root:
> - `InteractionRange`: `isWithinRange` and `requireWithinRange` (`403 OUT_OF_RANGE`), plus `allowsNewContacts(listingAreaId)`.
> - `ListingPinRule`: `isPinInsideArea` and `requirePinInsideArea` (`422 PIN_OUTSIDE_AREA`).
> - `FeedArea` and `FeedCenter`.
>
> One implementation, `application/AreaSpatialRules`, serves all three, using two native `ST_DWithin` queries in `UserAreaRepository`. Tests: `AreaSpatialRulesIntegrationTests`, 22 cases against PostGIS (3.9 / 4.1 km at MEDIUM; NEAR and FAR at ±100 m; pin at 1 499 / 1 501 m; EXPIRING, EXPIRED, REMOVED and other users' areas; the feed center). The new code has 100 % coverage.
>
> Readings of the SRS (SOT) made in this phase:
> 1. **Distances use the sphere.** `ST_DWithin(..., use_spheroid => false)` uses PostGIS's mean-Earth sphere, the same model as `GeoPoint.distanceMetersTo`, so the pin rule and the renewal rule (both 1 500 m) agree exactly. The ellipsoid would be about 0.5 % shorter near 10°N, about 8 m at 1 500 m.
> 2. **The range counts any valid area of the buyer** (VERIFIED or EXPIRING), each with its own `range_level`, not only the active one (FR-AR-09: "một khu vực hợp lệ của người mua").
> 3. **One pin error.** An area that is not the seller's, not valid, or a pin outside it all answer `422 PIN_OUTSIDE_AREA`, the single code FR-LS-01 gives for "khu vực không hợp lệ / ghim ngoài khu vực".
> 4. **Expired listing areas.** `allowsNewContacts` returns a boolean, and each caller answers with its own code: `409 LISTING_AREA_EXPIRED` in FR-TX-01, and a 403 with a reason in FR-CH-01.
> 5. **The feed keeps an expired active area as its center**, because browsing doesn't need a valid area (FR-AR-03 restricts trading only).
> 6. **Deferred:** `AreaSummaries` (FR-ID-12/13) moves to Phase 6, and `TradingAreaStatus` to Phase 4, where identity defines the SPIs.
> 7. **Consumers must add `areaverification` to `allowedDependencies`** in their `package-info.java` when they start calling these facades. `listing` and `transaction` don't list it yet; `chat` already does.
>
> **Clean-code review (after linking the clean-code skill into the playbook):** Phase 3 was checked against `.claude/clean-code-skill/references/07-review-checklist.md` and `04-comments-policy.md`. Fixes made:
> - The two spatial repository methods took 6 and 5 parameters (01 §2 allows at most 3). They now take value objects (`GeoPoint` + `RangeLevels`, and the new `GeoCircle`), bound in SQL through SpEL.
> - The `AreaSpatialRules` class Javadoc described the implementation and now only cites the requirements (04).
> - A UI hint was removed from the `requireWithinRange` Javadoc (04: module API Javadoc states only the contract).
>
> Still open for Phase 7: the API contract (08) must document the public module events (`AreaVerified`, `AreaExpiring`, `AreaExpired`, `AreaRemoved`) and these facades in `docs/api/area-api.md`.

| Type | Used by | Contract |
|---|---|---|
| `AreaVerified`, `AreaExpiring`, `AreaExpired`, `AreaRemoved` (records) | Listing, Notification | Ids, area id, `expiresAt`. No coordinates. |
| `InteractionRange` | Chat, Transaction | `requireWithinRange(buyerId, GeoPoint listingPublicLocation)` → `403 OUT_OF_RANGE`. Uses `ST_DWithin` against each VERIFIED or EXPIRING area's own `range_level` (FR-AR-09). |
| `ListingPinRule` | Listing | `requirePinInsideArea(sellerId, areaId, pin)` → `422 PIN_OUTSIDE_AREA` (within 1 500 m, `listing.pin_radius_m`) |
| `FeedArea` | Discovery | `activeFeedArea(userId)` → `Optional<{areaId, center, radiusMeters}>`, used only on the server side |
| `AreaRemovalBlocker` (SPI) | Transaction implements it | Open transactions per area |
| `AreaSummaries` | Identity (FR-ID-12/13) | Label, status, `expiresAt`, `localityName`. Never `center`. See Phase 6 for how identity reads it without a cycle. |

PostGIS integration tests (Testcontainers, same image as `compose.yaml`) for `InteractionRange`: an area at MEDIUM (4 km) with a listing at 3.9 km is allowed and one at 4.1 km is refused (BR-08). Also test exactly at the boundary, and the pin at 1 499 m and 1 501 m.

### Phase 4. Trading gate FR-ID-20 (in identity, decision D1)

> **Status: done (2026-11-22).** Added:
> - Identity root: `TradingGate` (`evaluate`, `requireOpen` → `403 TRADING_GATE_FAILED` with `reasons`), `TradingGateStatus`, `TradingGateFailure` (the five SRS codes, verbatim), and the SPI `TradingAreaStatus` returning `AreaStanding` (`VALID`, `ONLY_EXPIRED`, `NONE`).
> - Identity internals: the pure rule `domain/TradingGateConditions`, `application/gate/TradingGateService` (computed on every call, DEC-AR-2), and `GET /users/me/trading-gate` (`TradingGateController`, `TradingGateResponse`).
> - Area module: `application/AreaTradingStatus` implements the SPI. `ApplicationModules.verify()` stays green, so there is no cycle.
>
> Tests: `TradingGateConditionsTests` (10), `TradingGateIntegrationTests` (10, real identity and area modules, including the FR-ID-20 acceptance criterion "turning any one condition off closes the gate, turning it back on reopens it" for areas, consent and account status) and `TradingGateApiIntegrationTests` (3). The new code is 100 % covered.
>
> Contract: `docs/api/identity-api.md` gained endpoint #34, flow F15 with a call to action for each failure, §6, and the public API table. `openapi.yml` was regenerated; its diff is the new path and schema, plus springdoc reordering the `Problem` properties without changing them. The Postman folder "6. Trading gate" was added, and `docs/prompts/*` now reference F15.
>
> Readings of the SRS (SOT) made in this phase:
> 1. **All unmet conditions are returned**, in the order FR-ID-20 states them (ACTIVE, phone, area, consent).
> 2. **`AREA_EXPIRED` vs `NO_VALID_AREA`:** `AREA_EXPIRED` means the user has areas but all are EXPIRED, so the call to action is "renew". `NO_VALID_AREA` means the user has no area, so the call to action is "verify".
> 3. **Every non-ACTIVE status is `ACCOUNT_RESTRICTED`:** SUSPENDED, BANNED, PENDING_DELETION and DELETED.
> 4. **No Redis gate cache** (DEC-AR-2), although FR-ID-20's behaviour row mentions one. The SRS acceptance criterion (the gate reopens as soon as a condition is fixed) holds without the cache-refresh events.
>
> Note: running `bootRun` to export OpenAPI applied V12 to the local Docker database, so V12 must no longer be edited; corrections go in V13.

- In identity root: `TradingGate` with `evaluate(userId) → TradingGateResult(open, List<GateFailure>)` and `requireOpen(userId)` → `403 TRADING_GATE_FAILED` with `reasons[]`. The reasons are `ACCOUNT_RESTRICTED`, `PHONE_NOT_VERIFIED`, `CONSENT_MISSING`, `NO_VALID_AREA`, `AREA_EXPIRED`.
- Also in identity root: the SPI `TradingAreaStatus { AreaStanding standingOf(UUID userId); }` with values `VALID`, `ONLY_EXPIRED`, `NONE`. Areaverification implements it. Without an implementation (as in identity-only tests) the gate treats the user as `NONE`.
- Also in identity root: `AccountStanding` (`requireActiveWithVerifiedPhone`) for area's preconditions.
- Endpoint `GET /users/me/trading-gate` → `{open, failures[]}`, so the client can show the right call to action (verify phone, give consent, verify area, renew area).
- Tests: one per reason, all reasons together, and the gate opening right after `verify` succeeds.

### Phase 5. Web layer (`/api/v1`, USER role; every write needs `Idempotency-Key`)

> **Status: done (2026-11-22).** `areaverification/web/AreaController` serves `GET /areas/me`, `POST /areas/verifications`, `POST /areas/{areaId}/renewals`, `PATCH /areas/{areaId}`, `PUT /areas/active` and `DELETE /areas/{areaId}`. The DTOs are in `web/dto`. `VerificationResponses` turns a rejected outcome into a 422 only after the service has committed the attempt. `RejectionGuidance` holds the Vietnamese guidance FR-AR-05 requires.
>
> Tests: `AreaApiIntegrationTests`, 19 cases: 401, 201, replay, `IDEMPOTENCY_CONFLICT`, missing key, `VALIDATION_FAILED`, 422 with guidance, 403 consent, 409 limit, replace, 429 with `Retry-After`, renew 200 and `OUT_OF_RENEW_RADIUS`, 404, list, rename, `INVALID_RANGE`, remove and remove again, optional device signals, and no coordinates in any response. The web layer has 100 % line coverage.
>
> **Contract (step 10 of the skill), done in this phase instead of Phase 7:** the skill says an API change isn't done without its contract. Added `docs/api/area-api.md` (scope, conventions, endpoint table, state machine, flows FA1–FA5 with an idempotency sequence diagram, endpoint details, events, public API, jobs). `openapi.yml` was regenerated: +490 lines, nothing removed. Added `docs/postman/nearby-area.postman_collection.json` (16 requests in flow order, valid JSON), and linked the contract from `README.md` and `docs/prompts/*`. Phase 7 is now only the update for Phase 6.
>
> Readings of the SRS (SOT) made in this phase:
> 1. **Paths.** The SRS defines no area paths, so the paths in this plan are used.
> 2. **422 codes.** A renewal refused only for distance answers `OUT_OF_RENEW_RADIUS` (FR-AR-04) with `distanceMeters`. A hard-rule failure answers `AREA_VERIFICATION_REJECTED` with `reasons`. Both carry `attemptId` and `guidance[{reason, message}]` in Vietnamese ("hướng dẫn tiếng Việt", FR-AR-05).
> 3. **`rangeLevel` is accepted as a string** so that a wrong value answers `400 INVALID_RANGE` (FR-AR-08) instead of a generic JSON error.
> 4. **Idempotency-Key** is required only on the two GPS endpoints (SRS 5.4 lists "xác minh/gia hạn khu vực").

| # | Method and path | Success | Requirement / UC |
|---|---|---|---|
| 1 | `GET /areas/me` | 200: list of areas with `{id, slot, label, localityName, status, rangeLevel, isActive, verifiedAt, expiresAt, renewable}` | FR-AR-03, FR-AR-08, screen A27 |
| 2 | `POST /areas/verifications` `{latitude, longitude, accuracyMeters, fixTime, mockLocation, deviceSignals?, appVersion?, label?, replaceAreaId?}` | 201 new area, or 200 when an existing area was renewed (A4) | FR-AR-01/02/05/06/10, UC-AR-01, UC-AR-03, screen A28 |
| 3 | `POST /areas/{areaId}/renewals` `{latitude, longitude, accuracyMeters, fixTime, mockLocation, deviceSignals?}` | 200 | FR-AR-04, UC-AR-02 |
| 4 | `PATCH /areas/{areaId}` `{label}` | 200 | UC-AR-01 step 9 |
| 5 | `PUT /areas/active` `{areaId, rangeLevel}` | 200 | FR-AR-08, UC-AR-04, screens A06/A27 |
| 6 | `DELETE /areas/{areaId}` | 204 | FR-AR-07, UC-AR-03 |
| 7 | `GET /users/me/trading-gate` (identity) | 200 | FR-ID-20 |

Error mapping: `400 VALIDATION_FAILED` (lat/lng range, accuracy ≥ 0, label 1–30), `400 INVALID_RANGE`, `400 IDEMPOTENCY_KEY_REQUIRED`, `403 LOCATION_CONSENT_REQUIRED`, `403 TRADING_GATE_FAILED` (account not active or phone not verified), `404 AREA_NOT_FOUND` (also for areas of other users, FR-ID-18), `409 AREA_LIMIT_REACHED`, `409 AREA_HAS_OPEN_TRANSACTIONS`, `409 AREA_STATUS_CONFLICT`, `409 IDEMPOTENCY_CONFLICT`, `422 AREA_VERIFICATION_REJECTED` with `reasons[]`, `422 OUT_OF_RENEW_RADIUS` with `distanceMeters` rounded to 100 m, `429 AREA_RATE_LIMITED` with `reason` and `Retry-After`.

- Responses **never** contain `reported_location`. They also never contain the area `center` except the grid-snapped one, and that only for the owner, if the A27 map needs it. Add a controller test that checks this (NFR-15).
- Controllers carry `@Tag`, `@Operation`, and `@ApiResponse` for each error; DTOs carry `@Schema` (reference 08 §3). Controllers stay thin and contain no business `if`s.
- Controller tests (MockMvc): status codes and RFC 9457 bodies for bad input, a missing key, another user's area, and a rejected verification.

### Phase 6. Identity follow-ups enabled by Area

> **Status: done (2026-11-22).** SRS read: FR-ID-12 and FR-ID-13 (`ch3_features.tex`), `user_areas.locality_name` (`ch4_dd.tex`), schema delta row `users` (`ch4_tail.tex`: `home_area`, `verified_area_count` "not applied").
> - The SPI is named `ProfileAreas` (`areasOf`, `publicLocalityOf`) with the record `ProfileArea` (id, label, localityName, status, active, expiresAt), implemented by `AreaProfileSummaries`. Own profile: areas that are not removed, in slot order, including EXPIRED, since FR-ID-12 asks for each area's status and expiry.
> - Public label: the `locality_name` of a VERIFIED or EXPIRING area, the active one first; never the private `label`, never for a deleted account. It stays null until the source of `locality_name` is decided (open question).
> - `V13__drop_home_area.sql`; `HomeArea`, `HomeAreaPayload` and `User.homeArea` removed; `PUT /users/me` ignores a `homeArea` still sent. "Breaking changes" section added to `identity-api.md`; Postman identity updated.

1. `GET /users/me` adds `areas[]`, which completes the area part of FR-ID-12. `GET /users/{id}` adds the active area's `localityName` only (FR-ID-13). To avoid a cycle, identity defines the SPI `ProfileAreaSummaries` and areaverification implements it, the same pattern as D1.
2. Remove `home_area`, `verified_area_count` and `HomeArea`/`homeArea` (D4). Migration `V13__drop_home_area.sql`. Add a **"Breaking changes"** section at the top of `identity-api.md` and to the final report.

### Phase 7. API contract (reference 08, required)

- New `docs/api/area-api.md` laid out like `identity-api.md`: scope, conventions (link back), endpoint table, the state machine (Mermaid) with "any other transition → `409 AREA_STATUS_CONFLICT`", events, and runtime parameters with their values.
- Business flows, each with UC and screen codes, the data to keep between steps, and a UI action for every error code:
  - FA1 Verify the first area (UC-AR-01, A27 → A28), with a `sequenceDiagram` for the idempotent retry.
  - FA2 Renew from the reminder or from `AREA_EXPIRED` (UC-AR-02).
  - FA3 Third area → remove or replace (UC-AR-03).
  - FA4 Choose the active area and range (UC-AR-04, A06/A27).
  - FA5 Trading-gate CTA (FR-ID-20) before posting, chatting or making an offer. Link it from the listing, chat and transaction docs later.
- Update `identity-api.md`: the trading-gate endpoint, the profile `areas[]`, and the breaking change.
- Regenerate `docs/api/openapi.yml` with `curl.exe -s http://localhost:8080/v3/api-docs.yaml -o docs/api/openapi.yml` and check that the diff contains only the intended changes.
- New `docs/postman/nearby-area.postman_collection.json`, ordered by flow. Include happy paths and typical errors (mock flag, stale fix, 6th attempt, third area, renew too far), with scripts that save `areaId`. Validate the JSON.
- Update `README.md` (link to the Area API) and `docs/prompts/README.md` (new flows).

### Phase 8. Definition of done (reference 07)

- [ ] Each step was written test-first. Every new behaviour has a happy-path, a boundary and an error test. Time-dependent logic uses a fixed `Clock`.
- [ ] State machine: every allowed edge and the forbidden ones are tested. Two concurrent verifications for the third slot: only one succeeds (unique index plus `version`).
- [ ] PostGIS integration tests for every spatial query. Listener and job idempotency tests.
- [ ] `./gradlew build` is green with Docker on: tests, `checkFormat`, `ApplicationModules.verify()` with no cycle, and JaCoCo (project ≥ 60 %, domain/application ≥ 80 %, areaverification domain ≥ 90 % branch).
- [ ] No `reported_location` or exact coordinates in any response or log.
- [ ] No comments except "why" comments. No magic numbers; everything reads `app_parameters`.
- [ ] `area-api.md`, `identity-api.md`, `openapi.yml` and the Postman collection all match the code.
- [ ] The final report lists the tests added, coverage, the contract files changed, the breaking change (`homeArea`), and what is still open (IP_MISMATCH, Play Integrity, ShedLock, gate cache).

### Suggested order of work (small PRs)

1. Phase 0 (migration, idempotency upgrade, geo helpers, error codes)
2. Phase 1 (domain and signals)
3. Phases 2 and 5 for verify and list
4. Renew, remove, replace, select active
5. Jobs and listeners
6. Phases 3 and 4 (public API, trading gate)
7. Phase 6 (identity follow-ups, breaking change)
8. Phase 7 (contracts)

---

## Part 3. After Area: module roadmap

1. **Listing + Search**: FR-LS, FR-DC. Uses `TradingGate`, `ListingPinRule`, `FeedArea`, `UserBlocks`, and the shared `GridSnapper` for `public_location`. Settles OI-03 (listing images).
2. **Chat + Notify**: FR-CH, FR-NT. Includes FCM device-token registration, which closes the FR-ID-03 gap. Settles OI-04.
3. **Transaction (+ Payment R1 scope)**: FR-TX, FR-PM. Implements `AreaRemovalBlocker` and `AccountDeletionBlocker`. Settles OI-08.
4. **Reputation**: FR-RP. Consumes `UserRegistered` (50 / NEW). Completes FR-ID-12/13. Settles OI-05 and OI-06 (reputation keys).
5. **Moderation + Admin Console**: FR-MD, FR-AD. User 360 shows `area_verifications` and `device_signals`; also `user_roles` and TOTP. Settles OI-07.
