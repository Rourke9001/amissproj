# Tasks — AmissProj revival

## Goal 1: Get the old NetBeans project running locally again

### Plan
- [x] Review the exported project; identify entry point, DB usage, dependencies
- [x] Confirm toolchain on this machine (JDK 20 present; no Ant/MySQL)
- [x] Locate dependencies — found `dist/lib` already bundles AbsoluteLayout + beansbinding
- [x] Replace dead MySQL Connector/J 5.1.22 with modern 9.7.0 (from the user's bundle)
- [x] Modernise `DB.java` (driver class + connection URL; surface connection errors)
- [x] Reverse-engineer the schema from inline SQL → `db/setup.sql`
- [x] Reconstruct reference data (`tbljobs`, `tblhelp`) — lost with the old DB
- [x] Add `scripts/build.ps1` (javac + jar, no NetBeans/Ant) and `scripts/run.ps1`
- [x] Verify clean compile on JDK 20 and that the driver loads (only the absent
      server errors out)
- [x] Write `SETUP.md` runbook
- [x] **USER:** installed MySQL 9.7.1 Community (service `MySQL97`, root password = `password`)
- [x] Aligned `DB.java` to `root`/`password`; loaded schema via `db\setup.sql`
- [x] Confirmed app prints "Connection Successful" against the live DB
- [x] Verified the game's exact signup INSERTs + a job lookup + FK cascade cleanup
- [x] Launched the game GUI (login window opens)
- [ ] **USER:** play-test — create a player, take a Janitor job, work, study
      (final confirmation the reconstructed seed values feel right)
- [ ] (optional) Install MySQL Workbench for a DB GUI — https://dev.mysql.com/downloads/workbench/

### Review
*Goal 1 complete: the project builds with plain javac (no NetBeans/Ant), the JDBC
driver is modernised to Connector/J 9.7, and the app connects to MySQL 9.7.1 and
runs. Schema columns/types are derived directly from the code's SQL (reliable);
`tbljobs`/`tblhelp` row values are a reconstruction (originals unrecoverable) and
can be tuned after play-testing. Credentials are root/`password` for now —
replacing root with a least-privilege user is the first Goal 2 item.*

---

## Goal 2: backend hygiene & security  →  shipped as ROADMAP Phase 1 (June 2026)
The "fix what I can" backlog (ROADMAP.md Phase 1 is the canonical list):
- [x] Parameterise all SQL (`PreparedStatement`) — killed the pervasive SQL injection
- [x] try-with-resources for all JDBC `ResultSet`/`Statement`
- [x] Dedicated least-privilege DB user instead of `root`
- [x] Externalise DB config (properties/env) out of `DB.java`
- [x] Hash passwords (BCrypt), SLF4J/Logback logging, input validation
- [ ] Separate game logic from Swing so rules are unit-testable  → ROADMAP Phase 2

### Review — Phase 1 (backend hygiene & security)
All seven Phase-1 items shipped on branch `feat/phase1-backend-hardening`, one
commit per step. `DB` is now a small JdbcTemplate-style helper (parameterised,
try-with-resources); passwords are BCrypt hashes with transparent legacy upgrade;
the app connects as the least-privilege `amiss` user using settings from
`application.properties` / `AMISS_DB_*`; logging is SLF4J + Logback; usernames and
passwords are validated. Verified end-to-end against live MySQL as `amiss`
(connect, hashed insert, password verify, update, and DELETE correctly denied).

Found-but-deferred (for the backlog, out of scope here): `HelpGUI` reads column
`descip` while `setup.sql` defines `description`, so in-game Help has been broken
since the schema reconstruction — a one-line fix that belongs in its own commit.

---

## Goal 3: Phase 2 — Architecture, testing & build tooling  (ROADMAP Phase 2)
Tackled one branch + PR per item. CV bullets for each shipped item live in
`tasks/cv-highlights.md`.

### Item 1 — Migrate the build to Maven  (branch `chore/maven-build`)
Plan:
- [x] Move sources to the Maven standard layout (`src/main/java`, `src/main/resources`)
      via `git mv` (resources kept at `/amiss/resources/...` so `getResource` resolves)
- [x] Vendor the one non-Central jar (NetBeans `AbsoluteLayout`) → `vendor/` +
      project-local `vendor-repo/` (`install:install-file -DlocalRepositoryPath`)
- [x] `pom.xml`: managed deps (drop dead `beansbinding`), `release 8`,
      `maven-shade-plugin` uber-jar with manifest + **services** transformers
- [x] Add the Maven Wrapper (`mvnw`) — no global Maven on this box
- [x] Rewire `scripts/*.ps1`, `.gitignore`, drop `.vscode/settings.json`; update
      README/SETUP/ROADMAP and `Assets`/`gen-placeholders` resource paths
- [x] Delete NetBeans/Ant leftovers (`build.xml`, `nbproject/`, `manifest.mf`, `build/`,
      `dist/`)
- [x] Verify: `./mvnw clean package` → `target/AmissProj.jar`; `java -jar` launches and
      logs `Connection Successful`

### Review — Item 1 (Maven migration)
Build migrated from hand-rolled `scripts/build.ps1` + 7 committed jars to a declarative
Maven build with the `mvnw` wrapper (reproducible, no global Maven). `maven-shade-plugin`
produces one runnable `target/AmissProj.jar`; the `ServicesResourceTransformer` was
essential so the shaded SLF4J/Logback provider and JDBC `java.sql.Driver` still resolve.
Dropped the unused `beansbinding`; vendored `AbsoluteLayout` (not on Central) via a
committed project-local repo. Verified end-to-end against live MySQL: the uber-jar
launches the GUI and logs `Connection Successful` to console + `logs/amiss.log`, proving
logging, config and the MySQL driver all load from inside the single jar. Behaviour is
unchanged vs `main` — same game, same `amiss.LoginGUI` entry point.

Remaining Phase 2 items (each its own branch/PR): layered architecture + decouple rules
from Swing (combined), JUnit 5 tests, GitHub Actions CI, Flyway migrations. The
`HelpGUI` `descip`→`description` bug will be fixed within the Flyway/repository work.
