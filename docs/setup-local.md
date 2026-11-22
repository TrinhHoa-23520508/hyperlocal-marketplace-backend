# Local development setup

This guide takes you from a fresh clone to a running backend that the Android app can call.

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 25 | The Gradle toolchain picks up JDK 25 (IntelliJ: *File → Project Structure → SDK*). |
| Docker Desktop | 4.x or later | Must be **running** before you start the app or run tests. |
| Git | any | |
| IntelliJ IDEA | recommended | Enable *Annotation Processing* for Lombok. |

You do not need to install PostgreSQL, Redis or MinIO. They all run in Docker.

## 2. Environment variables

```sh
cp .env.example .env
```

Both Docker Compose (`compose.yaml`) and Spring Boot (`spring.config.import`) read `.env`, so you configure everything in one place. `.env` is gitignored, so **never commit it**. When you add a new variable, add it to `.env.example` too.

Key variables:

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_PORT` | `5433` | Host port of the PostGIS container. It is not `5432`, to avoid clashing with a PostgreSQL installed directly on the machine. |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `nearby` | Database credentials inside the container. |
| `REDIS_PORT` | `6379` | Redis (cache, rate limiting, idempotency). |
| `JWT_SECRET` | dev value | HS256 signing key, at least 32 bytes. Generate one with `openssl rand -base64 48`. |
| `MINIO_ENDPOINT` | `http://localhost:9000` | Address the backend uses to reach MinIO. |
| `MINIO_PUBLIC_ENDPOINT` | `http://10.0.2.2:9000` | Host the Android app uses for presigned image URLs (see section 6). |
| `SERVER_PORT` | `8080` | API port. |
| `SMS_PROVIDER` | `log` | `log` writes the one-time code to the backend log (search for `[sms disabled]`); `esms` sends it through eSMS.vn (section 2.1). |
| `ESMS_API_KEY` / `ESMS_SECRET_KEY` / `ESMS_BRANDNAME` | empty | eSMS credentials and the registered brandname. Required when `SMS_PROVIDER=esms`; the app refuses to start without them. |
| `ESMS_SANDBOX` | `true` | `true`: eSMS checks the request but neither delivers nor charges. Set `false` to send real SMS. |
| `SMS_OTP_TEMPLATE` | Vietnamese text with `{code}` | SMS body. Must match the template registered with eSMS character for character. |
| `OTP_HASH_SECRET` | dev value | HMAC key for code hashes stored in Redis, at least 32 bytes. |
| `MAIL_ENABLED` | `false` | When `false`, verification and password reset links are written to the backend log (search for `[mail disabled]`) instead of being emailed. |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail / `587` / empty | SMTP server and login (section 2.2). |
| `MAIL_SMTP_AUTH` / `MAIL_STARTTLS` | `true` | Required by Gmail and most providers. |
| `MAIL_FROM` | `Nearby <no-reply@nearby.local>` | Sender. With Gmail use the same address as `MAIL_USERNAME`. |
| `EMAIL_VERIFICATION_URL` / `PASSWORD_RESET_URL` | `nearby://...` | Links in account emails. The token is appended as `?token=...`. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@nearby.local` / `Nearby2026` | First admin account, created on startup if no account uses the email yet. |

OTP limits (`otp.ttl_s`, `otp.max_attempts`, `otp.resend_s`, `otp.max_per_day`) and policy versions (`consent.terms_version`, `consent.privacy_version`) are runtime parameters in the `app_parameters` table, seeded by Flyway, not environment variables.

Real OS environment variables, and IntelliJ *Run Configuration* variables, always override values in `.env`.

### 2.1 Real SMS codes with eSMS.vn

Vietnamese carriers only deliver application SMS from a **registered brandname**, so every provider needs one.

1. Create an account at https://account.esms.vn and open **Quản lý API** to copy the `ApiKey` and `SecretKey`.
2. Get a brandname: register your own (for example `NEARBY`, needs legal documents and takes days to weeks), or ask eSMS support for their shared test brandname for developers.
3. Register a **customer care (CSKH) template** for that brandname, for example `{code} la ma xac minh Nearby cua ban, het han sau 5 phut. Khong chia se ma nay.` Put the same text in `SMS_OTP_TEMPLATE`. A mismatch makes eSMS answer code `146`.
4. Top up the account balance.
5. Set `SMS_PROVIDER=esms`, the three credentials and keep `ESMS_SANDBOX=true`. Call `POST /api/v1/auth/register/otp`; the log line `SMS to *********678 accepted by eSMS` means the key, brandname and template are right.
6. Set `ESMS_SANDBOX=false` and send to your own phone.

Common eSMS codes: `100` accepted, `101` wrong key, `104` brandname not found, `146` template mismatch. Any failure is answered `503 SMS_UNAVAILABLE` and does not use up the daily quota.

### 2.2 Real emails with Gmail

1. Turn on **2-Step Verification** for the Google account that sends the mail.
2. Open https://myaccount.google.com/apppasswords, create an app password named `Nearby backend` and copy the 16 characters.
3. In `.env`: `MAIL_ENABLED=true`, `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME=<address>@gmail.com`, `MAIL_PASSWORD=<app password>`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`, `MAIL_FROM=Nearby <<address>@gmail.com>`.
4. Gmail allows about 500 emails per day; the backend also limits each address to 5 account emails per hour.

