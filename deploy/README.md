# Deployment

One VM running Docker Compose (Architecture Design §2): Nginx → Spring Boot → PostgreSQL/PostGIS, Redis, MinIO, plus Prometheus + Grafana.

## First deploy

```sh
git clone <repo> /opt/nearby && cd /opt/nearby/deploy
cp .env.example .env          # fill in secrets, domains and APP_IMAGE
docker compose up -d
```

Point two DNS records at the VM: `API_DOMAIN` (REST, WebSocket `/ws`, Grafana `/grafana/`) and `STORAGE_DOMAIN` (MinIO, used for presigned image URLs).

## TLS (Let's Encrypt)

```sh
docker compose run --rm --entrypoint certbot certbot certonly --webroot -w /var/www/certbot \
  -d "$API_DOMAIN" -d "$STORAGE_DOMAIN" --email you@example.com --agree-tos
```

Certbot names the certificate after the first `-d` domain. Either issue one certificate per domain (run the command twice) or point both `ssl_certificate` paths in the HTTPS template at the shared one.

```sh
mv nginx/templates/https.conf.template.example nginx/templates/https.conf.template
docker compose restart nginx
docker compose --profile tls up -d certbot   # auto-renewal loop
```

## What Nginx does here

Nginx is the only container with public ports (80/443); everything else is on the internal Docker network.

- **TLS termination**: HTTPS certificates from Let's Encrypt live in Nginx. The app speaks plain HTTP inside the network and learns the real scheme and client IP from `X-Forwarded-Proto` / `X-Forwarded-For` (`server.forward-headers-strategy: framework`), which keeps the per-IP login and registration limits correct.
- **Reverse proxy and routing**: `API_DOMAIN` goes to the app (REST and the `/ws` WebSocket upgrade) and `/grafana/` to Grafana; `STORAGE_DOMAIN` goes to MinIO with the `Host` header untouched, because it is part of the presigned URL signature.
- **Protection**: request body limits (1 MB for the API; images go straight to MinIO), `/actuator/*` except health is hidden so metrics are only scraped internally, `server_tokens off`, gzip for JSON.

Nginx belongs to the **deployment of the whole system**, not to the backend code or to a frontend: keep it here in `deploy/`. If the admin console is deployed, build it to static files and either add a `server` block (for example `ADMIN_DOMAIN`, `root /usr/share/nginx/html; try_files $uri /index.html;`) or serve it under the API domain; on the same domain no CORS is needed, on its own domain set `CORS_ALLOWED_ORIGINS` to it. CORS itself is handled by Spring Security, not Nginx, so the rules stay the same in development and production; do not add `Access-Control-*` headers in Nginx as well, or browsers reject the duplicated headers.

## Monitoring

Prometheus scrapes `app:8080/actuator/prometheus` on the internal network every 15 s and keeps 15 days. Rules in `prometheus/alerts.yml`: backend down, more than 5% 5xx, p95 latency over 1 s, heap over 90%, database pool waiting, and auth rate-limit spikes. They show in Prometheus and Grafana; add Alertmanager to send them by email or chat. Grafana is at `https://API_DOMAIN/grafana/` (`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`) with the *Nearby backend* dashboard provisioned from `grafana/dashboards/`.

## CI/CD

Only CI is set up for now (see [docs/setup-ci.md](../docs/setup-ci.md)). There is no automated deployment yet. Until there is, build the image on the VM (`docker compose up -d --build`) or push it to a registry by hand.

## Backups

The nightly `pg_dump` goes to `deploy/backups/`. Sync that folder off-site.

```cron
0 2 * * * cd /opt/nearby/deploy && sh scripts/backup.sh >> backups/backup.log 2>&1
```

Restore with `docker compose exec -T postgres pg_restore -U nearby -d nearby --clean /backups/<file>.dump`.

## Fallback

If the cloud VM is unavailable during the defense, run the same stack on a laptop with `docker compose up -d`.
