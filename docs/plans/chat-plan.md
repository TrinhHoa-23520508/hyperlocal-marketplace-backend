# Plan: Chat + Notification (`chat` module)

Status: **v3, 2026-11-24: Phases 0-6 done for R1.** D1-D12 were taken as recommended on 2026-11-23 (the user said "start phase 0"). Written on branch `feature/listing`. The acceptance traceability is at the end of this file.

v2 changes (second full read of the SOT): CH-W5 added (UC-TX-01 step 7, an offer creates the conversation); D3 now keeps the dictionary's composite PKs; readings R8–R10 added (field group NOT, WebSocket push-only, unavailable deep-link target); NT-W1 notes the FR-NT-01 "no reminder after cancel" exception; every phase now ends with the dependency gate (skill workflow step 13).

Sources (SOT = `docs-local/SRS_Hyperlocal_Marketplace_LaTeX/`; re-read the exact passages at the start of every phase):

| What | Where |
|---|---|
| Chat description, stimulus/response `tab:sr-ch`, FR-CH-01..12 | `tex/ch3_features.tex` lines 1668–1994 |
| Notification description, `tab:sr-nt`, FR-NT-01..06 | `tex/ch3_features.tex` lines 3184–3358 |
| UC index (CH 45–50, NT 78–79, SY-16 112); full spec of UC-CH-02 | `tex/appC.tex` lines 259–384; sequence diagram `fig/src/uc_ch_02_seq.puml` |
| Field rules, groups CHT and NOT | `tex/ch3_features.tex` lines 13 and 17 (`tab:fieldrules`) |
| UC-TX-01 step 7 (offer creates the conversation and posts a system message); FR-ID-20 included by UC-CH-01 | `tex/appC.tex` UC-TX-01; `tex/ch3_features.tex` line 27 |
| Data dictionary `conversations`, `conversation_participants`, `messages` | `tex/ch4_dd.tex` lines 354–397 |
| Data dictionary `notifications`, `device_tokens`, `notification_preferences` | `tex/ch4_dd.tex` lines 833–882 |
| Integrity (UK `conversations(listing_id, buyer_id)`, `notifications(source_event_id, recipient_id)`), retention, `tab:params` | `tex/ch4_tail.tex` lines 22, 40–55, 134; schema delta line 189 |
| Error catalogue | `tex/ch3_tail.tex` (`MESSAGE_TOO_LONG`, `CHAT_RATE_LIMITED`, `CONVERSATION_CLOSED`, `CONTACT_NOT_ALLOWED`, `OUT_OF_RANGE`, `TRADING_GATE_FAILED`) |
| Communication interfaces (STOMP, RabbitMQ, Redis presence, MinIO 10-minute URLs, FCM), rate limits `tab:ratelimits` | `tex/ch5.tex` lines 100–157 |
| Events and jobs; ADR-12 (api/realtime/worker roles), ADR-13 (RabbitMQ STOMP relay), ADR-14 (ShedLock) | `tex/appB.tex` lines 8–66, 81–90 |
| NFR-12 rate limits, NFR-20 two replicas, NFR-21 outbox, NFR-31 image privacy | `tex/ch6.tex` |
| OI-04 (`messages.body` 2 000 vs 1 000) | `tex/appF.tex` |

## Scope

### Functional requirements

| FR | Name | Priority / release | In this plan |
|---|---|---|---|
| FR-CH-01 | Open a conversation | M / R1 | Yes. Unique (listing, buyer); gate, range, blocks, own listing, listing ACTIVE/RESERVED; `ListingCounters.incrementChatCount` (closes LS-W6, chat part) |
| FR-CH-02 | Real-time send | M / R1 | Yes. STOMP over WSS through the RabbitMQ relay (D2); REST fallback; dedup by `client_msg_id` (D4); `MessageSent` through the outbox |
| FR-CH-03 | Text limits | M / R1 | Yes. 1–1 000 characters (`chat.max_message_chars`), 30 per minute per user (`chat.rate_limit_per_min`) |
| FR-CH-04 | Image messages | S / R1 | Yes. Up to 5 images ≤ 5 MB each, presigned upload, the shared WebP pipeline (D8), short-lived signed URLs for the two members only |
| FR-CH-05 | Meeting-point message | S / R1 | Yes. lat/lng + place name, inside Vietnam (D10), members only (BR-05) |
| FR-CH-06 | Read state and unread count | M / R1 | Yes. `last_read_message_id` per member; markers that go backwards are ignored; `unread_count` kept in step |
| FR-CH-07 | Sync on reconnect | M / R1 | Yes. `messages.id` (bigserial) is the checkpoint; `GET` missed messages after an id, in order |
| FR-CH-08 | System messages | S / R1 | **Hook only.** The SYSTEM message type and the internal poster are built now; the producers are Transaction and Payment events, which do not exist yet (CH-W1) |
| FR-CH-09 | Mute / leave | C / **R2** | No. Columns `muted`, `left_at` are created (dictionary) but stay at their defaults |
| FR-CH-10 | Scam warning | S / **R2** | No. `risk_flag` column, always false in R1 |
| FR-CH-11 | Readable after the end; send lock | M / R1 | Yes. Readable after SOLD/DELETED/REMOVED (closes LS-W12); sending refused with 403 `CONVERSATION_CLOSED` when the listing is REMOVED, either side blocked the other, or either side is suspended, banned or deleted |
| FR-CH-12 | Report a message | S / R1 | **Hook only.** The `reports` table and W03 belong to Moderation; chat ships the facade that returns the message and its 20 previous messages (CH-W2) |
| FR-NT-01 | In-app notification types | S / R1 | Yes for the producers that exist: messages while offline (chat), area expiring/expired (area), sanction (identity), welcome (identity, appB). The trade, payment, review and moderation types wait for their modules (NT-W1..NT-W4) |
| FR-NT-02 | FCM push | S / R1 | Yes. One token per device; invalid tokens deleted; FCM failure never breaks a business flow (D6) |
| FR-NT-03 | Preferences | S / R1 | Yes. CHAT, TRADE, MARKETING, SYSTEM switches + quiet hours; SYSTEM cannot be turned off (400); MARKETING needs the PUSH_MARKETING consent; KEYWORD_ALERT column exists, R2 |
| FR-NT-04 | Dedup | S / R1 | Yes. UK (`source_event_id`, `recipient_id`); `source_event_id` derived per D5 |
| FR-NT-05 | Notification list | S / R1 | Yes. Cursor page, newest first, mark read, mark all read, deep link |
| FR-NT-06 | Unread badges | S / R1 | Yes. Chat + notification counts by REST and pushed over WebSocket to every session of the user |
| FR-ID-03 (gap) | Register the device FCM token | M / R1 | Yes, through `PUT /devices/{deviceId}` (D7). Closes ID-W1 |