## 3. Run the app

### Option 1: IntelliJ

Open `NearbyBackendApplication` and click *Run*. On first start, `spring-boot-docker-compose` runs `docker compose up` for PostGIS, Redis and MinIO.

### Option 2: Terminal

```sh
./gradlew bootRun          # Windows: gradlew.bat bootRun
```

On first start, Flyway creates the schema (22 tables plus the outbox) and seeds the reference data.

### Check that the app is running

| URL | Expected result |
|---|---|
| http://localhost:8080/actuator/health | `{"status":"UP"}` |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/api/test | `{"status":"ok", ...}` |
| http://localhost:9001 | MinIO Console. Log in with `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`. |

To browse the database with DBeaver or DataGrip, use host `localhost`, port `5433`, and `nearby` as the database, user and password.

## 4. Run the tests

```sh
./gradlew build            # tests + format check + module boundaries + coverage
./gradlew test             # tests only
./gradlew test --tests '*TokenServiceTests'
./gradlew format           # format code before committing
```

- Integration tests use Testcontainers with real PostGIS and Redis, so Docker must be running. Tests do not use `.env` or the `compose.yaml` containers.
- The coverage report is at `build/reports/jacoco/test/html/index.html`. The build fails if overall line coverage is under 60%, or under 80% for `domain` / `application` packages.
- The module report is at `build/spring-modulith-docs/`.
- Unformatted code fails the build. Run `./gradlew format` and build again.

## 5. Code structure

```
src/main/java/vn/edu/uit/nearby/nearby_backend/
├── identity/          Identity + Admin
├── listing/           Listing + Search
├── transaction/       Transaction
├── areaverification/  Area Verification
├── reputation/        Reputation
├── chat/              Chat + Notify
└── shared/            Shared kernel: constant, error, security, storage, websocket, ratelimit, geo, web
```

- Each module is split into `web` / `application` / `domain` / `infrastructure` layers. Only the root package is public; it holds Facade interfaces and domain events.
- `ModularityTests` fails the build when a module reaches into another module's internals.
- Coding rules live in `.claude/clean-code-skill/`.

### Constants

Shared constants live in `shared/constant/`, one class per concern, so every name says what it is:

| Class | Holds |
|---|---|
| `HeaderNames` | Custom HTTP headers, for example `IDEMPOTENCY_KEY` |
| `ApiPaths` | URL paths and security matcher patterns |
| `WebSocketDestinations` | STOMP destination prefixes |
| `SecurityConstants` | Bearer scheme, JWT claim names, authority prefix |
| `Roles` | `USER`, `ADMIN` |
| `ErrorCodes` | Problem-detail `code` values and the problem type base URI |
| `RedisKeyPrefixes` | Redis key namespaces |
| `ApiLimits` | Page sizes and header length limits |
| `GeoConstants` | `WGS84_SRID` |

Rules:

- Standard HTTP headers come from Spring's `org.springframework.http.HttpHeaders` (`AUTHORIZATION`, `CONTENT_TYPE`...). Do not redefine them.
- Add a new constant to the class of its concern, or create a new concern class. Never use a vague name such as `HEADER` or `PREFIX`.
- A value that is an internal detail of one class stays `private` in that class. Examples are the cursor encoding separator and a request attribute name.
- Values that may change at runtime belong in `app_parameters`, not in code.

