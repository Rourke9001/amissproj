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

### Item 2 — Layered architecture, PR A: persistence layer  (branch `feat/phase2-persistence-layer`)
The "layered architecture + decouple rules from Swing" item, split into two PRs. PR A is
the low-risk persistence half; PR B adds the service layer and removes the statics.
Plan:
- [x] New `amiss.repository` package: `UserRepository` / `UserStatsRepository` /
      `JobRepository` / `HelpRepository`, each constructor-injected with a `DB`, Swing-free
- [x] Route every inline SQL call through the repos — game logic (`Stats`, `GetAJob`,
      `CalcDuration`, `Food`, `University`) via a private repo accessor over the still-static
      `MainGameGUI.db`; Swing screens (`LoginGUI`, `HighScoreGUI`, `HelpGUI`) via repo fields
- [x] Reads keep each caller's historical default; `Optional` for the may-not-exist lookups
- [x] Fix the `HelpGUI` `descip`→`description` bug (Help broken since the schema rebuild)
- [x] Verify: `./mvnw clean package` green; launch against live MySQL logs
      `Connection Successful`; DB-level check confirms `SELECT description` works and the old
      `descip` errors; Swing `initComponents` untouched in the diff

### Review — Item 2 PR A (persistence layer)
Moved ~40 inline SQL statements out of ten classes and behind four repositories in a new
`amiss.repository` package, leaving no SQL in the game rules or the Swing screens. The
repositories wrap the existing JdbcTemplate-style `DB` helper, take their data source by
constructor, and have no Swing/static dependency — so they already run headless, which is
the setup PR B needs for a Swing-free service layer. The refactor is behaviour-preserving
by construction: the SQL strings and per-column defaults are unchanged, and each call site
is a 1:1 swap (game-logic classes build a repo from the still-static `MainGameGUI.db` via a
small private accessor; PR B turns those into injected fields and deletes the statics).
Consolidating the Help query surfaced and fixed a real bug — the screen had queried column
`descip` while the schema column is `description`, so every Help topic had thrown
`SQLException` ("Can't Load Help") since the schema reconstruction. Verified end-to-end:
green Maven package, the uber-jar connects to live MySQL and opens the login screen, and a
direct DB check confirms `SELECT description ... = 'Controls'` returns the text while
`SELECT descip ...` errors with "Unknown column 'descip'". `git diff main` shows only the
eight intended files plus the new package; no Swing layout code changed.

PR B (next): extract the game rules into Swing-free services returning result objects, drop
the `MainGameGUI.db`/`MainGameGUI.user` statics via constructor injection, leave the GUIs as
thin callers.

### Item 2 — Layered architecture, PR B: service layer + headless decoupling  (branch `feat/phase2-service-layer`)
The second half of the "layered architecture + decouple rules from Swing" item.
Plan:
- [x] New `amiss.service` package: rename the five rules → `TimeService`/`EducationService`/
      `FoodService`/`JobService`/`StatsService`, Swing-free and constructor-injected with
      their repositories + the current username (no `MainGameGUI` statics)
- [x] `GameServices` composition root wires the repos + services from a `(User, DB)`; each
      screen holds one and reaches the rules through it
- [x] `ActionResult` result object for the two orchestration methods (`workMain`/`eatMain`)
      so the rules return notification text + timer/money instead of mutating widgets