### Use cases, screens, events, jobs

- **UC:** CH-01 open, CH-02 send text (main 1–8, A1–A5, E1–E6), CH-03 images, CH-04 meeting point, CH-05 conversation and read state; NT-01 view notifications, NT-02 preferences; SY-16 send notifications; SY-10 image processing (chat part).
- **Screens:** A10 (Chat button, already shipped by listing), A17 conversation list, A18 chat, A13 point picker (reused for the meeting point), A29 notifications, A30 notification settings, A34 report sheet (Moderation).
- **Events produced:** `MessageSent` (appB consumers: notification push when offline, moderation scam screening R2).
- **Events consumed now:** `MessageSent` (notification, inside the module); `UserRegistered` (preferences row + welcome notification); `UserSanctioned` (SYSTEM notification; device tokens already removed by `DeviceTokenCleanup`); `SanctionLifted` (SYSTEM notification); `AreaExpiring`, `AreaExpired` (SYSTEM notification with a deep link to A28); `AccountDeletionRequested` (tokens, existing); `AccountDeleted` (delete preferences and notifications of the user; messages stay with an anonymous author, ch4 retention). `DeviceSignedOut` (existing).
- **Events consumed later** (each is a register row): Transaction and Payment events for system messages and TRADE notifications, Reputation events for review notifications, Moderation events for report/dispute/appeal results, R2 `ListingPriceDropped`/`ListingStatusChanged`/`KeywordAlertMatched`.
- **Jobs:** notification retention, daily, deletes notifications older than `notification.retention_months` = 6 (ch4 retention); chat upload orphan cleanup, hourly, same rule as UC-SY-15 (`upload.orphan_ttl_hours` = 24). Both `@Transactional run()`, safe to run twice (X-W2, no ShedLock yet).

### Tables (target shape from `ch4_dd.tex`)

V7 created early, incompatible versions of `conversations`, `messages`, `notifications`, `device_tokens` with no meaningful rows. **V15 drops and recreates them** (the V12/V14 approach) and adds `conversation_participants` and `notification_preferences`.

- `conversations`: `id`, `listing_id`, `buyer_id`, `seller_id` (CHECK ≠ buyer), `last_message_id`, `last_message_at`, `last_message_preview` varchar(100), `created_at`, `updated_at`; UK (`listing_id`, `buyer_id`); indexes for the list by member.
- `conversation_participants`: PK (`conversation_id`, `user_id`), `role` CHECK BUYER/SELLER, `last_read_message_id` NN DEF 0, `unread_count` NN DEF 0 CHECK ≥ 0, `muted` DEF false, `left_at`, `joined_at`; index (`user_id`) for A17 and the badge.
- `messages`: `id` bigserial, `created_at`, `conversation_id` FK, `sender_id` (NULL only for SYSTEM), `type` CHECK TEXT/IMAGE/LOCATION/SYSTEM, `body` varchar(1000) (CHECK type ≠ TEXT or body NOT NULL), `payload` jsonb (CHECK type = TEXT or payload NOT NULL), `client_msg_id` (NN except SYSTEM), `risk_flag` DEF false. **Not partitioned in R1 (D3)**; UK (`sender_id`, `client_msg_id`) (D4); index (`conversation_id`, `id`). OI-04 is closed by the 1 000 limit in both the column and the API.
- `chat_images` (**not in the dictionary, D8**): `id`, `owner_id`, `conversation_id`, `original_key`, `thumb_key`, `large_key`, `width`, `height`, `processing_status` UPLOADING/READY/REJECTED, `reject_reason`, `message_id` (set when attached), `created_at`, `processed_at`.
- `notifications`: the 13 dictionary columns; UK (`source_event_id`, `recipient_id`); index (`recipient_id`, `created_at` DESC, `id` DESC); partial index on unread rows for the badge. Not partitioned (D3).
- `device_tokens`: `id`, `user_id`, `device_id` varchar(64) NN, `fcm_token` varchar(255) UK, `platform` CHECK ANDROID/WEB, `app_version`, `last_seen_at`, `created_at`, `updated_at`; UK (`user_id`, `device_id`) so a device has one token (FR-NT-02).
- `notification_preferences`: `user_id` PK, `chat_push`, `trade_push`, `keyword_alert_push` (DEF true), `marketing_push` (DEF false), `dnd_enabled` (DEF false), `dnd_start` 22:00, `dnd_end` 07:00, `time_zone` DEF `Asia/Ho_Chi_Minh`, `updated_at`, `version`.

### Parameters (`tab:params`, verbatim)

