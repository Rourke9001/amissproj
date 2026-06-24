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
| **External libs** | `AbsoluteLayout.jar`, `beansbinding-1.2.1.jar`, Connector/J (all in `dist/lib`) |
| **Build** | `scripts\build.ps1` (plain `javac` + `jar` — no NetBeans/Ant needed) |

The game stores **all** state in MySQL (database `amissdb`). Without a running
database the login window still opens, but you cannot create or load a player.

---

## 2. Prerequisites

1. **A JDK** — already installed (`C:\Program Files\Java\jdk-20`). Any JDK 8+ works.
2. **MySQL Community Server 8.x or 9.x** — see step 3.

> **Driver note:** the project originally shipped MySQL Connector/J **5.1.22 (2012)**,
> which cannot authenticate to MySQL 8/9. It has been replaced with
> **Connector/J 9.7.0** (`dist/lib/mysql-connector-j-9.7.0.jar`) and `DB.java`
> updated to the modern `com.mysql.cj.jdbc.Driver`. Nothing further to do here.

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
# from the project root
powershell -File scripts\build.ps1     # compiles src -> build\classes and rebuilds dist\AmissProj.jar
powershell -File scripts\run.ps1       # launches the game
```

On launch the console should print **`Connection Successful`**. In the login
window, type a username + password and click **Logging In** — it will say the
user doesn't exist and reveal a **Create New User** button. Create the player and
the main city screen opens.

> Alternative launch (uses the packaged jar):
> `cd dist; java -jar AmissProj.jar`

---

## 6. Troubleshooting

| Console / symptom | Cause & fix |
|---|---|
| `Cannot connect to database: Communications link failure` | MySQL isn't running. Start it: `net start MySQL84` (service name may differ) or via *services.msc*. |
| `Cannot connect to database: Access denied for user 'amiss'` | The `amiss` user/password don't match. Re-run `db\setup.sql` as root to (re)create the user, or set `AMISS_DB_USER` / `AMISS_DB_PASSWORD` to the right values. |
| `Cannot connect to database: Unknown database 'amissdb'` | You haven't loaded the schema — do step 4. |
| `Public Key Retrieval is not allowed` | Shouldn't happen (the JDBC URL sets `allowPublicKeyRetrieval=true`). If it does, confirm `DB.java` URL wasn't reverted. |
| `Cannot load driver` | The connector jar is missing from `dist/lib` — re-extract it (see §2). |
| A screen has no background image | Backgrounds load from bundled resources in `src/amiss/resources/`. If one is blank the console prints `Asset missing on classpath: ...` — regenerate them with `powershell -File scripts\gen-placeholders.ps1`, then rebuild. |

---

## 7. What changed from the original export

- **JDBC driver upgraded** 5.1.22 → 9.7.0; `DB.java` now uses `com.mysql.cj.jdbc.Driver`
  and a connection URL with `useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`.
- **Connection errors are now printed** (`DB.java` previously swallowed them silently).
- **Build no longer needs NetBeans/Ant** — `scripts\build.ps1` drives `javac`/`jar` directly.
- **Background images are now bundled & portable** — screens load art from
  `src/amiss/resources/` through `amiss.Assets` (classpath `getResource`), replacing the
  old hard-coded `C:\Users\The Rourke\...` paths. The placeholders are generated by
  `scripts\gen-placeholders.ps1`; drop in real art of the same name to replace them.
- **NetBeans `.form` files removed** — the GUIs are hand-edited in VSCode now, not the
  NetBeans visual designer. `.vscode/settings.json` points the Java extension at
  `dist/lib/*.jar` so `org.netbeans.*` / `com.mysql.cj.*` resolve in the editor.
- **`db/setup.sql` added** — the schema was reverse-engineered from the SQL in the
  code; the contents of `tbljobs`/`tblhelp` are a reconstruction (the originals
  lived only in NetBeans' database and were never in source control).
- **Phase 1 backend hardening** — all SQL is parameterised through a small
  JdbcTemplate-style `DB` (try-with-resources, no leaked cursors); passwords are
  BCrypt hashes (`PasswordHasher`, with transparent upgrade of legacy plaintext);
  the app connects as a least-privilege `amiss` user with settings from
  `src/application.properties` / `AMISS_DB_*` env vars (`Config`); logging is
  SLF4J + Logback; usernames/passwords are validated (`Validation`). New committed
  jars: `slf4j-api`, `logback-core`, `logback-classic`, `jbcrypt`.

---

## 8. What's next

Phase 1 (backend hygiene & security) is **done** — the items that used to live
here have shipped: parameterised SQL, try-with-resources, a least-privilege
`amiss` DB user, externalised config, BCrypt password hashing, SLF4J/Logback
logging and input validation. See **[ROADMAP.md](ROADMAP.md)** for the full
phased plan; **Phase 2** (Maven build, layered architecture, JUnit tests, CI) is
next — starting with separating the game logic from Swing so the rules can be
unit-tested without a GUI.
