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
   - **Set a root password and remember it.** On this machine it was set to
     `password`, and [`src/amiss/DB.java`](src/amiss/DB.java) is already
     configured to connect as `root` / `password`. *(If you use a different
     password, change `password` in `DB.java` and re-run `scripts\build.ps1`.)*

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

Enter the root password (`nbuser`) when prompted.

**Option B — MySQL Workbench:** File ▸ Open SQL Script ▸ `db\setup.sql` ▸ run (⚡).

This creates database `amissdb` with four tables and seeds `tbljobs` / `tblhelp`.
It is safe to re-run — it preserves saved players and only re-seeds reference data.

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
| `Cannot connect to database: Access denied for user 'root'` | root password isn't `password`. Reset it, or edit `password` in `DB.java` and rebuild. |
| `Cannot connect to database: Unknown database 'amissdb'` | You haven't loaded the schema — do step 4. |
| `Public Key Retrieval is not allowed` | Shouldn't happen (the JDBC URL sets `allowPublicKeyRetrieval=true`). If it does, confirm `DB.java` URL wasn't reverted. |
| `Cannot load driver` | The connector jar is missing from `dist/lib` — re-extract it (see §2). |
| Login window has no background images | Cosmetic only — the old code hard-codes image paths like `C:\Users\The Rourke\Pictures\...`. Ignore for now. |

---

## 7. What changed from the original export

- **JDBC driver upgraded** 5.1.22 → 9.7.0; `DB.java` now uses `com.mysql.cj.jdbc.Driver`
  and a connection URL with `useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`.
- **Connection errors are now printed** (`DB.java` previously swallowed them silently).
- **Build no longer needs NetBeans/Ant** — `scripts\build.ps1` drives `javac`/`jar` directly.
- **`db/setup.sql` added** — the schema was reverse-engineered from the SQL in the
  code; the contents of `tbljobs`/`tblhelp` are a reconstruction (the originals
  lived only in NetBeans' database and were never in source control).

---

## 8. Good next steps (for refreshing your Java / impressing reviewers)

These are deliberately **not** done yet — they're the "fix what I can" backlog:

1. **Use a dedicated least-privilege DB user** instead of `root` (create
   `amiss`@`localhost` with rights only on `amissdb`).
2. **Externalise the DB config** (host/user/password) to a properties file or
   environment variables instead of hard-coding in `DB.java`.
3. **Fix SQL injection** — every query is built by string concatenation
   (e.g. `"... WHERE name = '" + user + "'"`). Switch to parameterised
   `PreparedStatement`s. This is the single most important "banking-grade" fix.
4. **Close JDBC resources** (`ResultSet`/`Statement`/`Connection`) with
   try-with-resources to stop leaking connections.
5. **Separate game logic from Swing** so the rules can be unit-tested without a GUI.
