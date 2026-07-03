# AmissProj

[![CI](https://github.com/Rourke9001/amissproj/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Rourke9001/amissproj/actions/workflows/ci.yml)
![Coverage](https://raw.githubusercontent.com/Rourke9001/amissproj/badges/jacoco.svg)
![Branches](https://raw.githubusercontent.com/Rourke9001/amissproj/badges/branches.svg)

A Java **Swing** desktop game — a remake of the classic *Jones in the Fast Lane*.
You move around a small city, get a job, earn and spend money, study at university,
pay rent and chase your goals across rounds. All game state is persisted in **MySQL**.

> Originally built as a high-school project in NetBeans. This repo revives it on a
> modern toolchain (JDK 20 + MySQL 9 + Connector/J 9) and is being incrementally
> cleaned up as a Java refresher / portfolio piece.

---

## Tech stack

| | |
|---|---|
| Language / UI | Java 8 source, Swing (`amiss` package) |
| Database | MySQL 8.4+ / 9.x (`amissdb`); app runs as least-privilege `amiss` user |
| Migrations | Flyway 11 — versioned SQL in `src/main/resources/db/migration`, applied automatically at app start (by a dedicated `amiss_migrator` account) |
| JDBC driver | MySQL Connector/J 9.7 (Maven-managed) |
| Security | BCrypt password hashing (jbcrypt) + parameterised JDBC throughout |
| Logging | SLF4J + Logback (`src/main/resources/logback.xml`) |
| Config | `src/main/resources/application.properties`, overridable via `AMISS_DB_*` env vars |
| Build | Maven via the committed `mvnw` wrapper (no global Maven needed); one shaded runnable jar |
| Entry point | `amiss.LoginGUI` |

## Prerequisites

- A **JDK 17 or newer** (developed on JDK 20) — the Flyway engine that migrates
  the schema at startup needs 17+. (The source itself still targets Java 8.)
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

# 3. Build and run. The mvnw wrapper downloads a pinned Maven on first run -
#    no global Maven install required.
.\mvnw clean package
java -jar target\AmissProj.jar
```

> The PowerShell helpers `scripts\build.ps1` / `scripts\run.ps1` still work and
> simply delegate to `mvnw` / the packaged jar.

On launch the game first brings the schema up to date (Flyway logs
`Database schema up to date`), then the console prints `Connection Successful`.
In the login window, type a
username + password and click **Logging In** — it offers to create the user. Once
created, the city screen opens. (Tip: take a *Janitor* job first — it needs no
education or special clothes.)

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
- **Start:** double-click `target\AmissProj.jar`, or run `powershell -File scripts\run.ps1`.
- **Stop:** close the game window. If a window is left over, end it with:
  ```powershell
  Stop-Process -Name javaw     # closes the running game (Java GUI process)
  ```

---

## Build from source

```powershell
.\mvnw clean package                 # compiles + tests + builds target\AmissProj.jar (shaded)
java -jar target\AmissProj.jar       # runs the packaged jar
```
The wrapper (`mvnw`) downloads a pinned Maven on first run, so no global Maven,
NetBeans or Ant is required. `maven-shade-plugin` bundles all dependencies into
one runnable jar.

## Project structure

```
pom.xml                       Maven build (managed dependencies, shaded runnable jar)
mvnw, mvnw.cmd, .mvn/         Maven Wrapper (pinned Maven; no global install needed)
.github/workflows/ci.yml      GitHub Actions CI (build + tests + coverage badges on push/PR)
src/main/java/amiss/          Java source, layered into domain / application / infrastructure / presentation (see docs/ARCHITECTURE.md)
src/main/resources/amiss/resources/   bundled UI images (screen backgrounds + game board)
src/main/resources/db/migration/      Flyway versioned schema + seed data (applied at app start)
src/main/resources/application.properties  DB connection settings (overridable via AMISS_DB_* env vars)
src/main/resources/logback.xml  logging config (console + rolling file under logs/)
db/bootstrap.sql              One-time bootstrap: database + the two MySQL accounts
                              (schema itself lives in the Flyway migrations)
scripts/                      build.ps1 (mvnw wrapper), run.ps1 (launch),
                              gen-placeholders.ps1 (regenerate placeholder art)
vendor/AbsoluteLayout.jar     the one dependency not on Maven Central (NetBeans layout helper)
vendor-repo/                  project-local Maven repo holding the vendored AbsoluteLayout
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