## 6. Calling the API from the Android app

| Device | Base URL | `MINIO_PUBLIC_ENDPOINT` |
|---|---|---|
| Android Emulator | `http://10.0.2.2:8080` | `http://10.0.2.2:9000` |
| Physical phone on the same Wi-Fi | `http://<machine-LAN-IP>:8080` | `http://<machine-LAN-IP>:9000` |

- Inside the emulator, `localhost` is the emulator itself, not your computer.
- Android blocks plain HTTP by default. For debug builds, add a `network_security_config` that allows cleartext traffic to `10.0.2.2` or your LAN IP.
- Presigned URLs are signed for a specific host, so `MINIO_PUBLIC_ENDPOINT` must be exactly the host the phone calls. Change it in `.env` and restart the app.
- API contracts are in [docs/api/](api/). To try the account flow, import `docs/postman/nearby-identity.postman_collection.json` and `nearby-local.postman_environment.json` into Postman, then run the folders in order.
- Every API except `/api/v1/auth/**` needs `Authorization: Bearer <access-token>`. Errors follow RFC 9457 (`application/problem+json`) and include a `code` field.
- WebSocket: STOMP at `ws://<host>:8080/ws`. Send `Authorization: Bearer <token>` in the CONNECT frame.

## 6.1 Calling the API from a browser (CORS)

The admin console runs on another origin (Vite dev server `http://localhost:5173`), so the browser needs CORS. `CORS_ALLOWED_ORIGINS` (comma-separated) lists the allowed origins; the default allows `http://localhost:5173`. Tokens travel in the `Authorization` header, so credentials (cookies) stay off. The Android app is not a browser and needs no entry. Leave the variable empty to turn CORS off.

## 6.2 Monitoring (Prometheus + Grafana)

Off by default. Start the backend first (IDE or `./gradlew bootRun`), then:

```sh
docker compose --profile monitoring up -d prometheus grafana
```

| Tool | URL | Notes |
|---|---|---|
| Prometheus | http://localhost:9090 | Scrapes `host.docker.internal:8080/actuator/prometheus` every 15 s. *Status > Targets* must show `nearby-backend` as UP. Alert rules are under *Alerts* |
| Grafana | http://localhost:3000 | Login `admin` / `admin` (`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`). Dashboard *Nearby > Nearby backend* is provisioned automatically |

The configuration is shared with production: `deploy/prometheus/alerts.yml` (rules), `deploy/grafana/provisioning` (data source, dashboard provider) and `deploy/grafana/dashboards/nearby-backend.json`. Edit the JSON file, not the dashboard in the UI (UI changes are not saved). Stop with `docker compose --profile monitoring stop prometheus grafana`.

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `password authentication failed for user "nearby"` | The app connected to a PostgreSQL installed on the machine (port 5432) instead of the container. | Keep `POSTGRES_PORT=5433` in `.env` and run `docker compose up -d` to recreate the container. `docker compose ps` should show `0.0.0.0:5433->5432`. |
| `Port 8080 was already in use` | Another instance is running, usually from IntelliJ. | Stop it, or set `SERVER_PORT=8081` in `.env`. |
| `Could not find a valid Docker environment` during tests | Docker Desktop is not running. | Start Docker Desktop and wait until it shows *Running*. |
| `Image storage not reachable` (WARN) | MinIO is not running. The app still starts. | `docker compose up -d minio` |
| Flyway `checksum mismatch` | A migration that already ran was edited. | Never edit an applied migration; add a new `V9__...sql` instead. To reset local dev data, run `docker compose down -v`, which deletes all local data. |
| `checkFormatMain FAILED` | Code is not formatted. | `./gradlew format` |
| Browser shows a CORS error | The page's origin is not in `CORS_ALLOWED_ORIGINS`. | Add the exact origin (scheme, host and port) and restart the app. |
| Prometheus target `nearby-backend` is DOWN | The backend is not running on port 8080 of the host. | Start the app; on Linux Docker Engine check that `host-gateway` is supported (Docker 20.10+). |

## 8. Useful Docker commands

```sh
docker compose ps                 # container status
docker compose logs -f postgres   # follow logs
docker compose down               # stop, keep data
docker compose down -v            # stop and DELETE local data
```

For production deployment, see [deploy/README.md](../deploy/README.md).
