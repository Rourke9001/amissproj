# AmissProj — Local Setup & Run Guide

A Java Swing remake of *Jones in the Fast Lane*, originally built in NetBeans.
This guide gets it running again on a modern machine **without NetBeans**.

---

## 1. What the project is

| | |
|---|---|
| **Language / UI** | Java 8 source, Swing desktop GUI (`amiss` package) |
| **Entry point** | `amiss.LoginGUI` |
| **Persistence** | MySQL, accessed through the thin `amiss.DB` JDBC wrapper |
| **External libs** | Maven-managed (MySQL Connector/J, SLF4J + Logback, jBCrypt); the one non-Central jar, NetBeans `AbsoluteLayout`, is vendored under `vendor/` |
| **Build** | Maven via the committed `mvnw` wrapper — no global Maven/NetBeans/Ant needed |

The game stores **all** state in MySQL (database `amissdb`). Without a running
database the login window still opens, but you cannot create or load a player.

---

## 2. Prerequisites

1. **A JDK** — already installed (`C:\Program Files\Java\jdk-20`). Any JDK 8+ works.
2. **MySQL Community Server 8.x or 9.x** — see step 3.

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
     Root is only used to run `db\setup.sql` (below); the game itself connects as
     a least-privilege **`amiss`** user that the script creates. App connection
     settings live in [`src/application.properties`](src/application.properties)
     and can be overridden at runtime with the `AMISS_DB_URL` / `AMISS_DB_USER` /
     `AMISS_DB_PASSWORD` environment variables — no need to edit Java or rebuild.

Verify the service is up:

```powershell
Get-Service | Where-Object Name -match 'mysql'   # STATUS should be Running
```

---

## 4. Create the database (one-time)

The schema + the game's reference data (jobs, help text) are in
[`db/setup.sql`](db/setup.sql). Load it once. Pick whichever is easiest:

**Option A — command line** (the MySQL Installer adds `mysql` to PATH; if not,
use the full path shown):

```powershell
mysql -u root -p < db\setup.sql
# if 'mysql' isn't found:
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p < db\setup.sql
```

Enter the root password (`password`) when prompted.

**Option B — MySQL Workbench:** File ▸ Open SQL Script ▸ `db\setup.sql` ▸ run (⚡).

This creates database `amissdb` with four tables, seeds `tbljobs` / `tblhelp`, and
creates the least-privilege **`amiss`@`localhost`** user the game logs in as
(password `amisspw`; granted only SELECT/INSERT/UPDATE on `amissdb`). It is safe to
re-run — it preserves saved players and only re-seeds reference data.

---

## 5. Build and run

```powershell
# from the project root (mvnw downloads a pinned Maven on first run)
.\mvnw clean package                   # compiles + tests + builds target\AmissProj.jar
java -jar target\AmissProj.jar         # launches the game
```

> The PowerShell helpers `scripts\build.ps1` / `scripts\run.ps1` still work and
> just delegate to `mvnw` / the packaged jar.

On launch the console should print **`Connection Successful`**. In the login
window, type a username + password and click **Logging In** — it will say the
user doesn't exist and reveal a **Create New User** button. Create the player and
the main city screen opens.

---

## 6. Troubleshooting

| Console / symptom | Cause & fix |
|---|---|
| `Cannot connect to database: Communications link failure` | MySQL isn't running. Start it: `net start MySQL84` (service name may differ) or via *services.msc*. |
| `Cannot connect to database: Access denied for user 'amiss'` | The `amiss` user/password don't match. Re-run `db\setup.sql` as root to (re)create the user, or set `AMISS_DB_USER` / `AMISS_DB_PASSWORD` to the right values. |
| `Cannot connect to database: Unknown database 'amissdb'` | You haven't loaded the schema — do step 4. |
| `Public Key Retrieval is not allowed` | Shouldn't happen (the JDBC URL sets `allowPublicKeyRetrieval=true`). If it does, confirm `DB.java` URL wasn't reverted. |
| `Cannot load driver` | The MySQL driver didn't resolve — rebuild with `.\mvnw clean package` so the connector is on the classpath. |
| A screen has no background image | Backgrounds load from bundled resources in `src/main/resources/amiss/resources/`. If one is blank the console prints `Asset missing on classpath: ...` — regenerate them with `powershell -File scripts\gen-placeholders.ps1`, then rebuild. |

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

---

## 8. What's next

Phases 1 and the first **Phase 2** item are **done**: backend hardening
(parameterised SQL, try-with-resources, least-privilege `amiss` user, externalised
config, BCrypt hashing, SLF4J/Logback logging, input validation) and the **Maven
build migration**. See **[ROADMAP.md](ROADMAP.md)** for the full phased plan; the
rest of **Phase 2** is next — a layered architecture that separates the game logic
from Swing so the rules can be unit-tested without a GUI, then JUnit 5 tests,
GitHub Actions CI and Flyway migrations.