`chat.max_message_chars=1000`, `chat.rate_limit_per_min=30`, `notification.retention_months=6`. Reused: `upload.max_image_mb=5`, `image.max_bytes=5242880`, `upload.orphan_ttl_hours=24`. Fixed by the SRS text, kept as constants with the FR in the name: 5 images per message (FR-CH-04), 100-character preview (dictionary), 20 messages of report context (FR-CH-12).

### Error codes

| Code | HTTP | Where (SRS) |
|---|---|---|
| `TRADING_GATE_FAILED` | 403 + `reasons` | open a conversation (FR-CH-01, access matrix). Exists |
| `OUT_OF_RANGE` | 403 | listing outside the buyer's interaction range (FR-CH-01, FR-AR-09). Exists |
| `CONTACT_NOT_ALLOWED` | 403 | open a conversation with someone blocked either way; neutral text, never says "blocked" (FR-CH-01, FR-ID-14). Exists |
| `INVALID_LISTING_STATE` | 409 | open a **new** conversation about a listing that is not ACTIVE/RESERVED, or about one's own listing (FR-CH-01 "Tin không còn khả dụng"). Exists; reused (D11) |
| `LISTING_NOT_FOUND` | 404 | unknown listing, or DELETED/REMOVED with no existing conversation. Exists |
| `CONVERSATION_NOT_FOUND` | 404 | unknown conversation or caller not a member (FR-CH-02 E4, FR-ID-18) |
| `CONVERSATION_CLOSED` | 403 | send while the listing is REMOVED, a block exists, or a member is suspended/banned/deleted; neutral text (FR-CH-11) |
| `MESSAGE_TOO_LONG` | 422 | text over 1 000 characters (FR-CH-03, UC-CH-02 E1) |
| `CHAT_RATE_LIMITED` | 429 + `Retry-After` | 31st message in a minute (FR-CH-03, UC-CH-02 E2) |
| `VALIDATION_FAILED` | 400 | empty text, more than 5 images, a point outside Vietnam, SYSTEM switched off (FR-CH-03 "400", FR-CH-05, FR-NT-03), unknown cursor values. Exists (DEC-LS-1) |
| `INVALID_IMAGE` / `IMAGE_INVALID` | 400 / 422 | chat upload rejected / an image id that is not the sender's READY image (same meaning as listing). Exist |
| `NOTIFICATION_NOT_FOUND` | 404 | mark read of someone else's or an unknown notification (*name chosen*) |
| `INVALID_CURSOR` | 400 | broken cursor. Exists |

## Current state and gaps

- **Exists:** `chat/package-info.java` (`allowedDependencies` = shared, identity, listing, transaction, areaverification, reputation); `chat.infrastructure.DeviceTokenCleanup` (deletes tokens on `DeviceSignedOut`, `AccountDeletionRequested`, `UserSanctioned`); `shared.websocket` (`WebSocketConfig` with a **simple in-memory broker**, `StompAuthChannelInterceptor` authenticating CONNECT with the JWT); `WebSocketDestinations`; `RateLimiter` (Redis); `ImageStorage` (presigned PUT/GET, read, store); identity facades `TradingGate`, `UserBlocks`, `AccountStanding`; area `InteractionRange`; listing `ListingLookup`, `ListingCounters`.
- **Stale:** V7 tables (no participants, `body` 2 000 = OI-04, no type/payload/client id, notifications without category/title/dedup key, `device_tokens` without `device_id`). The simple broker cannot deliver between two replicas (NFR-20, FR-CH-02 acceptance).
- **Missing:** everything else. The WebP pipeline lives inside `listing` and must move to `shared` to be reused (D8). Identity has no facade for display names/avatars of many users at once (D9) nor for the PUSH_MARKETING consent (D12). `ListingSnapshot` has no cover thumbnail for A17 (D9).

## Decisions to confirm

