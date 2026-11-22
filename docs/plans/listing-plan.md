# Plan: Listing + Discovery (`listing` module)

Status: **approved for D2, D4/D5, D6 and D7 (2026-11-22, by the user, as recommended).** D1, D3, D8, D9 and D10 follow the recommendation unless the user objects before Phase 0. Ready to start Phase 0.

Sources (SOT = `docs-local/SRS_Hyperlocal_Marketplace_LaTeX/`; re-read the exact passages at the start of every phase):

| What | Where |
|---|---|
| Listing FRs, stimulus/response table, state machine `tab:sm-listing` | `tex/ch3_features.tex` §"Tin đăng" (lines ~883–1314) |
| Discovery FRs, stimulus/response table | `tex/ch3_features.tex` §"Khám phá" (lines ~1315–1667) |
| Use-case index | `tex/appC.tex` (UC-LS-01..09, UC-DC-01..08, UC-SY-08/10/15) |
| Data dictionary: `categories`, `listings`, `listing_images`, `favorites`, `search_history`, `keyword_alerts` | `tex/ch4_dd.tex` §"Module listing", §"Module discovery" |
| Field rules (LST group), integrity rules, `tab:params`, schema delta, retention | `tex/ch4_tail.tex` |
| Rate limits `tab:ratelimits`, error format | `tex/ch5.tex` §5.4 |
| Blur algorithm, "relevant" ranking formula | `tex/appD.tex` §"Làm mờ vị trí công khai", §"Xếp hạng “phù hợp nhất”" |
| Event catalogue (producer → consumers) | `tex/appB.tex` |
| Open issue OI-03 (`listing_images`) | `tex/appF.tex` |

## Scope

### Functional requirements

| FR | Name | Priority / release | In this plan |
|---|---|---|---|
| FR-LS-01 | Create listing | M / R1 | Yes |
| FR-LS-02 | Images and processing pipeline | M / R1 | Yes |
| FR-LS-03 | Public location blur | M / R1 | Yes |
| FR-LS-04 | Posting quota | M / R1 | Yes |
| FR-LS-05 | Automatic screening | S / **R2** | No (R2). R1 publishes straight to ACTIVE |
| FR-LS-06 | Edit listing | M / R1 | Yes |
| FR-LS-07 | Hide / unhide | M / R1 | Yes |
| FR-LS-08 | Soft delete | M / R1 | Yes (open-transaction check through an SPI, D5) |
| FR-LS-09 | Mark sold (Karrot style) | S / R1 | **Split (D6):** "sold outside the app" here; "sold to a chat partner" with the Transaction module |
| FR-LS-10 | Bump | S / R1 | Yes |
| FR-LS-11 | My listings | M / R1 | Yes (chat/offer counters filled by Chat/Transaction later) |
| FR-LS-12 | Favourites | S / R1 | Yes |
| FR-LS-13 | Listing state machine | M / R1 | Yes |
| FR-LS-14 | View count | C / R1 | Yes (small: Redis dedup) |
| FR-LS-15 | Admin-managed categories | C / R2 | No (seeded categories only; inactive category → 422 still enforced) |
| FR-DC-01 | Nearby feed | M / R1 | Yes |
| FR-DC-02 | Filtered search | M / R1 | Yes |
| FR-DC-03 | Sorting (nearest, newest, relevant) | S / R1 | Yes |
| FR-DC-04 | Search center | M / R1 | Yes |
| FR-DC-05 | Result card | M / R1 | Yes |
| FR-DC-06 | Empty-result hint and radius clamp | S / R1 | Yes |
| FR-DC-07 | Clustered map (≤ 200 points) | S / R1 | Yes |
| FR-DC-08 | Listing detail with gated buttons | M / R1 | Yes |
| FR-DC-12 | Separate read path | M / R1 | Yes |
| FR-DC-09, 10, 11 | Search history, keyword alerts, feed categories | C/S/C / R2 | No |
| FR-DC-13 | Personalised suggestions | W | No |

### Use cases, screens, events, jobs

