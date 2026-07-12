# AmissProj

[![CI](https://github.com/Rourke9001/amissproj/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Rourke9001/amissproj/actions/workflows/ci.yml)
![Coverage](https://raw.githubusercontent.com/Rourke9001/amissproj/badges/jacoco.svg)
![Branches](https://raw.githubusercontent.com/Rourke9001/amissproj/badges/branches.svg)

A full-stack Java remake of the classic *Jones in the Fast Lane*: a **Spring Boot
REST API** over a plain-Java rules core, consumed by a **React SPA**. You move
around a small city, get a job, earn and spend money, study at university, pay rent
and chase your goals across rounds. All game state is persisted in **MySQL**.

> Originally built as a high-school Swing project in NetBeans. This repo revives it
> on a modern toolchain (JDK 21 + Spring Boot 3 + React 19 + MySQL 9) and is being
> incrementally rebuilt as a Java refresher / portfolio piece. The original Swing
> desktop client was retired in July 2026 (KAN-51) once the web stack covered it.

---

## Tech stack

| | |
|---|---|
| Language | Java 21 (backend), TypeScript (frontend) |
| Modules | Maven reactor: `amiss-core` (rules + migrations), `amiss-api` (REST API), `amiss-coverage` (JaCoCo aggregate); React SPA in `frontend/` |
| Web frontend | React + TypeScript SPA (Vite) in `frontend/` (Phase 3, in progress — KAN-38 scaffold). Consumes the REST API only (no game rules in JS): typed fetch client, JWT auth plumbing + route guard, TanStack React Query, React Router. Dev server `npm run dev` on `:5173` proxies `/api` → `:8080` |
| REST API | Spring Boot 3.5 (`amiss-api`) over the same core services — `.\mvnw -f amiss-api spring-boot:run`, health at `/actuator/health`, RFC 7807 error responses. `POST /api/auth/register` and `POST /api/auth/login` are public; every other `/api/**` route requires a `Bearer` JWT (`GET /api/auth/me`), and a save-scoped route (`/api/saves/{saveId}/...`) 403s if the token's user doesn't own that save |
| Database | MySQL 8.4+ / 9.x (`amissdb`); app runs as least-privilege `amiss` user |
| Migrations | Flyway 11 — versioned SQL in `amiss-core/src/main/resources/db/migration`, applied automatically at API start (by a dedicated `amiss_migrator` account) |
| Persistence | Spring Data JPA (validate-only against the Flyway schema), MySQL Connector/J 9.7 |
| Security | BCrypt password hashing (jbcrypt); the API issues/verifies stateless HS256 JWTs and player-scopes every route |
| Config | `amiss-api/src/main/resources/application.yml`, overridable via `AMISS_DB_*` env vars, `AMISS_JWT_SECRET` (JWT signing key, 32+ bytes — a dev-only default is built in) and `AMISS_CORS_ALLOWED_ORIGINS` (comma-separated origins allowed to call the API cross-origin) |
| Build | Maven via the committed `mvnw` wrapper (no global Maven needed); frontend via npm/Vite |
| Entry point | `amiss.api.AmissApiApplication` (API) + `npm run dev` in `frontend/` (SPA) |

## Prerequisites

- A **JDK 21 or newer** (developed on Temurin 21 LTS) — the whole build targets
  Java 21 since Phase 3.
- **MySQL Server 8.4 LTS or 9.x**, running locally on port `3306`.

## Quick start

```powershell
# 1. Create the database + the MySQL accounts (one-time). The tables and
#    seed data are applied by Flyway migrations at first launch.
#    Enter your MySQL root password when asked.
mysql -u root -p < db\bootstrap.sql

# 2. (Optional) If your DB differs from the defaults, override at runtime:
#    $env:AMISS_DB_USER / $env:AMISS_DB_PASSWORD / $env:AMISS_DB_URL.
#    Defaults (amiss / amisspw / localhost:3306) live in src\application.properties.

# 3. Start the API. The mvnw wrapper downloads a pinned Maven on first run -
#    no global Maven install required. Flyway migrates the schema at startup.
.\mvnw -B install -DskipTests
.\mvnw -f amiss-api spring-boot:run

# 4. In a second shell, start the SPA dev server (proxies /api to :8080).
cd frontend
npm install
npm run dev      # open http://localhost:5173
```

On API startup Flyway brings the schema up to date (`Database schema up to date`
in the logs); health is at `http://localhost:8080/actuator/health`. In the SPA,
register a username + password and log in. (Tip: take a *Janitor* job first — it
needs no education or special clothes.)

Full first-time setup, including installing MySQL and troubleshooting, is in
**[SETUP.md](SETUP.md)**.

---

## Starting & stopping

### The MySQL server
It was installed as a Windows **service** (`MySQL97`) set to start automatically on
boot, so it's normally already running — you don't need to start it each time, and
you only need to stop it if you want to free up resources.

Starting/stopping a Windows service needs **administrator** rights — from a normal
shell you'll get `System error 5: Access is denied`. Two easy options:

**A. From a normal PowerShell** — each pops a UAC prompt (click *Yes*):
```powershell
Start-Process powershell -Verb RunAs -ArgumentList 'net start MySQL97'   # start
Start-Process powershell -Verb RunAs -ArgumentList 'net stop  MySQL97'   # stop
```

**B. From an elevated shell** (right-click PowerShell ▸ *Run as administrator*):
```powershell
net start MySQL97
net stop  MySQL97
```

Checking status needs no admin rights:
```powershell
Get-Service MySQL97
```
(You can also use the **Services** app — run `services.msc`, find *MySQL97*, Start/Stop.)

### The game
- **Start:** `.\mvnw -f amiss-api spring-boot:run` (API) + `npm run dev` in `frontend/` (SPA).
- **Stop:** Ctrl-C both. If an orphaned API keeps answering on :8080, stop the
  process that owns the port (never kill `java` by name — IDE tooling runs as java too):
  ```powershell
  Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess }
  ```

---

## Build from source

```powershell
.\mvnw clean package     # compiles + runs the unit suites for every module
.\mvnw clean verify      # additionally runs the Testcontainers ITs (needs Docker)
cd frontend; npm test    # frontend suite
```
The wrapper (`mvnw`) downloads a pinned Maven on first run, so no global Maven,
NetBeans or Ant is required.

## Project structure

```
pom.xml                       Reactor parent (module list, managed versions, shared plugins)
amiss-core/                   Game rules: domain / application / infrastructure layers,
                              Flyway migrations (src/main/resources/db/migration/),
                              and the whole JUnit 5 test suite (see docs/ARCHITECTURE.md)
amiss-api/                    Spring Boot REST API over amiss-core (Phase 3, in progress):
                              actuator health, RFC 7807 error envelope, application.yml,
                              JPA entities/adapters over the Flyway schema, and JWT auth with
                              full route lockdown + player-scoping (see Tech stack above)
amiss-coverage/               Aggregates per-module JaCoCo coverage for CI
frontend/                     React + TypeScript SPA (Vite) consuming amiss-api (Phase 3,
                              in progress): typed API client, JWT auth plumbing, route
                              guard, React Query; `npm run dev` proxies /api to :8080
mvnw, mvnw.cmd, .mvn/         Maven Wrapper (pinned Maven; no global install needed)
.github/workflows/ci.yml      GitHub Actions CI (backend build + tests + coverage badges,
                              frontend lint/typecheck/tests/build, on push/PR)
db/bootstrap.sql              One-time bootstrap: database + the two MySQL accounts
                              (schema itself lives in the Flyway migrations)
scripts/                      find-java21.ps1 (JDK resolver),
                              gen-frontend-placeholders.ps1 (regenerate placeholder art)
SETUP.md                      detailed setup & troubleshooting guide
tasks/                        roadmap progress, lessons, CV highlights
```

## Roadmap

This project is being grown from a high-school desktop game into a full-stack,
CV-ready Java application — backend hardening → testing & build tooling → a Spring
Boot API + web frontend → deployment. The full phased plan lives in
**[ROADMAP.md](ROADMAP.md)**.

## Notes

The contents of the `tbljobs` / `tblhelp` tables are a **reconstruction** — the
originals lived only in the old NetBeans database and were lost. Job names and
locations are taken verbatim from the code; the numeric values are tunable.
