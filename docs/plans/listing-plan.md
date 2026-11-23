# Plan: Listing + Discovery (`listing` module)

Status: **v2, 2026-11-22.** Re-checked line by line against the SRS LaTeX (SOT) on branch `feature/listing` (based on `master` @ `de4a887`, which contains Identity and Area). Decisions D1–D10 from v1 stand (DEC-LS-1..4 accepted; D1, D3, D8–D10 by recommendation). v2 adds **D11–D14 (accepted 2026-11-22 as DEC-LS-5..8)** and the **deferred-dependency list** (§ "Waits for other modules"). No production code exists yet; the next step is Phase 0.

Sources (SOT = `docs-local/SRS_Hyperlocal_Marketplace_LaTeX/`; re-read the exact passages at the start of every phase):

| What | Where |
|---|---|
| Listing description, stimulus/response `tab:sr-ls`, state machine `tab:sm-listing`, FR-LS-01..15 | `tex/ch3_features.tex` lines 883–1314 |
| Discovery stimulus/response `tab:sr-dc`, FR-DC-01..13 | `tex/ch3_features.tex` lines 1315–1667 |
| UC index; full specs of UC-LS-01, UC-LS-05, UC-DC-02 | `tex/appC.tex` (index lines 28–40, 104–111; specs lines 205–256) |
| Data dictionary `categories`, `listings`, `listing_images`, `favorites` | `tex/ch4_dd.tex` lines 243–324 |
| Validation rules `tab:validation`, integrity `tab:integrity`, parameters `tab:params` (lines 76–88) | `tex/ch4_tail.tex` |
| Rate limits `tab:ratelimits` | `tex/ch5.tex` lines 141–157 |
| Blur algorithm, "relevant" ranking formula | `tex/appD.tex` lines 38–45, 60–66 |
| Event catalogue | `tex/appB.tex` lines 16–39, jobs lines 53–61 |
| OI-03 (`listing_images`) | `tex/appF.tex` line 10 |

## Scope

### Functional requirements

| FR | Name | Priority / release | In this plan |
|---|---|---|---|
| FR-LS-01 | Create listing | M / R1 | Yes |
| FR-LS-02 | Images and processing pipeline | M / R1 | Yes |
| FR-LS-03 | Public location blur | M / R1 | Yes |
| FR-LS-04 | Posting quota | M / R1 | Yes. The "until the first completed transaction" exemption waits for Transaction (LS-W1) |
| FR-LS-05 | Automatic screening | S / **R2** | No. R1 publishes straight to ACTIVE; `review_reason` stays NULL |
| FR-LS-06 | Edit listing | M / R1 | Yes |
| FR-LS-07 | Hide / unhide | M / R1 | Yes |
| FR-LS-08 | Soft delete | M / R1 | Yes. The open-transaction guard is an SPI with a "none" default until Transaction (LS-W2) |
| FR-LS-09 | Mark sold (Karrot style) | S / R1 | **Split (DEC-LS-3):** (b) "sold outside the app" here; (a) "sold to a chat partner" with Transaction (LS-W4) |
| FR-LS-10 | Bump | S / R1 | Yes |
| FR-LS-11 | My listings | M / R1 | Yes. `chat_count` and open-offer count are 0 until Chat/Transaction (LS-W5, LS-W6) |
| FR-LS-12 | Favourites | S / R1 | Yes |
| FR-LS-13 | Listing state machine | M / R1 | Yes: every row of `tab:sm-listing` in the domain, including the rows that only other modules trigger |
| FR-LS-14 | View count | C / R1 | Yes (small: Redis dedup) |
| FR-LS-15 | Admin-managed categories | C / R2 | No admin CRUD. Seeded categories; an inactive category is still rejected on create |
| FR-DC-01 | Nearby feed | M / R1 | Yes |
| FR-DC-02 | Filtered search | M / R1 | Yes |
| FR-DC-03 | Sorting (nearest, newest, relevant) | S / R1 | Yes. `seller_score` = 50 for everyone until Reputation (LS-W7) |
| FR-DC-04 | Search centre | M / R1 | Yes |
| FR-DC-05 | Result card | M / R1 | Yes. `seller_tier` = NEW until Reputation (LS-W7) |
| FR-DC-06 | Empty-result hint and radius clamp | S / R1 | Yes |
| FR-DC-07 | Clustered map (≤ 200 points) | S / R1 | Yes |
| FR-DC-08 | Listing detail with gated buttons | M / R1 | Yes: the button states and reasons. The Chat / Offer / Report actions themselves come with Chat, Transaction, Moderation (LS-W8) |
| FR-DC-12 | Separate read path | M / R1 | Yes |
| FR-DC-09, 10, 11 | Search history, keyword alerts, feed categories | C/S/C / R2 | No |
| FR-DC-13 | Personalised suggestions | W | No endpoint (acceptance: "Không có endpoint gợi ý") |
| FR-ID-13 (part) | Public profile: "tin đang bán" | M / R1 | Yes, through `GET /users/{id}/listings` (D12) |

### Use cases, screens, events, jobs

