# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run locally
./gradlew run

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "ApplicationTest"

# Build executable fat JAR
./gradlew buildFatJar

# Build Docker image
./gradlew buildImage

# Start all services via Docker Compose
docker-compose up -d

# View backend logs
docker-compose logs -f backend

# Check health
curl http://localhost:8080/health
```

## Architecture Overview

**Ktor 3.1.3** REST API (Netty engine) with **Kotlin 2.1.10**, using a layered architecture:

### Layer Structure

- **`routes/`** — Ktor route handlers, one file per resource (`ClothesRoute.kt`, `LooksRoute.kt`, `ProfileRoute.kt`, `SharedLooksRoute.kt`). Auth routes live in `Routing.kt`.
- **`usecases/`** — Orchestrate business logic across repositories (e.g., `ClotheUseCase`, `ImportSharedLookUseCase`).
- **`services/`** — External API clients: `RemoveBgService` (background removal) and `ClotheAnalysisService` (AI fashion analysis).
- **`database/domain/repository/`** — Repository interfaces.
- **`database/data/repository/`** — Repository implementations using Exposed ORM.
- **`database/data/model/`** — Exposed DAO entity classes and table definitions.
- **`di/`** — Koin DI modules: `authModule`, `databaseModule`, `remBgModule`.
- **`plugins/`** — Ktor plugin setup: error handling (`StatusPages`), CORS, rate limiting, logging.
- **`auth/`** — JWT config, environment config loading, auth DTOs.

### Key Patterns

- **Database**: PostgreSQL via Exposed ORM (DAO pattern). Schema auto-created on startup via `SchemaUtils.create()`. All DB calls use `suspendTransaction()` (coroutine-safe wrapper around `newSuspendedTransaction(Dispatchers.IO)`).
- **Dependency Injection**: Koin — inject repositories and services into route handlers via `inject<>()`.
- **Authentication**: JWT (HMAC256), 15-min access tokens + 30-day refresh tokens. Token revocation via `RevokedTokenTable`. Routes are protected by the `authenticate("auth-jwt")` block.
- **Rate Limiting**: Three tiers — auth endpoints (10 req/min, IP-based), upload endpoints (20 req/min, user-based), default (100 req/min, user-based).
- **Error Handling**: Centralized in `plugins/ErrorHandling.kt` using `StatusPages`. Typed exception mapping to HTTP status codes.

### Database Tables

`UserTable`, `ClotheTable`, `UserClotheTable`, `LookTable`, `UserLookTable`, `LookItemTable`, `SharedLookTable`, `RevokedTokenTable`

### External Services (via Docker Compose)

- **RemoveBG Service** (`REMOVE_BG_SERVICE_URL`, default port 8000) — clothing background removal
- **Fashion Analyzer** (`ANALYSIS_SERVICE_URL`, default port 8088) — AI-based clothing attribute detection

### File Storage

Uploaded clothing images → `uploads/` directory. Look/outfit images → `looks/` directory. Both are volume-mounted in Docker.

## Environment Configuration

Copy `.env.example` to `.env` before running locally. Required variables validated on startup by `ConfigValidator.kt` — missing required fields throw `IllegalStateException`.

Key required variables: `JWT_SECRET` (min 32 chars), `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `REMOVE_BG_SERVICE_URL`, `ANALYSIS_SERVICE_URL`.

## API Structure

All authenticated endpoints are under `/api/v1`. The `/health` endpoint is public. Auth endpoints (`/register`, `/login`, `/refresh`, `/logout`) are defined in `routes/Routing.kt`.