- [x] Drop the `JTextArea`/`JLabel` params from the rules; persistence failures log via
      SLF4J (matching `CalcDuration`'s old precedent) — normal play byte-identical
- [x] Kill the `MainGameGUI.db/user` statics: `user`/`db` become instance fields across the
      screens; delete each screen's dead NetBeans `main()` stub (entry point is `LoginGUI`)
- [x] Verify: `./mvnw clean package` green; launch logs `Connection Successful`; a no-Swing
      probe drives `GameServices` against live MySQL; `git diff` shows zero `initComponents()`
      changes

### Review — Item 2 PR B (service layer + headless decoupling)
Extracted the game rules from five Swing-coupled classes (`CalcDuration`/`University`/`Food`/
`GetAJob`/`Stats`) into a new `amiss.service` package of intention-named, constructor-injected
services, with a `GameServices` composition root doing the wiring. The rules no longer take
`JTextArea`/`JLabel`; the work/eat orchestration returns an immutable `ActionResult` the screen
renders, and other methods return plain values. The `MainGameGUI.db`/`user` ambient statics are
gone — every screen now holds `user`/`db` as instance fields and builds its services locally —
and the dead NetBeans `main()` stubs were removed (real entry point: `amiss.LoginGUI`). The
refactor is behaviour-preserving by construction: each method maps 1:1, side-effect ordering is
unchanged, and the only deliberate delta is that mid-game persistence-failure messages now go to
`logs/amiss.log` instead of the notification area (normal play is byte-identical — no control
flow branches on that text). Verified end-to-end: `./mvnw clean package` is green and builds the
shaded jar; the jar launches and logs `Connection Successful`; a 30-line **no-Swing probe**
constructs `GameServices` and reads a live player's full state through every service against
MySQL (the headless capability this PR unlocks); and `git diff main` shows no change inside any
generated `initComponents()` block — only field decls, constructors and button handlers moved.

Phase 2 "layered architecture + decouple rules from Swing" is now complete (PR A + PR B). Next
Phase 2 items: JUnit 5 tests over the now-headless services, then GitHub Actions CI, then Flyway
migrations.

### Item 3 — JUnit 5 unit tests for the game logic  (branch `test/phase2-junit`)
The "JUnit 5 unit tests; aim for meaningful coverage" Phase 2 item, over the now-headless
`amiss.service` rules unlocked by PR B.
Plan:
- [x] **DB isolation = mock the repositories (Mockito).** Surfaced this decision before coding
      (mock vs in-memory H2 vs throwaway MySQL) and chose mocking: the unit under test is the
      *rules*, not JDBC; `DB`'s constructor is hardcoded to MySQL so an in-memory DB would force a
      behaviour-risky `DB` refactor; mocking is fast/deterministic and lets CI run with no MySQL.
- [x] `pom.xml` (additive only): JUnit 5 via `junit-bom`, Mockito (`mockito-core` +
      `mockito-junit-jupiter`), pinned `maven-surefire-plugin` 3.2.5, and JaCoCo for coverage
- [x] 9 test classes / **87 tests** under `src/test/java/amiss`: `Validation`, `ActionResult`,
      and the five services. Pure logic (board-distance maths, time formatting, buy/eat/work
      decision branches) + every `catch (SQLException)` fallback, via `@Mock` repositories
- [x] `workMain`/`eatMain` orchestration tested with **real** collaborator services over mocked
      repos (Mockito can't stub `job.toString()`), mirroring the `GameServices` wiring
- [x] `src/test/resources/logback-test.xml` silences the `amiss` logger so the deliberately-thrown
      `SQLException` fallbacks don't dump stack traces into the Surefire output
- [x] Verify: `./mvnw clean test` green (87/0/0/0); `git diff --stat main` = `pom.xml` + `src/test`
      only (zero `src/main` change → behaviour preserved by construction)

### Review — Item 3 (JUnit 5 tests)
Added a JUnit 5 + Mockito suite (87 tests, 9 classes) over the `amiss.service` rules, green via
`./mvnw clean test`. The repositories are mocked so each service is exercised in isolation from
MySQL — the tests are hermetic and fast (~1s), which is exactly what the upcoming GitHub Actions
CI needs (it can run them with no database). JaCoCo reports **≈88% instruction / 82% line / 78%
branch** coverage of `amiss.service` and **100%** of `Validation`; the remaining uncovered lines
are almost entirely the symmetric one-line `catch (SQLException) { return <fallback>; }` returns and
one provably-dead branch (below). These are characterization tests: they pin the current behaviour
rather than asserting an ideal, which is the right stance for a refactor-enabling safety net.

Two real findings surfaced (documented, **not** "fixed" — that would change behaviour):
- **`TimeService.getNewTime` doesn't zero-pad single-digit minutes** — only `mins == 0` is padded
  to `"00"`, so e.g. 2h 5m renders `"2:5"`, not `"2:05"`. Locked by a test as current behaviour.
- **`TimeService.getMulti` has a dead `else if`** — the third bonus branch
  (`|oldCol-col|==3 && row!=oldRow && row,oldRow interior`) is a strict subset of the first `if`'s
  condition, so it can never execute. Noted in `lessons.md`; chasing 100% branch on `getMulti`
  isn't meaningful because of it (and the many infeasible combinations of that compound condition).

The orchestration methods (`workMain`/`eatMain`) are covered with real collaborator services over
mocked repos rather than mocked services, because `workMain` calls `job.toString()`, which Mockito
can't stub — this also gives a more faithful end-to-end exercise of the decision tree. Build impact
is additive only (test deps + Surefire pin + JaCoCo); `git diff main` touches no `src/main` file, so
the game is byte-identical. Next Phase 2 item: GitHub Actions CI to run this suite on every push/PR.

### Items 4+5 — GitHub Actions CI (KAN-14) + Flyway migrations (KAN-15), and the develop/main branching model  (2026-07-03)
Finishes ROADMAP Phase 2. Also moves the repo to a gitflow-lite model per the user:
`develop` integration branch, feature branches base off it, `main` protected.
Plan:
- [ ] Create `develop` off `main`, push. Feature branches now base off `develop`;
      PRs target `develop`; `develop` → `main` via release PR when a milestone ships
- [ ] **KAN-14 — CI** (branch `feat/kan14-github-actions-ci` off develop)
  - [ ] Fix `mvnw` execute bit in the git index (Linux runners can't `./mvnw` otherwise)
  - [ ] `.github/workflows/ci.yml`: `build` job — `./mvnw -B clean package`
        (87 tests + JaCoCo) on push to main/develop + all PRs; surefire/JaCoCo
        artifacts + coverage step-summary; `coverage-badge` job (push to main only)
        publishes SVG badges to an orphan `badges` branch (main will be protected,
        so CI can't commit badges there)
  - [ ] README: CI + coverage badges; ROADMAP tick; document the develop-based
        branching in CLAUDE.md + ROADMAP "how we work"
  - [ ] PR → develop; verify the Actions run is green on the PR
- [ ] **KAN-15 — Flyway** (branch `feat/kan15-flyway-migrations`, stacked on the
      KAN-14 branch so its PR runs the new CI; auto-retargets when the CI PR merges)
  - [ ] pom: `flyway-core`/`flyway-mysql` 11.8.2 + `flyway-maven-plugin`
  - [ ] `src/main/resources/db/migration/`: `V1__baseline_schema.sql` (4 tables),
        `V2__seed_reference_data.sql` (idempotent DELETE+INSERT of tbljobs/tblhelp)
  - [ ] `db/bootstrap.sql` replaces `db/setup.sql`: DB + users only (runtime `amiss`
        stays SELECT/INSERT/UPDATE; new `amiss_migrator` gets the DDL+DML Flyway needs)
  - [ ] `FlywayMigrator` (infrastructure) run from `GameContext` before `Jdbc`;
        `baselineOnMigrate` so existing installs keep saves; `Config` gains migrator
        creds (`AMISS_DB_MIGRATOR_USER/_PASSWORD` overrides)
  - [ ] `.github/workflows/migrations.yml`: MySQL 9 service container → bootstrap →
        `flyway:migrate` as `amiss_migrator` → assert seed counts as the runtime user
  - [ ] Docs: README/SETUP quick start (bootstrap + auto-migrate; running now needs
        JDK 17+ for Flyway 11); ROADMAP tick → Phase 2 complete
  - [ ] Verify locally: green build; a clean scratch DB built purely from migrations;
        app-start baseline against the live `amissdb` keeps saves
- [ ] **Protect `main`**: require PR + green `build` check, block force-push/deletion.
      NOTE: repo is PRIVATE — GitHub may require a paid plan; attempt and report
- [ ] JIRA: KAN-14/15 In Progress + PR-link comments (Done once the user merges)

### Review — Items 4+5
All plan items shipped (every checkbox above done except the plan-gated protection):
**PR #9** (KAN-14, `feat/kan14-github-actions-ci` → `develop`) adds the CI workflow —
verified green on the PR itself (`build` pass, 89/89 tests, coverage summary +
artifacts; the badge job correctly skips on non-main events). **PR #10** (KAN-15,
stacked on #9) moves the schema into Flyway 11 migrations applied at startup by a
dedicated `amiss_migrator` account, retires `setup.sql` for a users-only
`bootstrap.sql`, and adds a `Migrations` workflow — verified green in CI (clean
MySQL 9 container built purely from migrations, seed counts asserted as the runtime
user) **and** locally against MySQL 9.7.1: clean scratch DB from `flyway:migrate`
alone; live `amissdb` baselined with the saved player intact; second run a no-op.
Phase 2 is complete once both PRs merge (merge #9 first, delete its branch — #10
auto-retargets to `develop`).

Deviations/notes: (1) **`main` branch protection is plan-gated** — the repo is
private and GitHub returns 403 "Upgrade to GitHub Pro or make this repository
public" for both rulesets and classic protection. The ready-to-apply ruleset is
committed at `.github/rulesets/protect-main.json` with apply instructions; revisit
when the repo goes public (Phase 4 showcase) or gets Pro. (2) Running the game now
needs a JDK 17+ runtime (Flyway 11); source still targets 8. (3) The test suite is
89 tests (the board rework had grown it past the documented 87).

---

## Fix: board clock orientation + protect `develop` from deletion  (2026-07-03)
Follow-up to the board rework above: Low-Cost Housing (start) was at top-*left*
`(0,0)`, not 12 o'clock. Rotated the ring 2 cells clockwise so start sits at
top-middle `(0,2)` (12 o'clock); the turn timer stays at bottom-middle `(3,2)`
(6 o'clock, already correct — untouched). Separately, added a deletion-only
ruleset for `develop`, mirroring the existing `main` ruleset precedent.
- [x] `Board.java`: rotate the `CELLS` array (keep `STOPS`/ring-index-0 fixed
      to Low-Cost Housing); update Javadoc
- [x] Propagate the new home coordinate `(0,2)` everywhere `(0,0)` was
      hard-coded as start/reset: `LoginGUI`, `JdbcUserRepository`
      (`insertNewUser`/`resetUser`), `MainGameGUI` (stale-position snapback,
      round-end highlight, new-round reset)
- [x] `BoardTest`: updated cell/location expectations for the rotated layout
- [x] Verify: `./mvnw clean test` green (89/89); a standalone board-print
      confirms Low-Cost Housing at `(0,2)` / 12 o'clock and the timer still at
      `(3,2)` / 6 o'clock
- [x] `.github/rulesets/protect-develop.json` (deletion-only) + README update;
      attempted `gh api .../rulesets -X POST` — same 403 plan-gate as `main`
      (private repo needs Pro or public); committed ready-to-apply, as with
      `protect-main.json`

---

## Phase 3 / KAN-16 start — KAN-27 Spring Boot scaffold  (2026-07-03)
Plan approved (`.claude/plans/witty-nibbling-pumpkin.md`). Decisions: time → integer
minutes (40 min/ring-step, week 3600/4320 — Jones-notes fractional movement without
floats); 1 PR = 1 JIRA subtask; monorepo (React in `frontend/` at KAN-19); Temurin 21
installed, `release=21` everywhere.

### PR1 — `chore/kan27-multi-module-reactor`
- [x] Install Temurin 21 (winget) + verify `mvnw -v` shows 21
- [x] JIRA: KAN-16 + KAN-27 → In Progress; design comments on KAN-29/KAN-30
- [x] Commit 1: pure `git mv` → `amiss-core` (domain/application/infrastructure +
      migrations + tests) and `amiss-swing` (presentation + assets +
      application.properties + logback.xml)
- [x] Commit 2: reactor poms (parent + core + swing + coverage aggregate), `release=21`,
      slf4j 2.0.17 / logback 1.5.18 (core exposes only slf4j-api; backend is per-app),
      CI → `clean verify` + aggregate JaCoCo paths, `-pl amiss-core` for the Migrations
      workflow, scripts → `amiss-swing/target/AmissProj.jar`, README/SETUP/ARCHITECTURE
- [x] Commit 3: `scripts/find-java21.ps1` — PATH java + machine JAVA_HOME were still
      JDK 20 (jar died silently with UnsupportedClassVersionError); user-level
      JAVA_HOME now → Temurin 21; run/build scripts resolve a 21+ JDK by `release` file
- [x] Verify: `mvnw -B clean verify` green (89/89 tests, aggregate csv); Swing jar
      launches on Temurin 21 + logs `Connection Successful` against live MySQL

### PR2 — `feat/kan27-spring-boot-api-scaffold` (stacked on PR1)
- [x] `amiss-api` module: Boot 3.5.16 BOM (module-only), starters web/validation/actuator/jdbc,
      spring-boot-maven-plugin repackage (never next to shade)
- [x] `PersistenceConfig` beans (Jdbc + 4 adapters), `GameServicesFactory` (per-request
      `GameServices`; 404/500 exceptions), `GlobalExceptionHandler` (RFC 7807 ProblemDetail),
      `application.yml` (AMISS_DB_* runtime user + AMISS_DB_MIGRATOR_* for Boot's Flyway)
- [x] Core: `Jdbc` gains pooled `DataSource` mode (borrow-per-call via `withConnection`);
      legacy single-connection ctor untouched for Swing
- [x] Tests: core `JdbcPooledModeTest` (5), api context smoke (flyway off),
      `ProblemDetailContractTest` (2), `GameServicesFactoryTest` (3) → 94 + 6 green
- [x] Verify: `spring-boot:run` → `GET /actuator/health` **UP** (db UP on live MySQL,
      flyway endpoint shows baseline + V2); Swing jar regression green (new Jdbc, old mode)
- [ ] Push both branches; `gh pr create` (PR1 → develop, PR2 → PR1 branch)

### Review
KAN-27 shipped as two stacked PRs. PR1 restructures to a Maven reactor
(amiss-core / amiss-swing / amiss-coverage) on Java 21 — pure-rename commit first, then
poms/CI/scripts/docs; CI now runs `clean verify` and reads the aggregate JaCoCo csv.
PR2 adds amiss-api: Spring Boot 3.5.16 over the untouched core services — PersistenceConfig
mirrors GameContext, Jdbc gained a pooled DataSource mode for concurrent requests,
errors leave as RFC 7807 problem+json, and Boot's Flyway runs fail-fast as amiss_migrator
while the runtime pool stays the least-privilege amiss user. Verified end-to-end: 100 tests
green, health UP against live MySQL, Swing client unchanged in behaviour.
Environment gotcha worth remembering: machine JAVA_HOME/PATH still pointed at JDK 20 —
user-level JAVA_HOME now → Temurin 21 and scripts/find-java21.ps1 picks a 21+ JDK by its
`release` file (details in tasks/lessons.md). Next: PR3 (KAN-29 prep) — minutes migration
+ ActionCosts per the approved time design.

---

## Phase 3 / KAN-29 PR3 — time → integer minutes + ActionCosts  (2026-07-04)
PRs #14/#15 merged → KAN-27 Done. PR3 executes the pre-approved spec from
`.claude/plans/witty-nibbling-pumpkin.md` (branch `refactor/kan29-time-minutes-and-cost-table`).

- [x] `ActionCosts` record (application.config): work/study/relax 360, apply-job 240,
      pay-rent 120, eat 60, shop 0, travel-per-step 40, enter-building 120,
      week 3600 / 4320 fed; `Config.actionCosts()` resolves each value
      (env `AMISS_COSTS_*_MINUTES` > `costs.*-minutes` props > defaults)
- [x] `TimeService`: `TimeSpend spendMinutes(int)` (record: remainingMinutes / rejected /
      weekOver) replaces `getNewTime`; static `format(int)` → "38h 30m"/"72h"/"45m"/"0h";
      `readClock()` for display-only sites; persistence failure now = rejected spend
- [x] `StatsService`/`GameServices` carry `ActionCosts` (old ctors delegate to defaults);
      `GameContext.servicesFor` wires `Config.actionCosts()` for Swing
- [x] Flyway `V3__time_to_minutes.sql` — `time*60`, default 4320 (ONE-WAY); seeds/resets
      4320 in `JdbcUserRepository` + `LoginGUI`
- [x] Swing sweep: 13 timer-display sites → `readClock()`; movement, study, enroll,
      apply, rent, shop, relax, new-round handlers → spendMinutes + rejected/weekOver
      flags. Latent-bug fix: EmploymentGUI apply and RentOfficeGUI pay proceeded
      (uncharged) when time was short — both now stop with "Not Enough Time"
- [x] Tests 100 → 109: TimeServiceTest rewritten in minutes (+format/readClock),
      ActionCostsTest pins the cost table, ConfigActionCostsTest pins precedence;
      orchestration "66h"/"71h" asserts survive on minute values
- [x] Verify: `mvnw -B clean verify` green; live MySQL97 — V3 applied at startup
      (save 595h → 35700 min, column default 720 → 4320, history v3 success),
      `Connection Successful`; zero generated `initComponents()`/layout changes vs develop

### Review — PR3
The clock is now integer minutes end-to-end: one `ActionCosts` table prices every action
(config-overridable without a rebuild in both clients), `TimeSpend` flags replace the
"Not Enough Time"/"0h" string sentinels, and the V3 migration converted the live DB in
place on first launch. Behaviour is byte-identical for default costs except two latent
Swing bugs the rejected-flag guards fixed (documented above). Next: PR4
`feat/kan29-turn-service-and-end-week` — TurnService extraction + POST end-week +
Spring `CostsProperties` (KAN-29 Done when merged).

## Phase 3 / KAN-29 PR4 — TurnService + end-week endpoint  (2026-07-04)
Branch `feat/kan29-turn-service-and-end-week`, stacked on PR3.

- [x] `TurnService.endWeek()` → `WeekSummary` (refuses while time remains; settles the
      closed round: 4th-round unpaid rent → debt+80; consumes one stored food for the
      fed 4320 / unfed 3600 budget; clock+position+round reset; flags rent due entering
      every 4th round; **no wages at rollover**). Unified rent rule = documented
      behaviour change: debt now charged exactly once at end-week (old code charged on
      re-login, repeatably). Also fixed the stale `btnArr[0][0]` new-round highlight.
- [x] Swing `btnNewRound` delegates; the MainGameGUI constructor keeps only the
      informational rent messages
- [x] API: `CostsProperties` (`amiss.costs.*` → `ActionCosts` bean, defaults when
      absent), `GET /api/players/{u}` (PlayerStateDto from `Board`, never
      `OpenLocation`), `POST /api/players/{u}/end-week` (summary + fresh state; 409
      `urn:amiss:week-not-over` while time remains), 404 unknown player
- [x] Reactor-wide compiler `-parameters` (Spring MVC @PathVariable name resolution —
      this build imports the Boot BOM without the Boot parent)
- [x] Tests 109 → 122 (TurnService 7, costs binding 3, controller 3); all green
- [x] Live MySQL97 verify: health UP; disposable `kan29test` player — GET weekOver
      "0h" → end-week 200 (round 2, 3600 min, home) persisted in DB → second POST 409
      → 404 for unknown; test rows deleted after

### Review — PR4
The week rollover is now one tested rule shared by both clients, and the API exposes the
full KAN-29 turn lifecycle with costs-from-config. KAN-29 → Done once PR #17 + PR4 merge.
Next: PR5 `feat/kan30-board-and-move` (TravelService, GET /api/board, POST move).