| # | Question | Recommendation | Why |
|---|---|---|---|
| D1 | Module layout | One `chat` module (already declared as "Chat + Notify"), with the notification code in `chat.notification.*` (`web`, `application`, `domain`, `infrastructure`) next to the chat layers | appB and the roadmap pair them; notification listens to every module, and chat is already last in the dependency chain, so no new module and no cycle |
| D2 | Broker | RabbitMQ STOMP broker relay (ADR-13): `rabbitmq:4-management` + `rabbitmq_stomp` in `compose.yaml`, `spring-boot-starter-reactor-netty` for the relay TCP client, property `app.websocket.relay.enabled` (true in `application.yml`, false in the default test profile so normal tests keep the simple broker), and **one** test class with a RabbitMQ Testcontainer that starts **two** app instances and proves FR-CH-02 "replica 1 → replica 2" | The SRS names the relay (FR-CH-02, NFR-20, ADR-13). Keeping the relay out of the other tests saves a container and memory on this machine. Alternative: relay in every test |
| D3 | `messages` (and `notifications`) partitioning | Not partitioned in R1, but keep the dictionary's PK verbatim: `messages` PK (`id`, `created_at`) with `id` from `messages_id_seq`, `notifications` PK (`id`, `created_at`). Monthly partitioning can then be added later without a key change (NFR-29 is R2 scale) | A partitioned table cannot have a unique key without the partition column, which would break the `client_msg_id` dedup that FR-CH-02 requires. Record as a reading in the status file |
| D4 | Dedup key for messages | UK (`sender_id`, `client_msg_id`); a repeat returns the stored message (UC-CH-02 A3) | The sequence diagram says "ON CONFLICT(client_msg_id)". Scoping it to the sender stops one user from reading another's message by guessing or reusing a UUID |
| D5 | `source_event_id` for FR-NT-04 | A name-based UUID (v5) computed from the event type and its identifying fields, e.g. `AreaExpiring:{areaId}:{expiresAt}`, `MessageSent:{messageId}`; no change to the events of other modules | Today's events carry no id. Adding one would change identity, area and listing events. A deterministic id gives the same value on every outbox replay, which is exactly what FR-NT-04 tests |
| D6 | FCM client | A `PushGateway` port; adapter for the FCM HTTP v1 API with `google-auth-library-oauth2-http` + `RestClient`, enabled when `app.push.fcm.credentials` is set; otherwise pushes are marked `SKIPPED` and logged. Timeout 5 s; `UNREGISTERED`/`INVALID_ARGUMENT` token errors delete the token | Lighter than the full Firebase Admin SDK, testable with a stub server; dev and CI need no Firebase project. FR-NT-02: failure never breaks a business flow |
| D7 | Where the app registers its FCM token (closes ID-W1) | `PUT /devices/{deviceId}` {`fcmToken`, `platform`, `appVersion`} in chat, called after login and whenever FCM rotates the token; upsert by (`user_id`, `device_id`), a token moved to another user is reassigned | Identity cannot call chat (cycle). FR-ID-03 asks only for "thông tin thiết bị"; the dictionary's `device_id` = the app installation id. Logout already removes the token (`DeviceSignedOut`) |
| D8 | Chat images | Move `ImageTranscoder`, `WebPImageTranscoder`, `ExifOrientation`, `ProcessedImage`, `UnusableImageException` from `listing` to `shared.image`; chat gets its own small `chat_images` table (upload → complete → READY/REJECTED, attached to one message) and processes asynchronously like listing | Two modules need the pipeline (shared rule). The dictionary stores only keys in `messages.payload`; something must track ownership and processing before a message exists, and FR-CH-04 "Xử lý lỗi: tin hiển thị kèm nút thử lại" implies asynchronous processing |
| D9 | Names, avatars and listing covers on A17/A18 | Add `identity.UserSummaries.summariesOf(Collection<UUID>)` (display name, avatar URL, deleted flag shown as "Người dùng đã xoá") and `ListingLookup.findAll(Collection<UUID>)` with a cover thumbnail URL on `ListingSnapshot` | A17 lists many conversations; one call per row would be slow. Listing's detail screen fetches the seller from `GET /users/{id}` because it shows one seller |
| D10 | "Inside Vietnam" for the meeting point (FR-CH-05) | Bounding box lat 8.0–23.5, lng 102.0–117.5 (mainland and islands), in `shared.geo` | The SRS gives no polygon; a box is testable and enough to reject obviously wrong points |
| D11 | Opening a conversation when one exists, and the 409 code | If the (listing, buyer) conversation exists, `POST` returns it (200) whatever the listing state, without the gate (reading is always allowed, FR-CH-11). A new one needs the gate, range, no block, not own listing, listing ACTIVE/RESERVED; otherwise 409 `INVALID_LISTING_STATE` | FR-CH-01: "Mở lần hai trả cùng định danh" and "cuộc trò chuyện cũ vẫn đọc được". The SRS names no code for "Tin không còn khả dụng"; listing's state-conflict code keeps one code for the app |
| D12 | "Offline" for message notifications and the PUSH_MARKETING consent | Presence in Redis (ch5 "trạng thái trực tuyến"): a per-user set of STOMP session ids, updated on connect/disconnect with a TTL refreshed by heartbeats; offline = no live session. A message to an online recipient creates no notification row (FR-NT-01 "tin nhắn khi ngoại tuyến"). Add `identity.MarketingConsent.allowsPushMarketing(userId)` | Works across replicas; a crashed replica's sessions expire by TTL. Identity owns consents, so notification asks it through a facade |

Readings added in Phase 1 (2026-11-23):

- **R11 — Deep links without an id:** `app://home` (welcome, report result placeholder) and `app://account` (sanction, lift, appeal result). The SRS lists screens only by name; Moderation fixes the report-result target (NT-W4).
- **R12 — Place name of a meeting point:** 1–100 characters, the same limit as the listing's meeting spot text (FR-LS-01); the dictionary gives none.
- **R13 — Quiet hours block every category's push, SYSTEM included** (FR-NT-03 acceptance: "trong khung giờ yên tĩnh push bị chặn"); start is inside the window, end is outside. Check order for a push: conversation muted (R2), category off, quiet hours, no device token.
- **R14 — Marketing without consent, SYSTEM off, an empty quiet window and an unknown time zone** all answer 400 `VALIDATION_FAILED` (field group NOT: "400 khi tắt SYSTEM"); the SRS names no other code.
- **R15 — A listing of an expired area takes no new conversation:** `InteractionRange.allowsNewContacts` false answers 409 `INVALID_LISTING_STATE` (FR-AR-03; the SRS names no chat code).
- **R16 — A repeated `client_msg_id` of the same sender in another conversation** is 400 `VALIDATION_FAILED` (D4: the key is per sender, so the stored message belongs to one conversation).
- **R17 — Presence** is a Redis sorted set per user (session id scored by its lapse time); each replica renews only its own sessions every 30 s, a session lapses after 90 s without renewal. Unavailable FCM credentials give push status SKIPPED (the dictionary lists SKIPPED for "lỗi FCM" too).
- **Domain shape:** `Conversation`, `Participant` and the preferences are plain Java records with the rules; Phase 2 persists them with `JdbcClient` and atomic SQL (`unread_count = unread_count + 1`) so concurrent sends do not lose updates. `messages` is not a JPA entity because of its composite key (D3).

Readings of the SRS taken without a question (tell the user, record in the status file):