- **UC:** LS-01 create, LS-02 edit, LS-03 hide/unhide, LS-04 delete, LS-05 mark sold (partly, D6), LS-06 bump, LS-07 my listings, LS-08/09 favourites; DC-01 feed, DC-02 search, DC-03 map, DC-04 detail; SY-10 image processing; SY-15 orphan cleanup (hourly).
- **Screens:** A06 home feed, A07/A08 search and filters, A09 map, A10 detail, A12 create/edit, A13 image picker, A14 my listings, A15 favourites.
- **Events produced:** `ListingPublished` (consumer: discovery keyword alerts, R2), `ListingPriceDropped`, `ListingStatusChanged` (consumers: notification R2, transaction: reject open offers on SOLD/REMOVED).
- **Events consumed:** `AreaRemoved` → hide with `hidden_reason = AREA_REMOVED` (FR-AR-07); `UserSanctioned` / `SanctionLifted` → hide with `SANCTION` / restore; `AccountDeleted` → anonymise/delete the seller's listings and favourites. **Not** `AreaExpired`: FR-AR-03 says listings of an expired area stay visible; only the seller's new chat/offer actions are blocked (by the trading gate).
- **Jobs:** UC-SY-15 orphan image cleanup, hourly, deletes uploads not attached after `upload.orphan_ttl_hours` = 24 ("gone within 25 hours").

### Tables (target shape from `ch4_dd.tex`)

- `categories`: add `icon_key`, `sort_order`, `created_at`, `updated_at`, `version`.
- `listings`: rebuild. New vs V3: `area_id`, `is_free` (generated), `accept_offers`, status set `PENDING_REVIEW/ACTIVE/RESERVED/SOLD/HIDDEN/DELETED/REMOVED`, `hidden_reason` (`OWNER/AREA_REMOVED/SANCTION`, required when HIDDEN), `locality_name`, `meeting_spot_text`, `bumped_at`, `bump_count`, `feed_at` (generated `greatest(created_at, bumped_at)`), `view_count`, `favorite_count`, `chat_count`, `reserved_transaction_id` (CHECK tied to RESERVED), `seller_tier`, `review_reason`, `removal_reason`, `removed_by`, `published_at`, `sold_at`, `deleted_at`; title 5–100; description ≤ 2000; price `bigint` 0–1 000 000 000; `search_vector` over `f_unaccent(title/description)`. Indexes: GiST `public_location`, (`status`, `feed_at`, `id`), GIN `search_vector`, `seller_id`.
- `listing_images`: rebuild per OI-03 (keep the original design): `owner_id`, nullable `listing_id`, `original_key` (private `incoming/`), `thumb_key` (320 px WebP), `large_key` (1080 px WebP), `position` 1–10, `mime_type`, `size_bytes` ≤ 5 242 880, `width`, `height`, `processing_status` `UPLOADING/READY/REJECTED`, `reject_reason`, `created_at`, `processed_at`.
- `favorites`: keep (PK `user_id, listing_id`).
- Extensions: `unaccent`, `pg_trgm`, an IMMUTABLE `f_unaccent` wrapper (needed for a generated column).

### Parameters (`tab:params`, verbatim)

`listing.blur_m=300`, `listing.pin_radius_m=1500` (exists, V12), `listing.max_images=10`, `listing.max_open_per_user=30`, `listing.new_account_daily_limit=3`, `listing.bump_cooldown_hours=48`, `listing.bump_max_per_listing=10`, `upload.max_image_mb=5`, `image.max_bytes=5242880` (exists), `upload.orphan_ttl_hours=24`, `search.max_radius_km=20`, `search.page_size=20`, `search.rank_weights=0.5,0.3,0.2`.

### Error codes (verbatim from the FRs)

