# AmissProj

[![CI](https://github.com/Rourke9001/amissproj/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Rourke9001/amissproj/actions/workflows/ci.yml)
![Coverage](https://raw.githubusercontent.com/Rourke9001/amissproj/badges/jacoco.svg)
![Branches](https://raw.githubusercontent.com/Rourke9001/amissproj/badges/branches.svg)

A Java **Swing** desktop game — a remake of the classic *Jones in the Fast Lane*.
You move around a small city, get a job, earn and spend money, study at university,
pay rent and chase your goals across rounds. All game state is persisted in **MySQL**.

> Originally built as a high-school project in NetBeans. This repo revives it on a
> modern toolchain (JDK 21 + MySQL 9 + Connector/J 9) and is being incrementally
> cleaned up as a Java refresher / portfolio piece.

---

## Tech stack

| | |
|---|---|
| Language / UI | Java 21, Swing (`amiss.presentation` in `amiss-swing`) |
| Modules | Maven reactor: `amiss-core` (rules + persistence + migrations), `amiss-swing` (desktop client), `amiss-api` (REST API), `amiss-coverage` (JaCoCo aggregate) |
| REST API | Spring Boot 3.5 (`amiss-api`) over the same core services — `.\mvnw -f amiss-api spring-boot:run`, health at `/actuator/health`, RFC 7807 error responses. `POST /api/auth/register`, `POST /api/auth/login` and `GET /api/highscores` are public; every other `/api/**` route requires a `Bearer` JWT (`GET /api/auth/me`), and a player-scoped route (`/api/players/{username}/...`) 403s if the token's subject doesn't match `{username}` |
| Database | MySQL 8.4+ / 9.x (`amissdb`); app runs as least-privilege `amiss` user |
| Migrations | Flyway 11 — versioned SQL in `amiss-core/src/main/resources/db/migration`, applied automatically at app start (by a dedicated `amiss_migrator` account) |
| JDBC driver | MySQL Connector/J 9.7 (Maven-managed) |
| Security | BCrypt password hashing (jbcrypt) + parameterised JDBC throughout; the API additionally issues/verifies stateless HS256 JWTs (`amiss-api`) |
| Logging | SLF4J + Logback (`amiss-swing/src/main/resources/logback.xml`) |
| Config | `amiss-swing/src/main/resources/application.properties`, overridable via `AMISS_DB_*` env vars; `amiss-api` additionally reads `AMISS_JWT_SECRET` (JWT signing key, 32+ bytes — a dev-only default is built in) and `AMISS_CORS_ALLOWED_ORIGINS` (comma-separated origins allowed to call the API cross-origin) |
| Build | Maven via the committed `mvnw` wrapper (no global Maven needed); the Swing client ships as one shaded runnable jar |
| Entry point | `amiss.presentation.ui.LoginGUI` |

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

# 3. Build and run. The mvnw wrapper downloads a pinned Maven on first run -
#    no global Maven install required.
.\mvnw clean package
java -jar amiss-swing\target\AmissProj.jar
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
- **Start:** double-click `amiss-swing\target\AmissProj.jar`, or run `powershell -File scripts\run.ps1`.
- **Stop:** close the game window. If a window is left over, end it with:
  ```powershell
  Stop-Process -Name javaw     # closes the running game (Java GUI process)
  ```

---

## Build from source

```powershell
.\mvnw clean package                          # compiles + tests + builds amiss-swing\target\AmissProj.jar (shaded)
java -jar amiss-swing\target\AmissProj.jar    # runs the packaged jar
```
The wrapper (`mvnw`) downloads a pinned Maven on first run, so no global Maven,
NetBeans or Ant is required. `maven-shade-plugin` bundles all dependencies into
one runnable jar.

## Project structure

```
pom.xml                       Reactor parent (module list, managed versions, shared plugins)
amiss-core/                   Game rules: domain / application / infrastructure layers,
                              Flyway migrations (src/main/resources/db/migration/),
                              and the whole JUnit 5 test suite (see docs/ARCHITECTURE.md)
amiss-swing/                  The Swing desktop client (amiss.presentation), bundled UI
                              images, application.properties + logback.xml; shades the
                              runnable amiss-swing/target/AmissProj.jar
amiss-api/                    Spring Boot REST API over amiss-core (Phase 3, in progress):
                              actuator health, RFC 7807 error envelope, application.yml,
                              JPA entities/adapters over the Flyway schema, and JWT auth with
                              full route lockdown + player-scoping (see Tech stack above)
amiss-coverage/               Aggregates per-module JaCoCo coverage for CI
mvnw, mvnw.cmd, .mvn/         Maven Wrapper (pinned Maven; no global install needed)
.github/workflows/ci.yml      GitHub Actions CI (build + tests + coverage badges on push/PR)
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
