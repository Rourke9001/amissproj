# AmissProj — Local Setup & Run Guide

A full-stack Java remake of *Jones in the Fast Lane*, originally a NetBeans Swing
project. This guide gets the **Spring Boot API + React SPA** running on a clean
machine. (The Swing desktop client was retired in July 2026, KAN-51.)

---

## 1. What the project is

| | |
|---|---|
| **Backend** | Java 21: `amiss-core` (game rules behind repository ports) + `amiss-api` (Spring Boot 3 REST API, Spring Data JPA validate-only over the Flyway schema, JWT auth) |
| **Frontend** | React 19 + TypeScript SPA (Vite) in `frontend/` |
| **Entry points** | `amiss.api.AmissApiApplication` (API, `:8080`) and `npm run dev` (SPA, `:5173`) |
| **Persistence** | MySQL (database `amissdb`); schema owned by Flyway migrations in `amiss-core` |
| **Build** | Maven multi-module reactor via the committed `mvnw` wrapper — no global Maven/NetBeans/Ant needed; npm for the frontend |

The game stores **all** state in MySQL. Without a running database the API
fails at startup (Flyway can't connect).

---

## 2. Prerequisites

1. **A JDK 21 or newer** — installed at
   `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` (Temurin 21 LTS).
   The whole reactor compiles with `--release 21` (Phase 3), so older JDKs no
   longer build it.
2. **MySQL Community Server 8.x or 9.x** — see step 3.
3. **Node.js 20+ with npm** — to build/run the React SPA in `frontend/`; the
   REST API builds without it. Developed on Node 24 (https://nodejs.org).

---

## 3. Install MySQL (one-time)

1. Download the **MySQL Installer for Windows (Community)**:
   https://dev.mysql.com/downloads/installer/
   (Pick the larger "full" installer if offered. Community = free/GPL, the right
   licence for a portfolio project.)
2. Run it and install at least **MySQL Server** (latest GA — 8.4 LTS or 9.x are
   both fine). MySQL Workbench is a useful optional extra.
3. In the configuration wizard:
   - Keep **port `3306`** and run it **as a Windows Service** (starts on boot).
   - Authentication: **"Use Strong Password Encryption"** (the default) is fine.
   - **Set a root password and remember it.** On this machine it is `password`.
     Root is only used to run `db\bootstrap.sql` (below); the game itself connects
     as a least-privilege **`amiss`** user that the script creates (plus a
     DDL-capable **`amiss_migrator`** account used only while Flyway applies the
     schema migrations at startup). App connection settings live in
     `amiss-api/src/main/resources/application.yml` and can be overridden at runtime
     with the `AMISS_DB_URL` / `AMISS_DB_USER` / `AMISS_DB_PASSWORD` /
     `AMISS_DB_MIGRATOR_USER` / `AMISS_DB_MIGRATOR_PASSWORD` environment
     variables — no need to edit Java or rebuild.

> **`amiss-api` only:** two further env vars configure the REST API's Spring Security layer
> (`amiss-api/src/main/resources/application.yml`) — `AMISS_JWT_SECRET`, the HS256 signing key
> for register/login access tokens (32+ bytes; a dev-only default is built in, but every real
> deployment must override it), and `AMISS_CORS_ALLOWED_ORIGINS`, a comma-separated list of
> origins allowed to call the API cross-origin (defaults to the Vite dev server,
> `http://localhost:5173`, for the future web frontend).

Verify the service is up:

```powershell
Get-Service | Where-Object Name -match 'mysql'   # STATUS should be Running
```

---

## 4. Bootstrap the database (one-time)

[`db/bootstrap.sql`](db/bootstrap.sql) creates only what Flyway can't create for
itself: the `amissdb` database and the two MySQL accounts. The tables and seed
data live in **versioned Flyway migrations**
(`amiss-core/src/main/resources/db/migration/`), which the game applies automatically the
first time it starts. Run it once, whichever way is easiest:

**Option A — command line** (the MySQL Installer adds `mysql` to PATH; if not,
use the full path shown):

```powershell
mysql -u root -p < db\bootstrap.sql
# if 'mysql' isn't found:
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p < db\bootstrap.sql
```

Enter the root password (`password`) when prompted.

**Option B — MySQL Workbench:** File ▸ Open SQL Script ▸ `db\bootstrap.sql` ▸ run (⚡).

This creates database `amissdb` plus two accounts: the least-privilege
**`amiss`@`localhost`** user the game plays as (password `amisspw`; only
SELECT/INSERT/UPDATE on `amissdb`) and **`amiss_migrator`@`localhost`**
(password `amissmigratorpw`; DDL + DML on `amissdb`), which is used only while
the startup migrations run. It is safe to re-run — everything is
`IF NOT EXISTS`, and saved players are never touched.

> **Upgrading from a pre-Flyway install?** Just re-run `db\bootstrap.sql` once
> (it adds the missing `amiss_migrator` account). On the next launch Flyway
> *baselines* the existing schema — saves are kept — and takes over from there.

---

## 5. Build and run

The SPA in `frontend/` consumes the REST API, so start `amiss-api` first, then
the Vite dev server (which proxies every `/api` request to `:8080` — no CORS
setup needed in dev):

```powershell
# from the project root (mvnw downloads a pinned Maven on first run)
.\mvnw -B install -DskipTests         # build the reactor once (core into ~/.m2)
.\mvnw -f amiss-api spring-boot:run   # REST API on http://localhost:8080

# in a second shell:
cd frontend
npm install                           # first time only
npm run dev                           # http://localhost:5173
```

On API startup Flyway logs `Database schema up to date` and health is at
`http://localhost:8080/actuator/health`. Open the SPA, register a username +
password, log in and play. `npm run build` produces the static production
bundle in `frontend/dist/`. Run the test suites with `.\mvnw clean package`
(unit), `.\mvnw clean verify` (adds the Testcontainers ITs; needs Docker
Desktop) and `npm test`.

---

## 6. Troubleshooting

| Console / symptom | Cause & fix |
|---|---|
| `Cannot connect to database: Communications link failure` | MySQL isn't running. Start it: `net start MySQL84` (service name may differ) or via *services.msc*. |
| `Cannot connect to database: Access denied for user 'amiss'` | The `amiss` user/password don't match. Re-run `db\bootstrap.sql` as root to (re)create the user, or set `AMISS_DB_USER` / `AMISS_DB_PASSWORD` to the right values. |
| `Cannot connect to database: Unknown database 'amissdb'` | You haven't bootstrapped the database — do step 4. |
| `Schema migration failed` in the console | Flyway couldn't connect as `amiss_migrator` — typically a pre-Flyway install. Re-run `db\bootstrap.sql` as root (it adds the account), or set `AMISS_DB_MIGRATOR_USER` / `AMISS_DB_MIGRATOR_PASSWORD`. |
| `Public Key Retrieval is not allowed` | Shouldn't happen (the JDBC URL sets `allowPublicKeyRetrieval=true`). If it does, check the datasource URL in `application.yml`. |
| `mvnw` dies with `UnsupportedClassVersionError` or "JAVA_HOME not found" | The default `java`/`JAVA_HOME` points at a pre-21 JDK. Point `JAVA_HOME` at a JDK 21+ (`scripts\find-java21.ps1` locates one). |
| `spring-boot:run` serves stale code after core changes | The single-module run resolves `amiss-core` from `~/.m2` — run `.\mvnw -B install -DskipTests` first. |
| SPA shows a connection error on login | The API isn't running (or died at startup — check its console for the Flyway/MySQL errors above). |
| A board tile has no image | Frontend art lives in `frontend/public/assets/` behind a manifest — regenerate placeholders with `powershell -File scripts\gen-frontend-placeholders.ps1`. |

---

## 7. What changed from the original export

- **JDBC driver upgraded** 5.1.22 → 9.7.0; `DB.java` now uses `com.mysql.cj.jdbc.Driver`
  and a connection URL with `useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`.
- **Connection errors are now printed** (`DB.java` previously swallowed them silently).
- **Build moved off NetBeans/Ant** — first to `scripts\build.ps1` (plain `javac`/`jar`),
  later to Maven (see the Phase 2 bullet below).
- **Background images are now bundled & portable** — screens load art from
  `src/main/resources/amiss/resources/` through `amiss.Assets` (classpath `getResource`),
  replacing the old hard-coded `C:\Users\The Rourke\...` paths. The placeholders are
  generated by `scripts\gen-placeholders.ps1`; drop in real art of the same name to replace them.
- **NetBeans `.form` files removed** — the GUIs are hand-edited outside the NetBeans
  visual designer. The editor resolves the classpath from `pom.xml` now, so no manual
  `.vscode` library configuration is needed.
- **`db/setup.sql` added** — the schema was reverse-engineered from the SQL in the
  code; the contents of `tbljobs`/`tblhelp` are a reconstruction (the originals
  lived only in NetBeans' database and were never in source control).
- **Phase 1 backend hardening** — all SQL is parameterised through a small
  JdbcTemplate-style `DB` (try-with-resources, no leaked cursors); passwords are
  BCrypt hashes (`PasswordHasher`, with transparent upgrade of legacy plaintext);
  the app connects as a least-privilege `amiss` user with settings from
  `src/application.properties` / `AMISS_DB_*` env vars (`Config`); logging is
  SLF4J + Logback; usernames/passwords are validated (`Validation`).
- **Phase 2 — build migrated to Maven** — the hand-rolled `scripts\build.ps1` + committed
  `dist/lib` jars are replaced by a `pom.xml` with managed dependencies plus the `mvnw`
  wrapper (no global Maven needed). `maven-shade-plugin` builds a single runnable
  `target/AmissProj.jar`. The dead `beansbinding` dependency was dropped; the one
  non-Central jar — NetBeans `AbsoluteLayout` — is vendored into a project-local Maven
  repo (`vendor/` + `vendor-repo/`). NetBeans leftovers (`build.xml`, `nbproject/`,
  `manifest.mf`) were removed.
- **Phase 2 — Flyway migrations** — the schema + seed data moved from the
  hand-maintained `db/setup.sql` (retired) into versioned migrations under
  `src/main/resources/db/migration/`, applied automatically at startup by a
  dedicated `amiss_migrator` account; `db/bootstrap.sql` now only creates the
  database + the two MySQL accounts. Existing pre-Flyway databases are
  *baselined* on first launch (saves kept).
- **Phase 3 — multi-module reactor** — the single Maven module split into
  `amiss-core` (domain/application/infrastructure + migrations + the test suite)
  and `amiss-swing` (the desktop client), with an `amiss-coverage` module
  aggregating JaCoCo for CI — making room for the Spring Boot `amiss-api`
  module. The whole build now targets **Java 21**.
- **Phase 3 — Swing client retired (July 2026, KAN-51)** — once the REST API +
  React SPA covered the game, the `amiss-swing` module, its shaded
  `AmissProj.jar`, the vendored NetBeans `AbsoluteLayout` jar and the core's
  Swing-only JDBC adapters/`GameContext` were removed. The API's Spring Data JPA
  adapters are now the only persistence path, and Boot's auto-configured Flyway
  the only migration runner.

---

## 8. What's next

**Phases 0–2 are done** (revival, backend hardening, Maven, tests, CI, Flyway),
and **Phase 3 is nearly there**: the Spring Boot REST API with JPA persistence
and JWT security is live, and the React SPA covers every building on the board.
See **[ROADMAP.md](ROADMAP.md)** for the full phased plan; in flight is the
Jones-parity jobs/degrees/saves milestone
(`docs/superpowers/specs/2026-07-07-jobs-degrees-saves-design.md`), then
OpenAPI docs, Docker and deployment.