| Code | HTTP | Where |
|---|---|---|
| `VALIDATION_FAILED` | 400 (see D2) | every write and search filter |
| `TRADING_GATE_FAILED` | 403 + `reasons` | create (FR-LS-01), from `identity.TradingGate` |
| `PIN_OUTSIDE_AREA` | 422 | create: area not a valid area of the seller, or pin > 1 500 m (from `ListingPinRule`) |
| `IMAGE_INVALID` | 422 | create/edit: image key not the seller's, not READY, or attached elsewhere |
| `INVALID_IMAGE` | 400 | upload: wrong type, magic bytes mismatch, > 5 MB (same code as the avatar flow) |
| `LISTING_DAILY_LIMIT` | 429 + remaining quota | 4th new listing in a day before the first completed transaction (BR-07) |
| `LISTING_OPEN_LIMIT` | 409 or 422 (D3) | 31st open listing |
| `IDEMPOTENCY_KEY_REQUIRED` / `IDEMPOTENCY_CONFLICT` | 400 / 409 | create, mark sold |
| `INVALID_LISTING_STATE` | 409 | any transition not in `tab:sm-listing`; editing SOLD/REMOVED/DELETED; price change while RESERVED |
| `VERSION_CONFLICT` | 409 | stale `version` (FR-LS-06/13; the SRS also names `CONCURRENT_MODIFICATION`, D2) |
| `LISTING_HAS_OPEN_TRANSACTIONS` | 409 (name to confirm, D2) | delete with OFFERED/ACCEPTED transactions; hide while RESERVED names the holding transaction |
| `BUMP_TOO_SOON` | 429 + `nextAllowedAt` (name to confirm, D2) | bump within 48 h |
| `BUMP_LIMIT_REACHED` | 409 (name to confirm, D2) | 11th bump, or listing not ACTIVE |
| `LISTING_NOT_FOUND` | 404 | unknown id, DELETED/REMOVED on public reads, another seller's listing on owner endpoints |
| `INVALID_CURSOR` | 400 | feed/search/map cursor that cannot be decoded |
| `OUT_OF_RANGE` | 403 | not raised here; detail only *reports* range for buttons (FR-DC-08) |

## Current state and gaps

- **Exists:** `listing/package-info.java` only (`allowedDependencies = shared, identity, reputation`). V3 tables `categories`, `listings`, `listing_images`, `favorites`; V8 seeds categories; `listing.pin_radius_m` (V12).
- **Stale (schema delta, OI-03):** V3 `listings` lacks ~20 columns above, uses `numeric(12,0)` price, title ≤ 120, status set without RESERVED/PENDING_REVIEW, and `search_vector` without unaccent. `listing_images` has one `object_key` and positions 1–8. No rows exist in any environment that matter, so V14 drops and recreates the listing tables (same approach as V12 for area).
- **Ready to reuse:** `shared.geo.GridSnapper` (300 m Web Mercator, built for this blur), `GeoPoint.distanceMetersTo`, idempotency (`@Idempotent`), `RateLimiter`, `ImageStorage` (presigned PUT/GET), `ImageUploadPolicy` (identity domain; move the shared part to `shared`), `identity.TradingGate`, `identity.UserBlocks`, `areaverification.ListingPinRule`, `FeedArea`, `InteractionRange`, `AreaRemoved`, `AccountStanding`.
- **Missing everything else.** `package-info` must change to `allowedDependencies = shared, identity, areaverification` (D4).

## Decisions to confirm

Answered 2026-11-22: D2 → 400 `VALIDATION_FAILED` and image status `REJECTED`; D4/D5 → Transaction, Chat and Reputation call Listing (no listing → transaction/reputation dependency); D6 → "sold outside the app" now, "sold to a chat partner" with Transaction; D7 → JPEG/PNG/WebP processed in-process, HEIC rejected with `INVALID_IMAGE` and converted to JPEG by the Android app.


