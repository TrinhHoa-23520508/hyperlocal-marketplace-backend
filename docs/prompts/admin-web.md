# Prompt: design and build the Nearby Admin Console (web)

Read [`README.md`](README.md) first for the context to attach and the workflow. Copy everything below the line into your AI coding tool, together with the business flows F13–F14 of [`docs/api/identity-api.md`](../api/identity-api.md#business-flows-per-screen) and [`docs/api/openapi.yml`](../api/openapi.yml). It describes only the admin features the backend already serves; do not invent other screens or endpoints.

---

## Role and goal

You are a senior frontend engineer and product designer. Design and build **Nearby Admin Console**, the internal web app that administrators of *Nearby* (a hyperlocal second-hand marketplace for Vietnam) use to find user accounts and to sanction them: suspend, ban and lift sanctions. The backend is a Spring Boot REST API that already exists; your job is the web client only.

Deliver a working, tested app, not a mock-up. The UI language is **Vietnamese**; code, identifiers and comments are English.

## Tech stack (fixed)

- React 18, TypeScript (strict), Vite.
- React Router v6 (data routers), TanStack Query v5 for server state.
- shadcn/ui on Tailwind CSS, `lucide-react` icons.
- React Hook Form + Zod for forms.
- `date-fns` with the `vi` locale; show times in `Asia/Ho_Chi_Minh`.
- Vitest + React Testing Library + MSW for tests; ESLint + Prettier.
- No other UI kit and no global state library: auth state lives in a React context, server state in TanStack Query.

## Backend facts you must respect

The flows F13 (login) and F14 (search, User 360, sanctions) in the attached API contract are the source of truth for call order and error handling; each table row is an acceptance test. Types come from the attached `openapi.yml`.

- Base URL comes from `VITE_API_BASE_URL` (for example `http://localhost:8080`). Every path below is under `/api/v1`.
- **CORS**: the backend allows the origins listed in its `CORS_ALLOWED_ORIGINS`; the default is `http://localhost:5173` (the Vite dev server), so calling `VITE_API_BASE_URL` directly works in development. Do not send cookies (`credentials: 'omit'`); tokens go in the `Authorization` header. The backend exposes the `Retry-After` header to the browser. For production, either serve the built console from the API's domain (no CORS needed) or ask for its origin to be added to `CORS_ALLOWED_ORIGINS`; state this in the README.
- JSON everywhere, timestamps ISO-8601 UTC.
- Errors are RFC 9457 problem details with a stable `code`:

```json
{ "type": "https://nearby.uit.edu.vn/problems/account-status-conflict", "title": "Conflict",
  "status": 409, "detail": "An account that is BANNED cannot become SUSPENDED.", "code": "ACCOUNT_STATUS_CONFLICT" }
```

  Validation errors (`400 VALIDATION_FAILED`) add `errors: [{ field, message }]`. Rate limits answer `429` with a `Retry-After` header in seconds. Always branch on `code`, never on `detail`.

### Authentication

| Call | Details |
|---|---|
| `POST /auth/login` | Body `{ "identifier": "<admin email>", "password": "..." }`. `200` → `{ tokenType, accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt }` |
| `POST /auth/refresh` | Body `{ "refreshToken": "..." }`. `200` → same body. The old refresh token is revoked; always store the new one |
| `POST /auth/logout` | Body `{ "refreshToken": "..." }`. Always `204` |

- The access token is an HS256 JWT that lives 15 minutes. Decode its payload (no verification needed client-side) and read the `roles` claim: only `ADMIN` may enter the console. A `USER` who logs in gets a clear "Tài khoản này không có quyền quản trị" message, and the app calls logout for them right away.
- Keep the access token **in memory only**. Keep the refresh token in `sessionStorage` (cleared when the tab closes). Never put tokens in URLs or logs.
- Refresh proactively about 60 s before `accessTokenExpiresAt`, and reactively once on a `401`, then retry the original request. Allow only **one** refresh in flight; queue the other requests behind it. Refresh tokens rotate, so two parallel refreshes would trip reuse detection.
- `401 REFRESH_TOKEN_REUSED` or `401 INVALID_REFRESH_TOKEN` on refresh: clear everything and send the admin to login with "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại".

Login errors:

| Status | Code | Show |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | "Email hoặc mật khẩu không đúng" |
| 429 | `LOGIN_LOCKED` | "Đăng nhập sai quá nhiều lần. Thử lại sau N phút" (N from `Retry-After`) |
| 403 | `ACCOUNT_SUSPENDED` / `ACCOUNT_BANNED` | Account restricted, with `reason` and `suspendedUntil` when present |

Two-factor login with TOTP (SRS FR-AD-01) is **not** built on the backend yet. Leave a clearly marked extension point in the login flow and do not fake it.

### Admin endpoints (`ADMIN` token required)

A non-admin token gets `403 ACCESS_DENIED`.

**Account object** returned by every admin endpoint:

```json
{
  "id": "1b0c6f1e-...",
  "phone": "+84912345678",
  "email": "an.nguyen@example.com",
  "displayName": "An Nguyen",
  "role": "USER",
  "status": "SUSPENDED",
  "suspendedUntil": "2026-10-25T03:00:00Z",
  "deletionRequestedAt": null,
  "phoneVerified": true,
  "emailVerified": true,
  "createdAt": "2026-10-18T03:00:00Z",
  "updatedAt": "2026-10-18T05:00:00Z"
}
```

- `phone`, `email`, `suspendedUntil` and `deletionRequestedAt` may be absent.
- `role` is `USER` or `ADMIN`.
- `status` is one of `ACTIVE`, `SUSPENDED`, `BANNED`, `PENDING_DELETION`, `DELETED`.

| # | Call | Notes |
|---|---|---|
| 1 | `GET /admin/users?query=&status=&cursor=&limit=` | Search. `query`: case-insensitive substring of phone, email or display name. `status`: one of the five statuses. `limit`: default 20, max 100. Newest first. Response `{ items: Account[], nextCursor: string \| null, hasMore: boolean }`; pass `nextCursor` back as `cursor`. `400 INVALID_CURSOR` for a bad cursor |
| 2 | `GET /admin/users/{userId}` | One account. `404 USER_NOT_FOUND` |
| 3 | `POST /admin/users/{userId}/suspend` | Body `{ "days": 1 \| 7 \| 30, "reason": "..." }` (reason required, 1-200 characters). `200` with the updated account (`SUSPENDED`, `suspendedUntil`). Ends all the user's sessions at once. The account returns to `ACTIVE` by itself when the suspension ends |
| 4 | `POST /admin/users/{userId}/lock` | Permanent ban. Body `{ "reason": "..." }`. Allowed from `ACTIVE` or `SUSPENDED`. `200` with the account (`BANNED`) |
| 5 | `POST /admin/users/{userId}/unlock` | Lifts a suspension or a ban. Body `{ "reason": "..." }`. `200` with the account (`ACTIVE`) |

Sanction errors:

| Status | Code | Meaning, and the Vietnamese copy to show |
|---|---|---|
| 400 | `INVALID_SUSPENSION_PERIOD` | `days` not 1, 7 or 30: "Thời hạn tạm khoá chỉ có thể là 1, 7 hoặc 30 ngày" |
| 400 | `VALIDATION_FAILED` | Show `errors[].message` under the matching field |
| 403 | `CANNOT_LOCK_OWN_ACCOUNT` | "Bạn không thể xử phạt chính tài khoản của mình" |
| 403 | `CANNOT_LOCK_ADMIN` | "Không thể xử phạt tài khoản quản trị viên" |
| 404 | `USER_NOT_FOUND` | "Không tìm thấy tài khoản" |
| 409 | `ACCOUNT_STATUS_CONFLICT` | The status changed or the action does not apply: "Trạng thái tài khoản đã thay đổi", then refetch the account |
| 409 | `CONCURRENT_MODIFICATION` | Another admin changed it at the same moment: same handling as above |

Every sanction is written to an audit log on the server with the admin's id and the reason. There is no endpoint yet to read the audit log, so do not build an audit screen; the reason field is mandatory precisely because it is audited.

## Screens (SRS screen codes)

### W01 - Đăng nhập quản trị (`/login`)

- Centered card: product name "Nearby Admin", email and password fields, a show/hide password toggle and a "Đăng nhập" button with a loading state.
- Zod validation: valid email; password required.
- Redirect to the originally requested route after login.

### Shell

- Left sidebar, collapsible to icons at `lg` and down, a drawer on mobile. One nav item for now: "Người dùng" (`/users`).
- Top bar: the admin's email (from the login form), a light/dark theme toggle and "Đăng xuất".
- Every route except `/login` is guarded: no session leads to `/login`; a session without `ADMIN` leads to a 403 page.

### W05 - Tìm người dùng (`/users`)

- Search box (debounced 300 ms) with the hint "Số điện thoại, email hoặc tên hiển thị", plus a status filter with all six options (Tất cả, Hoạt động, Tạm khoá, Bị cấm, Chờ xoá, Đã xoá).
- Keep both filters in the URL query string so the view can be shared and survives a reload.
- Table columns:
  - Tên hiển thị (with role badge `ADMIN` when relevant)
  - Số điện thoại (format `+84 912 345 678`, plus a "đã xác minh" check)
  - Email (with a verified check)
  - Trạng thái (status badge)
  - Ngày tạo (relative time, exact time in a tooltip)
- A row click opens the user detail.
- Cursor pagination with a "Tải thêm" button, using `useInfiniteQuery`. Do not show page numbers, because the API has none.
- States:
  - Skeleton rows while loading.
  - Empty state ("Không có tài khoản phù hợp") with a clear-filters button.
  - Error state with a retry button.

Status badges must use colour **and** text, never colour alone:

| Status | Label | Colour |
|---|---|---|
| `ACTIVE` | Hoạt động | green |
| `SUSPENDED` | Tạm khoá đến <date> | amber |
| `BANNED` | Bị cấm | red |
| `PENDING_DELETION` | Chờ xoá | slate |
| `DELETED` | Đã xoá | muted grey |

### W05 - Chi tiết người dùng / User 360 (`/users/:userId`)

- Header: display name, status badge, role badge, user id with a copy button.
- "Thông tin tài khoản" card with these fields:
  - phone, email (each with its verified state);
  - created and updated times;
  - `suspendedUntil`, shown as both remaining time and exact time;
  - `deletionRequestedAt`, with the note "Tài khoản sẽ bị ẩn danh sau 7 ngày nếu người dùng không đăng nhập lại".
- "Xử phạt" action area (W06), see below. Hide it entirely for `ADMIN` accounts and for the admin's own account, with an explanatory note.
- `404 USER_NOT_FOUND` renders a not-found state with a link back to the list.

### W06 - Xử phạt (dialogs on the User 360 page)

Which actions to show:

| Current status | Actions |
|---|---|
| `ACTIVE` | "Tạm khoá", "Cấm vĩnh viễn" |
| `SUSPENDED` | "Cấm vĩnh viễn", "Gỡ tạm khoá" |
| `BANNED` | "Gỡ cấm" |
| `PENDING_DELETION`, `DELETED` | None, with an explanatory note |

Each action opens a dialog:

- **Tạm khoá**: a segmented control with 1, 7 or 30 ngày (default 7) and a live preview "Hết hạn lúc …". Required reason textarea with a 200-character counter.
- **Cấm vĩnh viễn**: a destructive-styled dialog with a reason field. It also requires typing the user's display name to confirm. Warning copy: "Người dùng sẽ bị đăng xuất khỏi mọi thiết bị ngay lập tức".
- **Gỡ tạm khoá / Gỡ cấm**: a reason field (for example "Kháng nghị được chấp nhận").

Rules for all three dialogs:

- Disable the submit button while the request is pending.
- On success, show a toast, update the account in the TanStack Query cache from the response, and invalidate the list queries.
- On error, map `code` to the copy in the table above. Keep the dialog open for validation errors; close it and refetch for `409`.

## Design direction

- Calm, dense, data-first internal tool. Neutral palette with one accent colour. 14 px base text, 8 px spacing grid, tables with comfortable 44 px rows.
- Light and dark themes through shadcn CSS variables. Respect `prefers-color-scheme`, and let the admin override it (store the choice in `localStorage`).
- Responsive from 360 px wide: below `md` the table becomes stacked cards.
- Accessibility, WCAG 2.2 AA:
  - Every control reachable by keyboard, with a visible focus ring.
  - Dialogs trap focus and return it to the trigger.
  - Status communicated by text, not colour alone.
  - Form errors linked with `aria-describedby`.
  - Toasts announced politely.
- All copy in Vietnamese with proper diacritics. Put strings in one `src/i18n/vi.ts` module so they can be translated later.

## Project structure

```
src/
  api/            http client (fetch wrapper, problem-detail parsing, refresh queue), endpoint functions;
                  schema.ts generated with `npx openapi-typescript docs/api/openapi.yml -o src/api/schema.ts` (never edit by hand)
  auth/           AuthProvider, useAuth, route guards, JWT payload decoding
  features/users/ list page, detail page, sanction dialogs, status badge, hooks (useUsers, useUser, useSanction)
  components/     shared UI (layout shell, empty/error states, data table)
  i18n/vi.ts
  lib/            formatting helpers (phone, dates)
  test/           MSW handlers and fixtures
```

`ApiError` carries `status`, `code`, `detail`, `fieldErrors` and `retryAfterSeconds`. Every request goes through one client that adds the bearer token, parses problem details, and runs the single-flight refresh.

Environment: `.env.example` with `VITE_API_BASE_URL=http://localhost:8080`. Run the dev server on port 5173 (`strictPort: true`) so it matches the backend's allowed origin.

## Tests (required)

- Unit tests:
  - JWT role check.
  - Phone formatting.
  - Mapping each error `code` to its message.
  - Action availability per status (the table above), as a table-driven test.
- Component and integration tests with MSW:
  1. Login success, wrong password, `LOGIN_LOCKED` with `Retry-After`, and a non-admin account rejected.
  2. Expired access token: exactly one refresh for three parallel requests, then the requests succeed. A reused refresh token ends the session.
  3. Search with query and status updates the URL and loads the next page with the cursor.
  4. Suspend for 7 days: the request body is `{ days: 7, reason }`, and the badge shows the end date.
  5. Ban requires the typed name, and `409 ACCOUNT_STATUS_CONFLICT` refetches the account.
  6. No sanction actions are shown for admin accounts or for `PENDING_DELETION`.
- `npm run lint`, `npm run test` and `npm run build` must all pass.

## Acceptance criteria

- An admin can log in with the bootstrap account (`ADMIN_EMAIL` / `ADMIN_PASSWORD` from the backend `.env`), search users by phone, email or name, filter by status, and page through results.
- An admin can suspend for 1, 7 or 30 days, ban, and lift a sanction, always with a reason. The UI reflects the new status without a full reload.
- A suspended or banned user's sessions die server-side. The console does not need to do anything for this, but its copy says so.
- No token ever appears in the URL, the console or `localStorage`.
- Keyboard-only use works end to end, and both themes pass AA contrast.
- The README explains setup, the CORS origin the backend must allow, the production deployment options and how to run the tests.

## Out of scope (backend not ready)

TOTP login (FR-AD-01), fine-grained roles (FR-AD-02), audit log viewer (FR-AD-03), dashboard and statistics (FR-AD-04 / W02), reports and moderation queues (W03, W04), disputes, payments, categories and system parameters. Do not add placeholder pages for them.
