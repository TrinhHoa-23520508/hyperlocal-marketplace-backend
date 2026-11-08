# Nearby backend

Backend for the hyperlocal second-hand marketplace. It is a Spring Boot 4.1 / Java 25 **modular monolith** (Spring Modulith) built on PostgreSQL + PostGIS, Redis, and MinIO.

## Requirements

- JDK 25
- Docker with Docker Compose

## Setup

```sh
cp .env.example .env
```

## Run

```sh
./gradlew bootRun      # compose.yaml starts PostGIS (5433), Redis and MinIO
```

The API runs on <http://localhost:8080>, with Swagger UI at <http://localhost:8080/swagger-ui.html>.

## Build

```sh
./gradlew build        # tests + format check + module verification + coverage gate
./gradlew format       # fixes formatting
```

## Architecture

```mermaid
flowchart LR
    A["Android app"] -->|"REST + STOMP"| N["Nginx"]
    A -->|"presigned image URLs"| N
    FCM["Firebase Cloud Messaging (planned)"] -.->|"push"| A

    subgraph VM["Docker Compose"]
        N --> APP
        N --> M[("MinIO")]

        subgraph APP["Spring Boot modular monolith"]
            direction TB
            ID["identity"]
            AV["areaverification"]
            L["listing"]
            T["transaction"]
            REP["reputation"]
            C["chat"]
            SH["shared"]
        end

        APP --> P[("PostgreSQL + PostGIS")]
        APP --> R[("Redis")]
        APP --> M
        PR["Prometheus"] --> APP
        G["Grafana"] --> PR
    end

    APP -.-> FCM
    APP --> SMTP["SMTP"]
```

## More

- [Local setup](docs/setup-local.md)
- [CI](docs/setup-ci.md)
- [Deployment](deploy/README.md)
- [Identity API](docs/api/identity-api.md)