| # | Question | Recommendation | Why |
|---|---|---|---|
| D1 | Where does Discovery live? | Inside the `listing` module, sub-package `listing.discovery` (read-only services and controllers), as the existing module name "Listing + Search" and the roadmap say. | Discovery reads only `listings`, `listing_images`, `categories`; a separate module would need a wide listing read API for no benefit. Its R2 tables (`search_history`, `keyword_alerts`) can still go in the same package later. |
| D2 | SRS inconsistencies in codes | (a) `VALIDATION_FAILED` stays **400** everywhere (FR-DC-02 and the existing contract), not 422 as FR-LS-01 says. (b) Concurrency: `VERSION_CONFLICT` only. (c) Name the unnamed 409/429 codes `LISTING_HAS_OPEN_TRANSACTIONS` (also returned, with the transaction id, when hiding a RESERVED listing), `BUMP_TOO_SOON`, `BUMP_LIMIT_REACHED`. (d) Image status `REJECTED` (data dictionary CHECK), not `FAILED` (FR-LS-02 text). | One error shape for clients; the data dictionary is the stricter source for enum values. Each choice is recorded with its passage. |
| D3 | `LISTING_OPEN_LIMIT`: 409 or 422? | **409** | It depends on current state (30 open listings), like other state conflicts. |
| D4 | Module dependencies | listing → `shared, identity, areaverification`. Remove `reputation`. Reputation pushes `seller_score`/`seller_tier` through a listing API (`SellerScores.update`); Transaction calls a listing API (`ListingReservations`: reserve, release, markSold); Chat calls `ListingCounters` (chat count). Listing defines the SPI `OpenTransactionCheck` that Transaction implements (delete guard, FR-LS-08). | Transaction, Chat and Reputation all need listing data, so listing must not depend on them (cycle). Calls go "downhill"; listing asks "uphill" only through SPIs it owns — the pattern used for the trading gate. Until those modules exist, scores default to 50/NEW and the SPI says "no open transaction". |
| D5 | RESERVED / SOLD from transactions | Transaction calls `ListingReservations` inside its own transaction (synchronous), instead of listing listening to `TransactionAccepted/Completed/Cancelled`. | appB lists listing as a consumer of those events, but listing listening to transaction while transaction calls listing would be a cycle. A synchronous call also keeps "one ACCEPTED per listing" (BR-04) atomic. Behaviour matches the state machine either way. |
| D6 | FR-LS-09 mark sold | Build "sold outside the app" (ACTIVE → SOLD, no transaction) now; build "sold to a chat partner" (creates an ACCEPTED transaction, `SaleMarked`, 72 h deny window) with the Transaction module. | Option (a) needs `transactions` (v0.2, OI-08) and chat partners; appB names *trade* as the producer of `SaleMarked`. |
| D7 | Image processing (UC-SY-10) | Process in-process on a bounded async executor after `upload-complete`: verify magic bytes, strip metadata by re-encoding, write 320 px and 1080 px WebP. Library: TwelveMonkeys ImageIO (read JPEG/PNG/WebP) plus a WebP writer (`webp-imageio`, native cwebp). **HEIC:** accept the type in the contract but reject with `INVALID_IMAGE` until a decoder is chosen; ask the Android app to convert HEIC to JPEG before upload. | The SRS puts it in a `worker`; one process is enough for R1 and the executor can move to a worker later. Pure-Java HEIC decoding is not reliable; the app can convert cheaply. Alternatives: libvips via a sidecar, or a managed image service. |
| D8 | Read path (FR-DC-12) | A routing `DataSource` (`LazyConnectionDataSourceProxy` + read-only routing) that sends `@Transactional(readOnly = true)` to `spring.datasource.read.*` when configured and to the primary otherwise. Tested with the replica URL equal to the primary. | Exactly "falls back to primary when the read path is unavailable"; no code change when a replica is added. |
| D9 | `locality_name` | Copy the area's `locality_name` onto the listing at create time (null today, same open question as the area module). | Data dictionary §15 "copy for result cards". |
| D10 | Map clustering (FR-DC-07) | Server-side grid clustering in SQL: `ST_SnapToGrid` on Web Mercator with a cell size derived from the zoom level; return single points when the viewport holds ≤ 200 listings, clusters (count + centroid of blurred points) otherwise. | The SRS asks only for "grid by zoom, ≤ 200 points"; this does it in one query on `public_location`. |

## Phases

Each phase ends green (`./gradlew build`) and updates `references/01-project-status.md`.

### Phase 0. Groundwork