- **UC:** LS-01 create (main flow steps 1–10, A2–A5), LS-02 edit, LS-03 hide/unhide, LS-04 delete, LS-05 mark sold (only A1 "ngoài ứng dụng" now), LS-06 bump, LS-07 my listings, LS-08/09 favourites; DC-01 feed, DC-02 search (A1–A3, A5; E1–E2), DC-03 map, DC-04 detail; SY-10 image processing; SY-15 orphan cleanup (hourly).
- **Screens:** A06 home feed, A07/A08 search and filters, A09 map, A10 detail, A11 public profile (listings part), A12 create/edit, A13 image picker and pin, A14 my listings, A15 favourites.
- **Events produced** (appB): `ListingPublished` (consumer: discovery keyword alerts, R2), `ListingPriceDropped`, `ListingStatusChanged` (consumers: notification for favouriters R2 / UC-SY-08; trade: reject open offers on SOLD/REMOVED). Not produced in R1: `ListingSubmitted` (R2 screening).
- **Events consumed:** `AreaRemoved` → ACTIVE/HIDDEN listings of that area become HIDDEN with `AREA_REMOVED` (`tab:sm-listing` row "ACTIVE hoặc HIDDEN → HIDDEN"); `UserSanctioned` → ACTIVE → HIDDEN (`SANCTION`); `SanctionLifted` (manual lift and suspension end, both already published by identity) → HIDDEN(`SANCTION`) → ACTIVE, only listings hidden by the sanction; `AccountDeleted` → seller's listings DELETED, images removed from storage, user's favourites removed. **Not** `AreaExpired`: FR-AR-03 says listings of an expired area stay visible; only new chats/offers are blocked (`InteractionRange.allowsNewContacts`, shown on detail).
- **Events not consumed** (DEC-LS-2, cycle): `TransactionAccepted/Cancelled/Expired/Completed`, `SaleMarked/SaleDenied`, `ReputationUpdated`, `ContentFlagged`. Those modules call listing facades instead (Phase 3).
- **Jobs:** UC-SY-15 orphan image cleanup, hourly; deletes images with `listing_id IS NULL` older than `upload.orphan_ttl_hours` = 24, rows and objects ("gone within 25 hours"). `@Transactional` on `run()`, safe to rerun (no ShedLock yet, NFR-20 gap).

### Tables (target shape from `ch4_dd.tex`)

- `categories`: add `icon_key`, `sort_order`, `created_at`, `updated_at`, `version`. Codes exactly: ELECTRONICS, HOUSEHOLD, BOOKS, FASHION, VEHICLES, BABY, SPORTS, FREE_SHARING, OTHER (re-seed; V8 codes may differ).
- `listings` (rebuild): the 33 columns of the dictionary. Notable: `area_id` NN; `title` varchar(100) CHECK 5–100; `description` varchar(2000); `condition` CHECK NEW/LIKE_NEW/USED/WORN; `price bigint` CHECK 0–1 000 000 000; `is_free` GENERATED (price = 0); `accept_offers` DEF true; `status` CHECK PENDING_REVIEW/ACTIVE/RESERVED/SOLD/HIDDEN/DELETED/REMOVED; `hidden_reason` CHECK OWNER/AREA_REMOVED/SANCTION and NN iff HIDDEN; `exact_location`, `public_location` geography(Point,4326) NN; `locality_name` varchar(100); `meeting_spot_text` varchar(100); `bumped_at`; `bump_count` ≥ 0; `feed_at` GENERATED greatest(created_at, bumped_at); `view_count`, `favorite_count`, `chat_count` DEF 0; `reserved_transaction_id` with CHECK (status = 'RESERVED') = (reserved_transaction_id IS NOT NULL); `seller_score numeric(5,2)`; `seller_tier` CHECK NEW/CAUTION/STANDARD/TRUSTED/HIGHLY_TRUSTED; `review_reason`; `removal_reason` (CHECK status ≠ REMOVED or NOT NULL); `removed_by`; `published_at`, `sold_at`, `deleted_at`; `search_vector` GENERATED (title weight A, description weight B, `'simple'` config over `f_unaccent`); audit columns + `version`. Indexes: GiST `public_location`; (`status`, `feed_at` DESC, `id` DESC); GIN `search_vector`; trigram GIN on `f_unaccent(lower(title))` for pg_trgm matching; (`seller_id`, `status`); (`area_id`) for the `AreaRemoved` listener.
- `listing_images` (rebuild, OI-03): `id`, `listing_id` FK nullable (ON DELETE CASCADE), `owner_id` NN, `original_key` NN (private `incoming/`), `thumb_key`, `large_key`, `position` 1–10 (NN when attached), `mime_type` CHECK jpeg/png/webp/heic, `size_bytes` ≤ 5 242 880, `width`, `height`, `processing_status` CHECK UPLOADING/READY/REJECTED, `reject_reason` varchar(50) (TYPE_MISMATCH, TOO_LARGE, DECODE_ERROR…), `created_at`, `processed_at`. Unique (`listing_id`, `position`); partial index (`created_at`) WHERE `listing_id IS NULL` for the orphan job.
- `favorites`: PK (`user_id`, `listing_id`), FK listing, `created_at`.
- Extensions/functions: `unaccent`, `pg_trgm`, IMMUTABLE `f_unaccent(text)` wrapper (needed for generated columns and indexes).
- Discovery R2 tables (`search_history`, `keyword_alerts`): not created.

### Parameters (`tab:params`, verbatim)

New keys: `listing.blur_m=300`, `listing.max_images=10`, `listing.max_open_per_user=30`, `listing.new_account_daily_limit=3`, `listing.bump_cooldown_hours=48`, `listing.bump_max_per_listing=10`, `upload.max_image_mb=5`, `upload.orphan_ttl_hours=24`, `search.max_radius_km=20`, `search.page_size=20`, `search.rank_weights=0.5,0.3,0.2`. Existing: `listing.pin_radius_m=1500` (V12), `image.max_bytes=5242880` (V9). **Stale key:** V8 seeds `location.blur_meters` = 300; V14 deletes it in favour of `listing.blur_m` (checklist §3 "fix stale keys in the same migration").

### Error codes (verbatim from the SRS unless marked)

