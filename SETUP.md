# AmissProj — Local Setup & Run Guide

A Java Swing remake of *Jones in the Fast Lane*, originally built in NetBeans.
This guide gets it running again on a modern machine **without NetBeans**.

---

## 1. What the project is

| | |
|---|---|
| **Language / UI** | Java 21, Swing desktop GUI (`amiss.presentation` package in `amiss-swing`) |
| **Entry point** | `amiss.presentation.ui.LoginGUI` |
| **Persistence** | MySQL, accessed through the thin `Jdbc` helper behind repository ports (`amiss-core`) |
| **External libs** | Maven-managed (MySQL Connector/J, SLF4J + Logback, jBCrypt); the one non-Central jar, NetBeans `AbsoluteLayout`, is vendored under `vendor/` |
| **Build** | Maven multi-module reactor (`amiss-core` rules + `amiss-swing` client) via the committed `mvnw` wrapper — no global Maven/NetBeans/Ant needed |

The game stores **all** state in MySQL (database `amissdb`). Without a running
database the login window still opens, but you cannot create or load a player.

---

## 2. Prerequisites

1. **A JDK 21 or newer** — installed at
   `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` (Temurin 21 LTS).
   The whole reactor compiles with `--release 21` (Phase 3), so older JDKs no
   longer build it.
2. **MySQL Community Server 8.x or 9.x** — see step 3.
3. **Node.js 20+ with npm** *(web frontend only)* — needed only to build/run the
   React SPA in `frontend/` (Phase 3, in progress); the Swing game and the REST
   API build without it. Developed on Node 24 (https://nodejs.org).

> **Driver note:** the project originally shipped MySQL Connector/J **5.1.22 (2012)**,
> which cannot authenticate to MySQL 8/9. It has been replaced with
> **Connector/J 9.7.0** (now a Maven dependency) and `DB.java` updated to the
> modern `com.mysql.cj.jdbc.Driver`. Nothing further to do here.

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
     `amiss-swing/src/main/resources/application.properties` and can be overridden at runtime
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

```powershell
# from the project root (mvnw downloads a pinned Maven on first run)
.\mvnw clean package                          # compiles + tests + builds amiss-swing\target\AmissProj.jar
java -jar amiss-swing\target\AmissProj.jar    # launches the game
```

> The PowerShell helpers `scripts\build.ps1` / `scripts\run.ps1` still work and
> just delegate to `mvnw` / the packaged jar.

On launch the console should print **`Connection Successful`**. In the login
window, type a username + password and click **Logging In** — it will say the
user doesn't exist and reveal a **Create New User** button. Create the player and
the main city screen opens.

### Web frontend (React SPA, in progress)

The SPA in `frontend/` consumes the REST API, so start `amiss-api` first, then
the Vite dev server (which proxies every `/api` request to `:8080` — no CORS
setup needed in dev):

```powershell
.\mvnw -f amiss-api spring-boot:run   # REST API on http://localhost:8080

# in a second shell:
cd frontend
npm install                           # first time only
npm run dev                           # http://localhost:5173
```

The home page lists the live high scores straight from `GET /api/highscores` —
if it shows a connection error, the API isn't running. `npm run build` produces
the static production bundle in `frontend/dist/`.

---

## 6. Troubleshooting

| Console / symptom | Cause & fix |
|---|---|
| `Cannot connect to database: Communications link failure` | MySQL isn't running. Start it: `net start MySQL84` (service name may differ) or via *services.msc*. |
| `Cannot connect to database: Access denied for user 'amiss'` | The `amiss` user/password don't match. Re-run `db\bootstrap.sql` as root to (re)create the user, or set `AMISS_DB_USER` / `AMISS_DB_PASSWORD` to the right values. |
| `Cannot connect to database: Unknown database 'amissdb'` | You haven't bootstrapped the database — do step 4. |
| `Schema migration failed` in the console | Flyway couldn't connect as `amiss_migrator` — typically a pre-Flyway install. Re-run `db\bootstrap.sql` as root (it adds the account), or set `AMISS_DB_MIGRATOR_USER` / `AMISS_DB_MIGRATOR_PASSWORD`. |
| `Public Key Retrieval is not allowed` | Shouldn't happen (the JDBC URL sets `allowPublicKeyRetrieval=true`). If it does, confirm `DB.java` URL wasn't reverted. |
| `Cannot load driver` | The MySQL driver didn't resolve — rebuild with `.\mvnw clean package` so the connector is on the classpath. |
| The window never opens on double-click, or `java -jar` dies instantly with `UnsupportedClassVersionError` | The default `java` on PATH is older than 21 (e.g. the old JDK 20 shim). Launch via `powershell -File scripts\run.ps1` (it finds the Temurin 21 install itself), or point `JAVA_HOME`/PATH at a JDK 21+. |
| A screen has no background image | Backgrounds load from bundled resources in `amiss-swing/src/main/resources/amiss/resources/`. If one is blank the console prints `Asset missing on classpath: ...` — regenerate them with `powershell -File scripts\gen-placeholders.ps1`, then rebuild. |

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
  and `amiss-swing` (the desktop client, still shading `AmissProj.jar`), with an
  `amiss-coverage` module aggregating JaCoCo for CI — making room for the Spring
  Boot `amiss-api` module. The whole build now targets **Java 21**.

---

## 8. What's next

**Phases 0–2 are done**: revival, backend hardening (parameterised SQL,
least-privilege users, BCrypt, logging, validation), the Maven build, the
layered architecture with headless game rules, the 89-test JUnit 5 suite,
GitHub Actions CI and Flyway schema migrations. See **[ROADMAP.md](ROADMAP.md)**
for the full phased plan; next is **Phase 3** — extracting the game logic into a
Spring Boot REST API with JPA persistence, Spring Security and a web frontend.