- `V14__listing_v2.sql`: extensions `unaccent`, `pg_trgm`; `f_unaccent`; drop/recreate `listings`, `listing_images`, `favorites` per the data dictionary; alter `categories` (+ codes in the SRS list: ELECTRONICS, HOUSEHOLD, BOOKS, FASHION, VEHICLES, BABY, SPORTS, FREE_SHARING, OTHER); seed the parameters above; partial/check constraints (`hidden_reason` required when HIDDEN, `reserved_transaction_id` iff RESERVED, `removal_reason` when REMOVED).
- `ErrorCodes` for every code in the table; `ApiPaths.LISTINGS_BASE`, `DISCOVERY` paths; `RedisKeyPrefixes` for daily quota and view dedup; `StorageKeyPrefixes.LISTING_INCOMING` / `LISTING_IMAGES`.
- Move the type/size part of `ImageUploadPolicy` to `shared.storage` so avatar and listing images share it (avatar tests stay green).
- Routing read `DataSource` (D8).

### Phase 1. Domain (pure Java, test-first, ≥ 90 % branches)

| Class | Responsibility | Key tests (SRS numbers) |
|---|---|---|
| `ListingStatus`, `HiddenReason` | Enums verbatim | — |
| `Listing` (entity) | Every transition of `tab:sm-listing` through one method set with `version`; edit rules (no area change; no price change when RESERVED; no edit when SOLD/REMOVED/DELETED); `bump` (48 h, 10 max); hide reasons OWNER/AREA_REMOVED/SANCTION and "restore only what the sanction hid" | Every row of `tab:sm-listing` allowed; every other pair → `INVALID_LISTING_STATE` (FR-LS-13 acceptance); price drop reports a drop only when lower |
| `ListingDraft` / value objects `Title` (5–100), `Description` (≤ 2000), `Price` (0–1 000 000 000, `isFree`), `Condition`, `MeetingSpot` (≤ 100), `ImageOrder` (1–10, first = cover) | Field rules LST | 4/5 and 100/101 chars; price −1/0/1e9/1e9+1; 0 and 11 images |
| `PublicLocation` (via `GridSnapper`) | Blur, deterministic | Same pin → same cell; pin–cell ≤ 212 m (appD); point ≠ pin |
| `PostingQuota` | 3/day before the first completed transaction, 30 open | 4th listing rejected (BR-07); 31st open rejected; ACTIVE+RESERVED+PENDING_REVIEW count |
| `DisplayDistance` | 100 m rounding < 1 km, 0.1 km ≥ 1 km | 949 m → 900 m, 1 049 m → 1.0 km |
| `RelevanceScore` | appD formula with weights from params | weight change changes order; ties by `feed_at`, then `id` |
| `FeedCursor` | Encodes (`feed_at`, `id`) / (score, `feed_at`, `id`); tamper → `INVALID_CURSOR` | round trip; garbage → error |
| `SearchRadius` | Clamp to 1–20 km with an "adjusted" flag | 25 → 20 + flag; 0.5 → 1 + flag |
| `ListingImage` | UPLOADING → READY / REJECTED; attach once, owner only | attaching another user's image → `IMAGE_INVALID` |

### Phase 2. Application

- **UC-LS-01 create:** `@Idempotent`; `TradingGate.requireOpen` → `ListingPinRule` (area valid and pin ≤ 1 500 m) → `PostingQuota` (Redis rolling counter + open count) → images READY and owned → save ACTIVE with blur, `published_at`, `locality_name` → `ListingPublished`.
- **UC-LS-02 edit:** owner + `version`; `ListingPriceDropped` once on a drop; re-blur when the pin moves (still checked by `ListingPinRule` for the same area).
- **UC-LS-03/04/06:** hide/unhide, soft delete (SPI `OpenTransactionCheck`, default "none"), bump.
- **UC-LS-05 (part):** sold outside the app.
- **UC-LS-07/08/09:** my listings grouped by status with counters; favourites (insert-if-absent, counter ±1 per distinct user, "no longer available" flag on DELETED/REMOVED).
- **UC-SY-10:** presigned PUT under `incoming/{userId}/`, `upload-complete` → async processing (D7); originals never served.
- **UC-SY-15:** hourly job, `@Transactional` on `run()`, deletes unattached images older than 24 h and their objects.
- **Listeners:** `AreaRemoved` → hide `AREA_REMOVED`; `UserSanctioned`/`SanctionLifted` → hide/restore `SANCTION`; `AccountDeleted` → seller listings DELETED, images deleted, favourites removed. All safe to run twice.
- **Discovery (read path, `readOnly = true`):** feed (FR-DC-01: center from `FeedArea` or GPS/picked point, range level, blocked sellers excluded via `UserBlocks`), search (FR-DC-02/03/06), map (FR-DC-07), detail (FR-DC-08: gate status, `InteractionRange`, own-listing actions), view count (FR-LS-14: Redis `SETNX` per listing/viewer/day; failures never block).