| Code | HTTP | Where (SRS) |
|---|---|---|
| `VALIDATION_FAILED` | 400 (DEC-LS-1) | every write and search filter (FR-LS-01 says 422, FR-DC-02 says 400) |
| `TRADING_GATE_FAILED` | 403 + `reasons` | create (FR-LS-01), from `identity.TradingGate` |
| `PIN_OUTSIDE_AREA` | 422 | create/edit: area not a valid area of the seller, or pin > 1 500 m (`ListingPinRule`) |
| `IMAGE_INVALID` | 422 | create/edit: image id not the seller's, not READY, or attached to another listing (FR-LS-01) |
| `INVALID_IMAGE` | 400 | upload-complete: wrong type, magic-byte mismatch, > 5 MB; object deleted (FR-LS-02). Same code as the avatar flow |
| `LISTING_DAILY_LIMIT` | 429 + `remaining` + `Retry-After` | 4th listing in a day before the first completed transaction (FR-LS-04, BR-07) |
| `LISTING_OPEN_LIMIT` | 409 (D3) | 31st open listing (ACTIVE + RESERVED + PENDING_REVIEW) |
| `IDEMPOTENCY_KEY_REQUIRED` / `IDEMPOTENCY_CONFLICT` | 400 / 409 | create, sold-outside |
| `INVALID_LISTING_STATE` | 409 | any transition not in `tab:sm-listing`; edit of SOLD/REMOVED/DELETED; bump of a listing that is not ACTIVE (FR-LS-06/09/10/13). Name from FR-LS-09 E2, used for all illegal moves (the playbook's `*_STATUS_CONFLICT` pattern yields to the SRS name) |
| `CONCURRENT_MODIFICATION` | 409 | stale `version` (D11; FR-LS-06 names `VERSION_CONFLICT`/`CONCURRENT_MODIFICATION`) |
| `LISTING_HAS_OPEN_TRANSACTIONS` | 409 + `transactionId` | delete with OFFERED/ACCEPTED; hide / delete / price change of RESERVED names the holding transaction (FR-LS-07/08, `tab:sr-ls`). *Name chosen, SRS unnamed* |
| `BUMP_TOO_SOON` | 429 + `nextAllowedAt` + `Retry-After` | bump within 48 h (FR-LS-10). *Name chosen* |
| `BUMP_LIMIT_REACHED` | 409 | 11th bump (FR-LS-10). *Name chosen.* Bump of a non-ACTIVE listing → `INVALID_LISTING_STATE` |
| `CATEGORY_INACTIVE` | 422 | inactive category on create (FR-LS-15 "422"; unnamed, *name chosen*). Unknown category → 400 `VALIDATION_FAILED` |
| `LISTING_NOT_FOUND` | 404 | unknown id; DELETED/REMOVED on public reads (FR-LS-08 "404 công khai"); another seller's listing on owner endpoints (FR-ID-18) |
| `INVALID_CURSOR` | 400 | undecodable or tampered cursor (FR-DC-01). Exists |
| `RATE_LIMITED` | 429 | search over the global limit (FR-DC-02 E2). Code exists; the global limit itself is a platform task (`tab:ratelimits` "TBD tại Gate 4", register X-W3) |

Deferred to Transaction (FR-LS-09 (a), UC-LS-05 E3–E6): `NOT_A_CHAT_PARTNER`, `CONTACT_NOT_ALLOWED`, `COUNTERPART_UNAVAILABLE`, `INVALID_OFFER_PRICE`, `LISTING_ALREADY_RESERVED`.

## Current state and gaps

- **Exists:** `listing/package-info.java` only (`allowedDependencies = shared, identity, reputation`; must become `shared, identity, areaverification`, D4). V3 tables `categories`, `listings`, `listing_images`, `favorites`; V8 seeds categories and `location.blur_meters`; V12 seeds `listing.pin_radius_m`.
- **Stale (schema delta, OI-03):** V3 `listings` lacks ~20 dictionary columns, uses `numeric(12,0)` price, title ≤ 120, no RESERVED/PENDING_REVIEW, `search_vector` without unaccent. `listing_images` has one `object_key` and positions 1–8. No meaningful rows exist, so V14 drops and recreates the listing tables (the V12 approach).
- **Ready to reuse:** `shared.geo.GridSnapper` (Web Mercator grid, built for this blur), `GeoPoint.distanceMetersTo`, `@Idempotent` (replay and conflict), `RateLimiter`, `ImageStorage` (presigned PUT/GET), `ImageUploadPolicy` (identity; share the type/size part through `shared.storage`), `identity.TradingGate`, `identity.UserBlocks.blockedEitherWay`, `identity.AppParameters`, `areaverification.ListingPinRule`, `FeedArea` (`FeedCenter(areaId, center, radiusMeters)`), `InteractionRange` (`isWithinRange`, `allowsNewContacts`), events `AreaRemoved`, `UserSanctioned`, `SanctionLifted`, `AccountDeleted`. `GlobalExceptionHandler` already maps `OptimisticLockingFailureException` → 409 `CONCURRENT_MODIFICATION`.
- **Missing:** everything else.

## Decisions

Accepted (v1, 2026-11-22): **DEC-LS-1** 400 `VALIDATION_FAILED`, image status `REJECTED`; **DEC-LS-2** listing → shared, identity, areaverification only; Transaction/Chat/Reputation call listing; listing owns SPIs; **DEC-LS-3** sold outside the app now, sold to a chat partner with Transaction; **DEC-LS-4** JPEG/PNG/WebP in-process (320/1080 px WebP, metadata stripped), HEIC → `INVALID_IMAGE`, the app converts.

By recommendation (v1): **D1** discovery lives in `listing.discovery`; **D3** `LISTING_OPEN_LIMIT` = 409; **D8** routing read `DataSource` (`LazyConnectionDataSourceProxy` + `readOnly` routing, falls back to primary); **D9** copy the area's `locality_name` at create; **D10** SQL grid clustering on Web Mercator by zoom, single points when ≤ 200.

**Accepted 2026-11-22 (v2, DEC-LS-5..8):**

| # | Question | Recommendation | Why |
|---|---|---|---|
| D11 | Stale-`version` code | `CONCURRENT_MODIFICATION` (409), replacing v1's `VERSION_CONFLICT` | FR-LS-06 allows either name. The backend already has it and maps every optimistic-lock failure to it globally; identity uses it; OI-09 says keep backend names. One code for all modules |
| D12 | Public profile "tin đang bán" (FR-ID-13) | Listing serves `GET /users/{id}/listings` (public, ACTIVE + RESERVED, cursor page of result cards); the app composes A11 from `GET /users/{id}` + this call | Identity cannot read listing (it is upstream). An SPI from identity would push listing pages through the identity response for no gain. Closes the listing part of FR-ID-13 |
| D13 | `listing.new_account_daily_limit` window | Rolling 24 h from the first listing, like DEC-AR-5 (built as a count of the seller's listings in the last 24 h under a per-seller advisory lock, not a Redis counter) | "mỗi ngày" is unspecified; the same reading as area keeps one rule for users. Alternative: calendar day in `Asia/Ho_Chi_Minh` |
| D14 | View-count "day" (FR-LS-14) | Calendar day in `Asia/Ho_Chi_Minh`; Redis key `listing:view:{listingId}:{viewerId}:{yyyyMMdd}` with 25 h TTL; guests keyed by a hash of IP + User-Agent; the owner's own views not counted | "Mười lượt xem của một người trong một ngày chỉ tính một" names a day; guests can open A10 (FR-DC-08 actors) |

## Waits for other modules (deferred cross-module dependencies)

Every row is also in the register in `.claude/skills/nearby-feature-delivery/references/01-project-status.md`, and the module named in "Closed by" must close it.

| ID | Listing behaviour (SRS) | Waits for | Stand-in until then | Hook built now | Closed by |
|---|---|---|---|---|---|
| LS-W1 | FR-LS-04/BR-07: 3/day limit applies only **until the first completed transaction** | Transaction (FR-TX-08) | No completed transactions known → the limit always applies | SPI `listing.SellerTradeHistory.hasCompletedTransaction(sellerId)`, injected as `List<>`/`ObjectProvider` | Transaction implements it |
| LS-W2 | FR-LS-08: delete refused while OFFERED/ACCEPTED exist (409 names the transaction) | Transaction | "No open transaction" | SPI `listing.OpenTransactionCheck.findOpenTransaction(listingId)` | Transaction implements it |
| LS-W3 | `tab:sm-listing` ACTIVE ⇄ RESERVED → SOLD / ACTIVE from TransactionAccepted / Cancelled / Expired / Completed; FR-LS-07 / FR-LS-06 refusals while RESERVED | Transaction (FR-TX-03/05/06/08/10/12) | RESERVED never occurs; domain rules tested in unit tests only | Facade `listing.ListingReservations` (`reserve`, `release`, `markSoldByTransaction`) | Transaction calls it in its own transaction (DEC-LS-2) |
| LS-W4 | FR-LS-09 (a) sold to a chat partner, `SaleMarked`, 72 h `SaleDenied` → ACTIVE, RESERVED → SOLD for the ACCEPTED buyer | Transaction + Chat | Only "sold outside the app" | Domain transitions SOLD ⇄ ACTIVE (deny window) and the facade methods `markSoldToPartner`, `revertDeniedSale` | Transaction |
| LS-W5 | FR-LS-09 (b) and `ListingStatusChanged`: other OFFERED transactions become REJECTED on SOLD/REMOVED | Transaction | No offers exist | `ListingStatusChanged` event published now | Transaction listens to it |
| LS-W6 | FR-LS-11 counters: `chat_count`, open offers | Chat (FR-CH-01), Transaction | 0 | Facade `listing.ListingCounters.incrementChatCount`; SPI `listing.OpenOfferCounts` for the open-offer figure | Chat calls the facade; Transaction implements the SPI |
| LS-W7 | FR-DC-03 "relevant" ranking and FR-DC-05 card tier use `seller_score`/`seller_tier` | Reputation (FR-RP-07/08/09, OI-05) | Score 50, tier NEW (FR-DC-03 "điểm nền 50", FR-RP-01) | Facade `listing.SellerScores.update(sellerId, score, tier)` | Reputation calls it on every recompute |
| LS-W8 | FR-DC-08 detail: Chat / Offer / Report buttons act; seller card shows score, tier, reviews | Chat, Transaction, Moderation (FR-MD-01), Reputation | Detail returns each button's enabled flag and reason (gate, range, area expired, own listing, guest); seller card shows tier from the copy | Response fields `actions.chat/offer/save/report` | Each module adds its endpoint; contract updated then |
| LS-W9 | `tab:sm-listing` "Mọi trạng thái chưa kết thúc → REMOVED" by a moderator with reason | Moderation (FR-MD-02) | No removal path | Domain transition `remove(reason, moderatorId)` + facade `listing.ListingModeration` | Moderation calls it |
| LS-W10 | FR-LS-05, `ContentFlagged` → PENDING_REVIEW, screen W04 approve/reject | Moderation R2 | Not built (R2) | Statuses in the CHECK and the enum | R2 |
| LS-W11 | UC-SY-08 notify favouriters of price drops / status changes | Notification R2 | Not built | `ListingPriceDropped`, `ListingStatusChanged` published now | Notification (R2) |
| LS-W12 | FR-LS-08: the conversation about a DELETED listing stays readable (FR-CH-11) | Chat | No chat | `ListingLookup` returns DELETED/REMOVED listings to modules (not to the public) | Chat |
| X-W1 | `locality_name` on cards (D9) | Area open question (how `locality_name` is produced) | NULL | Copied at create | Area follow-up |
| X-W2 | UC-SY-15 must run on one instance (NFR-20) | ShedLock (platform) | Job is safe to run twice | `@Transactional run()` | Platform task |

## Phases

Each phase ends green (`./gradlew build`), updates `01-project-status.md` (FR status **and the deferred register**), and re-reads its SRS passages first.

### Phase 0. Groundwork

- `V14__listing_v2.sql`: extensions `unaccent`, `pg_trgm`; IMMUTABLE `f_unaccent`; drop/recreate `listings`, `listing_images`, `favorites`; alter and re-seed `categories`; all CHECKs above; parameters above; delete `location.blur_meters`.
- `ErrorCodes`: `IMAGE_INVALID`, `LISTING_DAILY_LIMIT`, `LISTING_OPEN_LIMIT`, `INVALID_LISTING_STATE`, `LISTING_HAS_OPEN_TRANSACTIONS`, `BUMP_TOO_SOON`, `BUMP_LIMIT_REACHED`, `CATEGORY_INACTIVE`, `LISTING_NOT_FOUND`. `ApiPaths` for listings, images, categories, feed, search, map, favourites. `RedisKeyPrefixes` for daily quota and view dedup. `StorageKeyPrefixes.LISTING_INCOMING` / `LISTING_IMAGES`.
- Move the type/size part of `ImageUploadPolicy` to `shared.storage` (avatar tests stay green).
- Routing read `DataSource` (D8), tested with the replica URL equal to the primary.
- `package-info`: `allowedDependencies = shared, identity, areaverification`.

### Phase 1. Domain (pure Java, test-first, ≥ 90 % branches)

| Class | Responsibility | Key tests (SRS numbers) |
|---|---|---|
| `ListingStatus`, `HiddenReason`, `ItemCondition` | Enums verbatim | — |
| `Listing` (aggregate) | One transition method per row of `tab:sm-listing` (all 19 rows, including LS-W3/LS-W4/LS-W9 rows); `version`; edit rules (no area change; no price change when RESERVED; no edit when SOLD/REMOVED/DELETED); `bump` (48 h, 10 max); `hide`/`unhide` OWNER; `hideForArea`, `hideForSanction`, `restoreAfterSanction` (only `SANCTION`); `sold_at` set/cleared | Parameterized: every allowed row; every other (state, event) pair → `INVALID_LISTING_STATE` (FR-LS-13); price drop reported only when lower; bump at 47:59 vs 48:00, 10th vs 11th |
| Value objects `Title` (5–100), `Description` (≤ 2000), `Price` (0–1 000 000 000, `isFree`), `MeetingSpot` (≤ 100), `ImageOrder` (1–10, first = cover) | Field rules LST (`tab:validation`: trim, strip control characters) | 4/5 and 100/101 chars; price −1/0/1e9/1e9+1; 0 and 11 images; duplicate image id |
| `PublicLocation` (via `GridSnapper`, `listing.blur_m`) | Blur, deterministic | Same pin → same cell; pin–cell ≤ 212 m (appD); returned point ≠ pin |
| `PostingQuota` | Daily limit (unless `hasCompletedTransaction`), open limit | 3rd allowed, 4th → `LISTING_DAILY_LIMIT` with remaining 0; 30th allowed, 31st → `LISTING_OPEN_LIMIT`; ACTIVE + RESERVED + PENDING_REVIEW counted, HIDDEN/SOLD not |
| `DisplayDistance` | 100 m rounding < 1 km, 0.1 km ≥ 1 km | 949 → 900 m; 950 → 1 000 m shown as 1.0 km; 1 049 → 1.0 km; 1 050 → 1.1 km (half-up rounding, recorded in the contract) |
| `RelevanceScore` | appD: w1(1 − d/D) + w2·R/100 + w3·exp(−age/7 d) | weight change changes order; R = 50 default; ties by `feed_at`, then `id` |
| `SearchCursor` | One shape for every sort: (sort, `rankedAt`, rank, `feed_at`, `id`), base64url, version-tagged. `rankedAt` freezes relevance freshness across pages | round trip; garbage, other version or other sort → `INVALID_CURSOR` |
| `SearchRadius` | Clamp to 1–20 km with `radiusAdjusted` | 25 → 20 + flag; 0.5 → 1 + flag; 20 → no flag |
| `ListingImage` | UPLOADING → READY / REJECTED; attach once, owner only | another user's image or REJECTED → `IMAGE_INVALID` |
| ~~`DetailActions`~~ | Moved to Phase 2: the button rules depend on FR-CH-01 / FR-TX-01 preconditions, read with the detail use case | — |

**Phase 1 done (2026-11-22).** Built: `ListingStatus` (12 events, 19 allowed rows, every other pair refused), `Listing` entity, `ListingImage`, `Category`, `ListingTitle/Description/MeetingSpot/Price/ListingContent`, `ImageOrder`, `ListingLocation`, `BumpRules`, `PostingQuota/History/Allowance`, `DisplayDistance`, `RankWeights`, `RelevanceScore`, `SearchRadius`, `SortOrder`, `SearchCursor`, application `ListingParameters`; shared `InvalidRequestException.invalidField` (per-field `VALIDATION_FAILED` from the domain). Domain coverage 99.1 % lines, 94.1 % branches.

**SRS readings taken in Phase 1** (cite `tab:sm-listing`, FR-LS-06/07/10, `tab:sr-ls`):

| # | Reading | Why |
|---|---|---|
| R1 | Owner "Hiện" (HIDDEN → ACTIVE) only when `hidden_reason = OWNER`; AREA_REMOVED and SANCTION listings are not the owner's to show | FR-LS-07 sets `OWNER`; FR-AR-07 / FR-MD-05 would be bypassed otherwise |
| R2 | `AreaRemoved` on an owner- or sanction-hidden listing replaces the reason with `AREA_REMOVED` (row "ACTIVE hoặc HIDDEN → HIDDEN") | The area is gone, so neither the owner nor a sanction lift may show it again |
| R3 | "Mọi trạng thái chưa kết thúc" = every state but DELETED and REMOVED; SOLD stays removable | SOLD listings remain searchable with `includeSold` (FR-DC-02) |
| R4 | Bump cooldown counts from the last bump; the first bump has no cooldown | FR-LS-10 "tối đa một lần mỗi 48 giờ" limits bumps, not age |
| R5 | Hide, delete and price change of a RESERVED listing → `409 LISTING_HAS_OPEN_TRANSACTIONS` with `transactionId` | `tab:sr-ls` "Từ chối, nêu giao dịch đang giữ tin" for all three |
| R6 | The R2 screening rows (flag → PENDING_REVIEW, approve, reject) are not built; PENDING_REVIEW is only a source state | FR-LS-05 is R2 |
| R7 | Distance rounding is one 100 m step everywhere; the app shows "m" below 1 km and "km" with one decimal from 1 km | FR-DC-05: 100 m and 0.1 km are the same step |
| R8 | Title and meeting spot drop every control character; the description keeps line breaks (`

` → `
`); lengths count code points | `tab:validation` "loại bỏ ký tự điều khiển"; `char_length` in the DB |

### Phase 2. Application

- **UC-LS-01 step 1 (posting eligibility):** gate status + remaining daily quota + open count, so A12 can show them before the form.
- **UC-LS-01 create** (steps 6–9 in SRS order): `@Idempotent`; `TradingGate.requireOpen` → `ListingPinRule.requirePinInsideArea` → `PostingQuota` → fields + active category → images READY, owned, unattached → save ACTIVE with blur, `published_at`, `locality_name`, images in order → `ListingPublished`. A4 (same key → original 201) by `@Idempotent`.
- **UC-LS-02 edit:** owner + `version`; area immutable; re-blur when the pin moves (checked again by `ListingPinRule` on the same area); images reordered/added/removed (removed ones become orphans); `ListingPriceDropped` once per drop.
- **UC-LS-03/04/06:** hide/unhide, soft delete (`OpenTransactionCheck`, LS-W2), bump.
- **UC-LS-05 A1:** sold outside the app (ACTIVE → SOLD; Idempotency-Key + `version`); `ListingStatusChanged`.
- **UC-LS-07/08/09:** my listings grouped by status with counters; favourites (insert-if-absent; counter ±1 only when a row was inserted/deleted; "no longer available" flag for DELETED/REMOVED).
- **UC-SY-10:** presigned PUT under `incoming/{userId}/{imageId}`; `upload-complete` → async processing (DEC-LS-4) on a bounded executor; reject deletes the object; originals never served.
- **UC-SY-15:** hourly orphan job.
- **Listeners** (all run twice safely): `AreaRemoved`, `UserSanctioned`, `SanctionLifted`, `AccountDeleted`.
- **Detail button rules (moved from Phase 1):** `DetailActions` after reading FR-CH-01 and FR-TX-01 preconditions.
- **Discovery (`readOnly = true`):** feed (FR-DC-01/04: center from `FeedArea` or a client point; ACTIVE + RESERVED within radius; blocked sellers excluded with `UserBlocks.blockedEitherWay`); search (FR-DC-02/03/06: unaccent + trigram/tsvector, filters, sort nearest/newest/relevant, clamp, `includeSold`); map (FR-DC-07); detail (FR-DC-08 + FR-LS-14 view count, never blocking); public seller listings (D12).

**Phase 2 done (2026-11-22)**, in three green steps:

- **A. Commands:** `ListingPublishingService` (posting eligibility, create, edit), `ListingLifecycleService` (hide, unhide, delete, bump, sold outside the app), `MyListingsService`, `FavoriteService`, `ListingSafetyListeners` (`AreaRemoved`, `UserSanctioned`, `SanctionLifted`, `AccountDeleted`), `PostingQuotaCheck` (advisory lock per seller), `ListingEvents`; repositories `ListingRepository`, `ListingImageRepository`, `CategoryRepository`, `FavoriteRepository`; events and SPIs in the module root (`ListingPublished`, `ListingPriceDropped`, `ListingStatusChanged`, `OpenTransactionCheck`, `SellerTradeHistory`, `OpenOfferCounts`); area facade `AreaLocality`.
- **B. Images:** `ListingImageService` (upload ticket, upload-complete, status), `ListingImageQueue` (private 2-thread pool, caller runs when full), `ListingImageProcessor`, `ImageTranscoder` port with `WebPImageTranscoder` (TwelveMonkeys `imageio-jpeg` 3.15.3 + `com.github.usefulness:webp-imageio` 0.11.0), `ExifOrientation`, `ImageFormat` (magic bytes), `OrphanImageCleanupJob` (hourly); `ImageStorage.read/store`; code `IMAGE_NOT_FOUND`.
- **C. Discovery:** `DiscoveryQueries` (native PostGIS: radius, unaccent + trigram keyword, filters, NEWEST/NEAREST/RELEVANT keyset pages, viewport points or grid clusters), `DiscoveryService` (feed, search, map, categories, seller listings), `ListingDetailService` + `DetailActions` + `ListingViewCounter`; area facade `FeedArea.radiusMetersOf`.

Coverage after Phase 2: listing domain 100 % lines / 96.2 % branches, application 96.3 % / 84.0 %, infrastructure 93.9 % / 79.2 %.

**SRS readings taken in Phase 2:**

| # | Reading | Why |
|---|---|---|
| R9 | Deleting a listing (owner or anonymisation) replaces the pin with the public point; its images are detached and the hourly orphan job deletes them | `tab:retention`: pin kept "tới khi tin bị xoá"; "ảnh dẫn xuất xoá khi tin bị xoá" |
| R10 | Account anonymisation moves every listing that is not DELETED or REMOVED (SOLD included) to DELETED; REMOVED listings keep their state but lose the pin | `tab:retention` "tin chuyển DELETED" |
| R11 | `ListingPriceDropped` only for an ACTIVE listing | FR-LS-06 acceptance names ACTIVE; a hidden listing's favouriters cannot see it |
| R12 | Derivatives bound the longer side to 320 / 1080 px, never enlarge, and apply the EXIF orientation before metadata is dropped; sources over 50 MP are refused (`TOO_LARGE`) | FR-LS-02 gives sizes only; phone photos rely on the orientation tag; decompression bombs |
| R13 | Upload-complete without an uploaded object → 400 `INVALID_IMAGE`; an object over 5 MB is rejected and deleted at once; type mismatch, HEIC and decode errors come back as `REJECTED` with a reason on the status call; another user's image → 404 `IMAGE_NOT_FOUND` (name chosen) | FR-LS-02 exceptions; SRS has no code for an unknown image |
| R14 | The daily posting limit counts listings created in the last 24 h from the database (deleted ones too), under a per-seller advisory lock, instead of a Redis counter | Exact, survives Redis loss, and parallel requests cannot both pass; DEC-LS-7's rolling 24 h window is unchanged |
| R15 | Between users who blocked each other: feed and search leave the seller out, detail and seller listings answer 404 / empty | FR-DC-01 and FR-ID-14 (never reveal a block) |
| R16 | A guest without a point gets 400 `VALIDATION_FAILED` on `center` | FR-DC-04 "gợi ý chọn điểm trên bản đồ" |
| R17 | Map: up to 200 listings in the viewport come back as markers, more as clusters on a Web Mercator grid of about 60 px at the requested zoom, coarsened until at most 200 | FR-DC-07 |
| R18 | Views: the owner's own views are not counted; guests are keyed by a fingerprint the web layer derives from IP and User-Agent (DEC-LS-8) | FR-LS-14 |

### Phase 3. Public API for other modules (module root)

Facades: `ListingLookup` (status, seller, area, price, `accept_offers`, public location; includes DELETED/REMOVED for modules), `ListingReservations` (LS-W3/LS-W4), `ListingCounters` (LS-W6), `SellerScores` (LS-W7), `ListingModeration` (LS-W9). SPIs: `OpenTransactionCheck` (LS-W2), `SellerTradeHistory` (LS-W1), `OpenOfferCounts` (LS-W6). Events: `ListingPublished`, `ListingPriceDropped`, `ListingStatusChanged`. Every SPI has a test for the "no implementation" default.

**Phase 3 done (2026-11-22).** `ListingModuleApi` implements `ListingLookup` (`ListingSnapshot`: status, seller, area, price, `acceptOffers`, public point, holding transaction; `takesNewChats` / `takesNewOffers`), `ListingReservations` (`reserve` idempotent for the same transaction, `release`, `completeReservedSale`, `markSoldToChatPartner`, `revertDeniedSale`), `ListingCounters` (`incrementChatCount`), `SellerScores` (`update` copies score and tier onto all the seller's listings) and `ListingModeration` (`remove` with reason, in the caller's transaction). All join the caller's transaction and publish `ListingStatusChanged`.

| # | Reading | Why |
|---|---|---|
| R19 | A new listing copies the seller's latest pushed score and tier from their other listings; with none yet, score null (ranked as 50) and tier NEW | FR-DC-03 "điểm nền 50", FR-RP-01; keeps one push mechanism (DEC-LS-2) without a new table |
| R20 | Facade statuses are strings (`ListingSnapshot.status`, `ListingStatusChanged`) with helper predicates, so other modules never import listing internals | Module boundary rule (reference 04) |

### Phase 4. Web

| Endpoint | Success | FR / UC / screen |
|---|---|---|
| `GET /listings/posting-eligibility` | 200 gate status, remaining daily quota, open count | UC-LS-01 step 1, A12 |
| `POST /listings/images/upload-url` | 200 presigned PUT + `imageId` | FR-LS-02, A13 |
| `POST /listings/images/{imageId}/complete` | 202 processing | UC-SY-10 |
| `GET /listings/images/{imageId}` | 200 status READY/REJECTED (+ thumb URL, reject reason) | A13 polling, UC-LS-01 A5 |
| `POST /listings` (Idempotency-Key) | 201 | UC-LS-01, A12 |
| `PUT /listings/{id}` (`version`) | 200 | UC-LS-02, A12 |
| `POST /listings/{id}/hide`, `/unhide` (`version`) | 200 | UC-LS-03, A14 |
| `DELETE /listings/{id}` | 204 | UC-LS-04, A14 |
| `POST /listings/{id}/bump` | 200 | UC-LS-06, A14 |
| `POST /listings/{id}/sold-outside` (Idempotency-Key, `version`) | 200 | UC-LS-05 A1, A14 |
| `GET /listings/me?status=` | 200 grouped with counters | UC-LS-07, A14 |
| `PUT` / `DELETE /listings/{id}/favorite` | 204 | UC-LS-08, A10 |
| `GET /users/me/favorites` | 200 cursor page | UC-LS-09, A15 |
| `GET /users/{id}/listings` | 200 cursor page (public) | FR-ID-13, A11 (D12) |
| `GET /categories` | 200 active categories | A08, A12 |
| `GET /feed` | 200 cursor page (public; guests pass a point) | UC-DC-01, A06 |
| `GET /search` | 200 cursor page + `radiusAdjusted` | UC-DC-02, A07/A08 |
| `GET /map/listings` | 200 points or clusters (≤ 200) | UC-DC-03, A09 |
| `GET /listings/{id}` | 200 detail; the owner also sees the exact pin | UC-DC-04, A10 |

Integration tests assert: no public response contains the exact pin (NFR-15, BR-05) and returned points equal the cell centre; HIDDEN/PENDING_REVIEW/DELETED/REMOVED never appear in feed/search/map, HIDDEN still in "my listings"; "ao khoac" finds "Áo khoác"; SOLD only with `includeSold`; pages never repeat or skip when a listing is inserted mid-pagination; bumped listing above older ones; 25 km → 20 km + flag; min > max price → 400; ≤ 200 map points; another seller's listing on owner endpoints → 404; DELETED → public 404; ten views by one viewer in a day → +1.

**Phase 4 done (2026-11-22).** Controllers `ListingController`, `ListingImageController`, `FavoriteController`, `DiscoveryController`; request/response records in `listing/web/dto` (query parameters bound as records, so a bad value is a `400 VALIDATION_FAILED` on its field); `GuestFingerprint` (SHA-256 of client address and User-Agent) for guest view counts. Final paths: as the table above, with `GET /listings/images/{imageId}` for status, `PUT`/`DELETE /listings/{id}/favorite`, `GET /users/me/favorites`. Shared changes: `SecurityConfig` lets guests `GET` feed, search, map, categories, listing detail and `/users/{id}/listings` (seller reads under `/listings` stay authenticated); `GlobalExceptionHandler` now answers a wrongly typed query value and a violated `@RequestParam` constraint with `400 VALIDATION_FAILED` and `errors[]` (before: a 400 without `code`) for every module. 28 API tests (`ListingApiIntegrationTests`).

### Phase 5. Contract

`docs/api/listing-api.md` (flows: post a listing A12/A13 with the trading-gate CTA from FA5, manage A14, browse A06/A07/A08, map A09, detail A10 with button reasons, favourites A15, public seller listings A11; a "Waits for" section listing LS-W1..LS-W12, X-W1, X-W2 so the app team knows which buttons and counters are placeholders), regenerated `openapi.yml`, `docs/postman/nearby-listing.postman_collection.json`, README and `docs/prompts` links.

**Phase 5 written (2026-11-23).** `docs/api/listing-api.md` (scope, conventions, 21 endpoints, lifecycle diagram, flows FL1–FL9 for A06–A15 and A11, endpoint details, events, "Waits for other modules", parameters, job); `docs/api/openapi.yml` regenerated (18 new paths, no path removed; query records shown as separate parameters through `@ParameterObject`); `docs/postman/nearby-listing.postman_collection.json` (6 folders, 31 requests in flow order, valid JSON, not run: no Newman here); links in `README.md`, `docs/prompts/README.md`, `docs/prompts/android-user-app.md`; the shared 400 change is noted in `identity-api.md` and `area-api.md`. The last `POST /listings` 400 description was brought in line by hand (same text springdoc renders for the identical annotation on `PUT /listings/{id}`), because the app could not be started again for low memory.

### Phase 6. Definition of done

Clean-code checklist (reference 07); `./gradlew build` green with Docker; FR codes in test display names; status file (FR status, decisions, **deferred register LS-W1..LS-W12, X-W1, X-W2**), roadmap (each W row noted in the closing module's section) and decision log updated; report with remaining gaps (LS-W1..LS-W12, X-W1, X-W2, R2 items, NFR-05 radius search over 10 000 listings load test).

**Phase 6 done (2026-11-23).** Checklist 07 passed: no direct `now()`, no TODO, no `Thread.sleep`, no narrating comments, the pin leaves the module only to its owner (`OwnerListingResponse`, owner branch of `ListingDetailResponse`; `ListingSnapshot` has only `publicPoint`); every listing test display name now cites an FR/UC/BR/NFR or screen code. `./gradlew format` + `./gradlew build` green with Docker: 1 262 tests, 0 failures, `ApplicationModules.verify()` green. Coverage: domain 100 % lines / 96.3 % branches, application 96.6 % / 84.0 %, infrastructure 93.9 % / 79.2 %, web 94.5 % / 66.7 %; project 97.4 % / 89.7 %. Still open: LS-W1..LS-W12, X-W1, X-W2 (register), R2 items (FR-LS-05 screening, UC-SY-08 favourite notifications), NFR-05 load test (radius search over 10 000 listings), Postman collection not run (no Newman).

**Re-review (2026-11-23), every R1 FR-LS/FR-DC row against its acceptance criteria and exceptions.** All covered by code and tests, with three findings: (1) FR-LS-14 "a counting failure never blocks the detail" and (2) FR-LS-15 "an existing listing keeps a deactivated category and stays visible" were implemented but untested; tests added to `DiscoveryIntegrationTests`. (3) FR-DC-02 E2 `429 RATE_LIMITED` on search is not enforced: `tab:ratelimits` puts it under "Endpoint công khai khác … Nginx/Redis (TBD tại Gate 4)", so it is a platform task, now register row X-W3 and noted in `listing-api.md`. D13's text corrected (the window is counted in PostgreSQL, not Redis). Two register rows were missing for events listing does not own but the earlier modules publish for Notification: AR-W2 and ID-W4 (added).

## Order of work (small reviewable steps, each green)

1. Phase 0: V14 + params + error codes + shared image policy + read path + `package-info`.
2. Domain: `Listing` state machine and value objects, quota, blur, distance, cursor, radius, relevance.
3. Images: upload URL, upload-complete processing, status polling, orphan job.
4. Create (+ eligibility) / edit / hide / delete / bump / sold-outside / my listings / favourites + listeners.
5. Discovery: feed, search, sorting, map, detail + view count, public seller listings.
6. Public API for Transaction, Chat, Reputation, Moderation (facades, SPIs with defaults).
7. Web + contract + Postman; status file and deferred register final pass.