- **R1 — DELETED vs REMOVED for sending.** FR-CH-11 locks sending only for REMOVED (and blocks/sanctions). Acceptance: "Sau khi tin SOLD hai bên vẫn nhắn được; sau REMOVED thì không". A DELETED listing therefore keeps its conversations open for sending.
- **R2 — Sanctions.** "một bên bị chặn, tạm khoá hoặc cấm" → `AccountStanding.isActiveWithVerifiedPhone` false for either member closes sending (neutral 403, never which side).
- **R3 — Read markers.** Opening a conversation sets the reader's marker to the newest message; `unread_count` is recomputed as messages after the marker not sent by the reader; an older marker is ignored (FR-CH-06). The badge update is pushed to all the reader's sessions (FR-NT-06 "trên mọi thiết bị").
- **R4 — Sync.** `GET /conversations/sync?afterMessageId=` returns every missed message of the caller's conversations in id order, at most 200 per call with `hasMore` (FR-CH-07 "toàn bộ tin bị lỡ theo thứ tự"); an unknown checkpoint (above the newest id) returns 400 `INVALID_CURSOR` and the app reloads (FR-CH-07 exception).
- **R5 — Categories.** CHAT = message notifications; TRADE = offers, sale, appointments, payments, handover, completion, reviews; SYSTEM = area, sanction, welcome, report/dispute/appeal results (FR-NT-03: SYSTEM and security notices cannot be turned off); MARKETING has no producer in R1.
- **R6 — Quiet hours** apply to push only; the in-app row is always created (FR-NT-03 acceptance). A window may cross midnight (dictionary `dnd_end`).
- **R7 — Deep links** use the dictionary's form `app://<screen>/<id>`: `app://conversations/{id}`, `app://areas/{id}` (A28), `app://transactions/{id}`, `app://listings/{id}`, `app://users/{id}`, `app://disputes/{id}`.
- **R8 — Field group NOT** ("SYSTEM luôn bật; khung giờ hợp lệ"): SYSTEM switched off → 400 `VALIDATION_FAILED`; a quiet-hours window with `dndStart` = `dndEnd` or an unknown time zone → 400. A window crossing midnight is valid (R6).
- **R9 — WebSocket only pushes** (ch5 "REST là nguồn sự thật; WebSocket chỉ đẩy"): every STOMP action has a REST twin with the same rules and codes; the client syncs through REST after a reconnect (R4).
- **R10 — Unavailable deep-link target** (FR-NT-05 exception): the server keeps the link as created; the target's own endpoint answers 404, and the contract tells the app to show "không còn khả dụng".

## Waits for other modules

### Rows this module opens (copied to the register as **Planned**)