### Phase 3. Public API for other modules (module root)

`ListingReservations` (reserve / release / markSold, called by Transaction), `ListingCounters` (chat count), `SellerScores` (Reputation pushes score and tier), `ListingLookup` (status, seller, price, `accept_offers` for offers and chat), SPI `OpenTransactionCheck`; events `ListingPublished`, `ListingPriceDropped`, `ListingStatusChanged`.

### Phase 4. Web

| Endpoint | Success | FR / UC / screen |
|---|---|---|
| `POST /listings/images/upload-url` | 200 presigned PUT | FR-LS-02, A13 |
| `POST /listings/images/{imageId}/complete` | 202 (processing) | UC-SY-10 |
| `GET /listings/images/{imageId}` | 200 status READY/REJECTED + thumb | A13 polling |
| `POST /listings` (Idempotency-Key) | 201 | UC-LS-01, A12 |
| `PUT /listings/{id}` (`version`) | 200 | UC-LS-02 |
| `POST /listings/{id}/hide`, `/unhide` | 200 | UC-LS-03, A14 |
| `DELETE /listings/{id}` | 204 | UC-LS-04 |
| `POST /listings/{id}/bump` | 200 | UC-LS-06 |
| `POST /listings/{id}/sold-outside` (Idempotency-Key, `version`) | 200 | UC-LS-05 (b) |
| `GET /listings/me?status=` | 200 grouped with counters | UC-LS-07, A14 |
| `PUT` / `DELETE /listings/{id}/favorite`, `GET /users/me/favorites` | 204 / 200 | UC-LS-08/09, A10/A15 |
| `GET /categories` | 200 active categories | A12, A08 |
| `GET /feed` | 200 cursor page | UC-DC-01, A06 (public; guests pass a point) |
| `GET /search` | 200 cursor page + `radiusAdjusted` | UC-DC-02, A07/A08 |
| `GET /map/listings` | 200 points or clusters (≤ 200) | UC-DC-03, A09 |
| `GET /listings/{id}` | 200 detail; owner sees the exact pin | UC-DC-04, A10 |

Integration tests assert: no public response contains `exactLocation`/pin (NFR-15, BR-05); returned points equal the cell center; HIDDEN/PENDING_REVIEW/DELETED/REMOVED never appear in feed/search/map; "ao khoac" finds "Áo khoác"; SOLD only with `includeSold`; pages never repeat or skip when a listing is inserted mid-pagination; 25 km → 20 km + flag; another seller's listing on owner endpoints → 404.

### Phase 5. Contract

`docs/api/listing-api.md` (flows: post a listing A12/A13, manage A14, browse A06/A07/A08, map A09, detail A10, favourites A15, plus the trading-gate CTA from FA5), regenerated `openapi.yml`, `docs/postman/nearby-listing.postman_collection.json`, README and `docs/prompts` links.

### Phase 6. Definition of done

Clean-code review checklist (reference 07); `./gradlew build` green with Docker; FR codes in test display names; status file, roadmap and decision log updated; report with remaining gaps (mark sold to a chat partner, RESERVED/SOLD from transactions, chat/offer counters, seller score, R2 items, NFR-05 radius search over 10 000 listings load test).

## Order of work (small reviewable steps, each green)

1. Phase 0 migration + params + error codes + read path.
2. Domain: `Listing` state machine and value objects.
3. Images: upload URL, processing, orphan job.
4. Create / edit / hide / delete / bump / sold-outside + listeners.
5. Discovery: feed, search, sorting, map, detail, view count.
6. Public API for Transaction, Chat, Reputation.
7. Web + contract + Postman.