| ID | Waiting feature (SRS) | Waits for | Stand-in now | Hook built now | Closed by |
|---|---|---|---|---|---|
| CH-W1 | FR-CH-08: one SYSTEM message per transaction transition and per sale marked/denied | Transaction (FR-TX-01..10), Payment | No system messages | Message type SYSTEM + internal `SystemMessagePoster.post(listingId, buyerId, kind, facts)` with dedup by source event | Transaction and Payment plans add the listeners in `chat` |
| CH-W2 | FR-CH-12: report a message with 20 previous messages as context, shown on W03 | Moderation (FR-MD-01, UC-SP-01) | No report path | Facade `chat.ReportableMessages.contextOf(messageId, reporterId)` (refuses one's own message) | Moderation |
| CH-W3 | FR-MD-09: moderators read the chat transcript of a dispute (audited) | Moderation | Not readable by staff | Facade `chat.ConversationTranscripts` (planned shape only, built by Moderation) | Moderation |
| NT-W1 | FR-NT-01 TRADE types: offer received/accepted/rejected/withdrawn/expired, sale marked (confirm/deny actions), appointment confirmed and reminder (UC-SY-06; FR-NT-01 exception: no reminder when the transaction was cancelled first), handover, receipt, completion | Transaction | Not sent | `NotificationCatalogue` entries reserved; listener pattern + `source_event_id` rule (D5) | Transaction |
| NT-W2 | FR-NT-01 payment succeeded, refund, payout | Payment | Not sent | Same | Payment |
| NT-W3 | FR-NT-01 review invite and review published | Reputation | Not sent | Same | Reputation |
| NT-W4 | FR-NT-01 report result, dispute, appeal | Moderation | Not sent | Same | Moderation |
| NT-W5 | FR-NT-02: push sent from the `worker` role (ADR-12) | Platform (runtime roles) | Sent by the async listener in the single app | `PushGateway` port | Platform task |
| CH-W5 | UC-TX-01 step 7: an offer creates the (listing, buyer) conversation if none exists, then posts the system message | Transaction (FR-TX-01) | Conversations are opened only from A10 (FR-CH-01) | Internal `ConversationOpener.openForTrade(listingId, buyerId, sellerId)`: same UK and race handling as UC-CH-01, no second gate/range check (Transaction already checked them), increments `chat_count` only when a row is created | Transaction (its `OfferCreated` listener in `chat` calls it before `SystemMessagePoster`) |
| CH-W4 | LS-W4 support: Transaction checks "is a chat partner" (FR-LS-09 (a) `NOT_A_CHAT_PARTNER`, UC-TX-05) | Transaction | — | Query `ConversationRepository.existsByListingAndBuyer`; Transaction will define SPI `transaction.ChatPartners`, chat implements it | Transaction |

### Register rows this module closes

| Row | How | Phase |
|---|---|---|
| ID-W1 (FCM token at login) | `PUT /devices/{deviceId}` (D7) | 2 + 4 |
| LS-W6, chat part (`chat_count`) | `ListingCounters.incrementChatCount` when a conversation is created | 2 |
| LS-W8, Chat button part | `POST /conversations` behind A10's Chat button; listing's detail already returns the button state | 4 |
| LS-W12 (chat about a deleted listing stays readable) | conversation reads use `ListingLookup`, which returns DELETED/REMOVED to modules | 2 |
| AR-W2 (area expiring/expired notifications) | listeners on `AreaExpiring`, `AreaExpired` | 2 |
| ID-W4 (welcome and sanction notifications) | listeners on `UserRegistered`, `UserSanctioned`, `SanctionLifted` | 2 |

### Changes to upstream modules (allowed direction: chat → them)

- `identity`: new facades `UserSummaries` (D9) and `MarketingConsent` (D12).
- `listing`: `ListingLookup.findAll`, cover thumbnail on `ListingSnapshot` (D9); the image pipeline moves to `shared.image` (D8).
- `shared`: `image` package (D8), `geo.VietnamBounds` (D10), `WebSocketConfig` relay switch (D2), ApiPaths, ErrorCodes, RedisKeyPrefixes (presence, chat rate limit), StorageKeyPrefixes (chat images).
- `chat/package-info.java`: drop `transaction` and `reputation` until they exist? **No**: keep them; chat is last in the chain and will listen to them (CH-W1, NT-W1, NT-W3).

## Phases

Each phase re-reads its SRS passages first, ends with `./gradlew build` green, and updates `01-project-status.md` (FR status **and the register**). No commits; ask the user before each next phase.

**Dependency gate (every phase, skill workflow step 13).** Before coding a phase, list which of CH-W1..W5 / NT-W1..W5 it touches. While coding, any new need for a module that is not built (Transaction, Payment, Reputation, Moderation, platform) gets a stand-in, a hook, a row in this plan's "Waits" table **and** in the register of `01-project-status.md`, in the same change. At the end of the phase, move rows whose hook shipped from *Planned* to *Open*, mark closed rows *Closed* (date) with the test that proves it, and write the opened/closed IDs in the phase report.

### Phase 0. Groundwork

V15 (`chat_notify_v2`: drop the V7 tables, create the six tables above, seed `chat.max_message_chars`, `chat.rate_limit_per_min`, `notification.retention_months`); error codes `CONVERSATION_NOT_FOUND`, `CONVERSATION_CLOSED`, `MESSAGE_TOO_LONG`, `CHAT_RATE_LIMITED`, `NOTIFICATION_NOT_FOUND`; ApiPaths; Redis and storage prefixes; move the image pipeline to `shared.image` (listing tests stay green); `VietnamBounds`; RabbitMQ in `compose.yaml` and the relay switch; identity `UserSummaries`, `MarketingConsent`; listing `ListingLookup.findAll` + cover thumbnail. Schema test for the CHECKs and UKs.

### Phase 1. Domain (pure Java, test-first)

| Class | Responsibility | Key tests (SRS numbers) |
|---|---|---|
| `Conversation` | members, roles, last-message fields, preview (100 chars, "[Hình ảnh]", "[Vị trí]") | preview cut at 100; seller ≠ buyer |
| `ConversationAccess` (policy) | who may open (gate, range, block, own listing, listing state) and who may send (FR-CH-11 R1/R2) | SOLD → send allowed; REMOVED → closed; DELETED → allowed (R1); block either way → closed; suspended member → closed |
| `Participant` | read marker and unread count | marker backwards ignored; reset to 0 on open |
| `MessageContent` (sealed: `Text`, `Images`, `MeetingPoint`, `SystemNote`) | field rules CHT | 1 and 1 000 chars ok, 0 → 400, 1 001 → `MESSAGE_TOO_LONG`; 1–5 images, 6 → 400; point at the box edges in/out |
| `NotificationPreferences` | switches, SYSTEM always on, MARKETING needs consent, quiet hours across midnight | 22:00–07:00: 23:30 and 06:59 quiet, 07:00 not; SYSTEM off → 400 |
| `NotificationCategory`, `NotificationType`, `NotificationCatalogue` | type → category, title, body template, deep link | every R1 type has a working deep link (FR-NT-01) |
| `PushDecision` | send / skip (category off, quiet hours, no token, muted R2) | TRADE off → SKIPPED, row still created |
| `SourceEventId` | name-based UUID (D5) | same input → same id; different event → different id |

### Phase 2. Application + infrastructure

- **Open (UC-CH-01):** existing → return it; else guards (gate → 403 + reasons, range → `OUT_OF_RANGE`, block → `CONTACT_NOT_ALLOWED`, own/state → 409) → insert conversation + 2 participants under the UK (race: catch the unique violation and return the winner) → `ListingCounters.incrementChatCount` (same transaction).
- **Send (UC-CH-02/03/04):** membership (404) → send lock (403) → content rules → rate limit 30/min (`CHAT_RATE_LIMITED` + retry) → insert with dedup (repeat returns the stored message) → update conversation last-message fields and the other member's `unread_count` in one transaction → publish `MessageSent` → after commit deliver to `/user/{recipient}/queue/messages` and acknowledge the sender (`/user/queue/acks` with `clientMsgId`, `id`, `createdAt`).
- **Images:** upload URL (incoming prefix, 10 min), complete → async WebP processing → READY/REJECTED; send IMAGE with up to 5 READY ids of the sender in that conversation; served to members only through 10-minute presigned GETs. Orphan job.
- **Read (UC-CH-05):** set marker (ignore older), recompute unread, notify the sender's sessions (read receipt) and the reader's sessions (badge).
- **Sync (FR-CH-07):** after-id query across the caller's conversations (R4); page of messages by conversation (`before`/`after` id).
- **Lists:** A17 conversations by `last_message_at` with counterpart summary, listing summary (title, status, cover), unread count; A18 header shows whether sending is open and a neutral reason.
- **Notifications (UC-SY-16):** listeners (`MessageSent` when the recipient is offline, `UserRegistered`, `UserSanctioned`, `SanctionLifted`, `AreaExpiring`, `AreaExpired`, `AccountDeleted`) → insert under UK (replay creates nothing) → push decision → `PushGateway` after commit → `push_status`/`pushed_at`; invalid token → delete it. List (cursor), mark read, mark all read, badge counts, preferences (optimistic lock; a user without a row, such as one registered before V15, gets the defaults and the row is created on first save), device upsert. Retention job.
- **Presence:** STOMP `SessionConnectedEvent`/`SessionDisconnectEvent` → Redis set per user with TTL.
- Infrastructure tests on PostGIS/Redis Testcontainers; FCM adapter against a stub HTTP server (success, 404 UNREGISTERED → token deleted, timeout → FAILED, business transaction still committed).

### Phase 3. Public API for other modules

`MessageSent` (event); `ReportableMessages` (CH-W2); `SystemMessagePoster` stays internal until Transaction adds its listeners inside chat. Tests: facade contracts, `ApplicationModules.verify()`.

### Phase 4. Web

| Method and path | Success | FR/UC/screen |
|---|---|---|
| `POST /conversations` {listingId} | 201 new / 200 existing | FR-CH-01, UC-CH-01, A10 → A18 |
| `GET /conversations?cursor=` | 200 page | FR-CH-06, UC-CH-05, A17 |
| `GET /conversations/{id}` | 200 header (listing, counterpart, `canSend`, reason) | A18 |
| `GET /conversations/{id}/messages?before=&after=` | 200 | A18, FR-CH-07 |
| `POST /conversations/{id}/messages` | 201 (200 on repeat) | FR-CH-02 A1 (REST fallback), UC-CH-02..04 |
| `PUT /conversations/{id}/read` {lastReadMessageId} | 204 | FR-CH-06 |
| `GET /conversations/sync?afterMessageId=` | 200 | FR-CH-07 |
| `POST /chat-images/upload-url`, `POST /chat-images/{id}/complete`, `GET /chat-images/{id}` | 200 / 202 / 200 | FR-CH-04, UC-SY-10 |
| `GET /notifications?cursor=` | 200 | FR-NT-05, A29 |
| `POST /notifications/{id}/read`, `POST /notifications/read-all` | 204 | FR-NT-05 |
| `GET /users/me/badges` | 200 {chatUnread, notificationUnread} | FR-NT-06 (path follows `CURRENT_USER_BASE`, v2.1) |
| `GET /users/me/notification-preferences`, `PUT …` (with `version`) | 200 | FR-NT-03, A30 (path follows `CURRENT_USER_BASE`, v2.1) |
| `PUT /devices/{deviceId}`, `DELETE /devices/{deviceId}` | 200 / 204 | FR-NT-02, ID-W1 |
| STOMP `SEND /app/conversations/{id}/messages`, `SEND /app/conversations/{id}/read` | ack / receipt | FR-CH-02, FR-CH-06 |
| STOMP subscriptions `/user/queue/messages`, `/acks`, `/read-receipts`, `/badges`, `/notifications`, `/errors` | — | FR-CH-02, FR-CH-06, FR-NT-06 |

STOMP errors go to `/user/queue/errors` as the same RFC 9457 body plus `clientMsgId`. Integration tests: REST status codes and bodies; a STOMP client test (send, ack, delivery, error frame, expired token on CONNECT, UC-CH-02 E6); the two-replica relay test (D2).

### Phase 5. Contract

`docs/api/chat-api.md` (flows: open from A10, chat A18 with socket + REST fallback + reconnect sync, images, meeting point, conversation list A17, notifications A29, settings A30, device registration after login; a "Waits for other modules" section: CH-W1..W4, NT-W1..W5), regenerated `openapi.yml`, `docs/postman/nearby-chat.postman_collection.json`, a STOMP frame reference for the Android team, README and `docs/prompts` links.

### Phase 6. Definition of done

Checklist 07; `./gradlew build` green with Docker; FR codes in test names; status file (FR status, decisions, register rows closed and opened), roadmap, decision log; report with the remaining gaps (CH-W*, NT-W*, R2 items FR-CH-09/10, X-W2, X-W3).

## Order of work (small reviewable steps, each green)

1. Phase 0: V15, params, codes, `shared.image` move, relay switch + RabbitMQ in compose, upstream facades.
2. Domain: conversation access, content rules, read markers, preferences, catalogue, source ids.
3. Open + send + read + sync + lists (REST) with dedup, rate limit and send lock.
4. STOMP delivery, presence, relay test with two instances.
5. Images (upload, processing, members-only URLs, orphan job) and meeting point.
6. Notifications: listeners, dedup, push gateway, preferences, list, badges, devices, retention job.
7. Web + contract + Postman; status file and register final pass.

## Acceptance traceability (Phase 6, 2026-11-24)

Every R1 acceptance criterion of the SRS and the test that proves it. Test classes are under `src/test/java/.../chat/`.

| FR | Acceptance criterion (SRS) | Test |
|---|---|---|
| FR-CH-01 | Opening twice returns the same conversation id | `ChatMessagingIntegrationTests.Opening.openingTwiceGivesTheSameConversation`, `ChatApiIntegrationTests.Opening.opensOnceAndReturnsTheSameConversation`, `ChatRepositoriesIntegrationTests.Opening` |
| FR-CH-02 | With two replicas, sender on 1 reaches recipient on 2; the same `client_msg_id` stores one message | `ChatRelayIntegrationTests.aSocketSendIsDeliveredAndAcknowledged`, `aMessageCrossesReplicas`, `aRepeatedSocketSendIsAcknowledgedAgain`; `ChatRepositoriesIntegrationTests.Sending.theSameClientMessageIdIsStoredOnce` |
| FR-CH-03 | The 31st message in a minute is refused | `ChatMessagingIntegrationTests.Sending.theThirtyFirstMessageIsRateLimited`, `ChatApiIntegrationTests.Messages.rateLimit`; 1 000 / 1 001 characters: `MessageContentTests.TextRules`, `ChatApiIntegrationTests.Messages.textLimits` |
| FR-CH-04 | Only the two members see an image, and it has no EXIF | `ChatImageIntegrationTests.Visibility`, `ChatImageIntegrationTests.Processing.theDerivativesHaveNoExif`, `ChatMessagingIntegrationTests.Images` |
| FR-CH-05 | The point never appears in a public API (BR-05) | `ChatApiIntegrationTests.Messages.meetingPoints`, `aStrangerGetsNotFound`; the point is stored only in `messages.payload`, read only through member-checked endpoints |
| FR-CH-06 | Opening a conversation sets the unread count to 0 on every device of the reader | `ChatMessagingIntegrationTests.ReadingAndSyncing.readingClearsTheUnreadCount`, `ChatRelayIntegrationTests.aSocketReadSendsAReceiptAndTheBadge`; an older marker: `ParticipantTests`, `ReadServiceTests` |
| FR-CH-07 | After a 5-minute loss nothing is missing or repeated | `ChatMessagingIntegrationTests.ReadingAndSyncing.syncReturnsTheMissedMessages`, `ChatRepositoriesIntegrationTests.Syncing.missedMessagesAcrossConversations`, `ChatApiIntegrationTests.ReadingListingAndSyncing.sync` |
| FR-CH-08 | One system message per event | **Hook only (CH-W1).** `ChatModuleApiIntegrationTests.SystemMessages.aReplayedEventPostsOnce` |
| FR-CH-11 | After SOLD both sides can still write; after REMOVED not | `ChatMessagingIntegrationTests.Locking.soldStaysOpenRemovedCloses`, `ChatApiIntegrationTests.Messages.aRemovedListingClosesSending`, `ConversationAccessTests.Sending` |
| FR-CH-12 | The report refers to the message and shows in W03 | **Hook only (CH-W2).** `ChatModuleApiIntegrationTests.Reporting` |
| FR-NT-01 | Every event type creates a notification with a working deep link | `NotificationTypeTests.everyTypeRenders` (every catalogue type), `NotificationFlowIntegrationTests.Events` (the producers that exist). Trade, payment, review and moderation types wait for their modules (NT-W1..W4) |
| FR-NT-02 | With FCM unreachable every business flow still succeeds | `PushDispatcherTests` (failure, throwing gateway, database failure), `FcmPushGatewayTests` (timeout, unreachable, 5xx); the push runs after commit in the listener thread, never inside the business transaction |
| FR-NT-03 | TRADE off: no push, still in app; quiet hours block the push | `NotificationServiceTests`, `PushDecisionTests`, `NotificationPreferencesTests.QuietHours` |
| FR-NT-04 | Replaying an event from the outbox creates no second notification | `NotificationFlowIntegrationTests.Events.aReplayedEventIsIgnored`, `NotificationRepositoriesIntegrationTests.Dedup`, `SourceEventIdTests` |
| FR-NT-05 | Mark all read sets the count to 0 | `NotificationFlowIntegrationTests.Listing.readingChangesTheBadge`, `NotificationApiIntegrationTests.Listing.readStateAndBadges` |
| FR-NT-06 | The badge updates after reading on another device | `ChatRelayIntegrationTests.aSocketReadSendsAReceiptAndTheBadge` (push to every session), `NotificationApiIntegrationTests` (REST count) |

## Not done and why (end of R1)

- FR-CH-08 producers, FR-CH-12 report, FR-MD-09 transcripts and the TRADE, payment, review and moderation notification types: wait for Transaction, Payment, Reputation and Moderation (register rows CH-W1..W5, NT-W1..W5).
- FR-CH-09, FR-CH-10, KEYWORD_ALERT: R2.
- NT-W5: the push runs in the single application, not in a `worker` role (platform task X-W2).
- A real FCM push to a phone, two `bootRun` instances side by side, the Postman collection run and the production compose on a real server were not run by hand (see `docs/setup-local.md`, section 9).
- No load test of the chat was run.

## Review of 2026-11-24: gaps found against the SOT and closed

A second pass over the SRS (ch4 retention table, ch6 monitoring, FR-ID-14) found three things the first pass missed. All three were built and tested the same day.

| Gap | SRS | Fix | Test |
|---|---|---|---|
| Both accounts deleted: the conversation, its messages, locations and image files must be deleted ("Giữ khi một trong hai tài khoản còn"; "xoá khi cả hai tài khoản bị xoá") | `ch4_tail.tex` retention rows "Tin nhắn" and "Ảnh và vị trí trong chat" | `ConversationRetention` listener on `AccountDeleted`; files deleted first, rows cascade | `ConversationRetentionTests`, `ChatRepositoriesIntegrationTests.Deleting` |
| Monitoring metrics: WebSocket connections per replica, push failures | `ch6.tex` "Metric giám sát" | Gauge `nearby.websocket.sessions`, counter `nearby.push.notifications{result}` | `ChatRelayIntegrationTests.presenceIsSharedBetweenReplicas`, `PushDispatcherTests` |
| Unblocking removes every effect (FR-ID-14 acceptance) | `ch3_features.tex` FR-ID-14 | Already true because the send lock is computed live; now proven | `ChatMessagingIntegrationTests.Locking.unblockingReopensIt` |

Still not done and outside this module: the demo-data command (ch6 "một lệnh riêng nạp dữ liệu demo"), the alert thresholds (TBD in the SRS) and the `worker` runtime role (X-W2).
