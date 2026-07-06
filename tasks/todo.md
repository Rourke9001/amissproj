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

## Phase 3 / KAN-30 PR5 — TravelService + board & move endpoints  (2026-07-04)
Branch `feat/kan30-board-and-move`, stacked on PR4.

- [x] `TravelService.moveTo(Location)` → `MoveResult` (OK / INSUFFICIENT_TIME /
      WEEK_OVER): ringDistance × travel-per-step + enter-building; position persisted
      only on success; moving to the current stop charges entry only; a walk landing
      exactly on 0 ends the week charged-but-unmoved (Swing parity); stale saved cell
      measures from home. `Board.ringIndexOf(Location)` added.
- [x] `MainGameGUI` click handler delegates to `TravelService`
- [x] API: `GET /api/board` (13 stops + minutesPerStep/enterBuildingMinutes metadata,
      from `Board` + the `ActionCosts` bean), `POST /api/players/{u}/move {"target"}` →
      steps/minutesCharged/fresh state; 400 `urn:amiss:unknown-location`,
      409 `urn:amiss:insufficient-time` / `urn:amiss:week-over`. `LocationDto`
      promoted to a shared top-level DTO.
- [x] Tests 122 → 135: TravelService ×8 (both ring directions — 1 step cw/acw 160 min,
      cross-town 6×40+120=360 — entry-only, rejections persist nothing, exact-zero,
      stale clamp), board contract ×1, move contract ×4
- [x] Live MySQL97 verify: board JSON (13 stops), cross-town move 360 min → "66h" +
      position (3,3) persisted, 1-step back 160 min → "63h 20m", unknown target 400,
      insufficient-time & week-over 409s, position only changed on success; test rows
      deleted

### Review — PR5
Movement now lives in one tested rule used by both clients, and the REST surface for
KAN-30 is complete (board model + move with full problem-detail contracts). KAN-30 →
Done when the stack (#17 → #18 → PR5) merges. Next: KAN-28 (player state DTO
enrichment + highscores), then KAN-31 (bank/rent), KAN-32 (jobs/university/food) to
close KAN-16.

---

## Phase 3 / KAN-28 PR6 — enriched state DTO + high scores  (2026-07-04)
Branch `feat/kan28-state-highscores`, stacked on PR5. First of the four-PR chain from
`.claude/plans/next-up-new-session-tranquil-candy.md` that closes KAN-16. (Bookkeeping for this
PR was deferred and is written retroactively as part of PR9's closing bookkeeping — see the
KAN-32-api PR9 section below.)

- [x] `JobService.getJob()` made public (was assembler-only private access);
      `getClothingLevel()` added so the assembler can read both without new core surface area
- [x] `PlayerStateDto` gains `foodWeeks, clothing, job, stats, goals` (new `JobDto`, `StatsDto`,
      `GoalsDto`, `GoalDto` records); `PlayerStateAssembler` populates them from
      `services.{food,jobs,education}()` + `Validation.parseIntOrDefault` for the
      String-returning happiness/work accessors
- [x] `GET /api/highscores` (new `HighscoresController`, injects `UserRepository` directly —
      no per-player services needed) — ranks `UserRepository.highScores()` rows 1-based
- [x] Fixed the `baordv2.png` filename typo: `git mv` → `docs/design/boardv2.png`
- [x] Tests: `PlayerStateAssemblerTest` (real `GameServices` over mocked ports),
      `HighscoresControllerTest`, `PlayerControllerTest.dto()` updated for the new shape
- [x] Verify: full reactor green; live smoke against MySQL97 confirmed the enriched
      `GET /api/players/{u}` shape and ranked `GET /api/highscores`

### Review — PR6
`PlayerStateDto` is now the single call the future React client needs for a full game-state
screen instead of stitching together five service calls itself, and the high-score board is
finally HTTP-callable (`HighScoreGUI`'s `UserRepository.highScores()` query, ranked). Verified
end-to-end against live MySQL. Next: PR7 (KAN-31 bank/rent), PR8 (KAN-32-core typed outcomes),
PR9 (KAN-32-api endpoints, this file's most recent section) to close KAN-16.

---

## Phase 3 / KAN-31 PR7 — bank + rent  (2026-07-04)
Branch `feat/kan31-bank-rent`, stacked on PR6. Bank is greenfield (no bank column existed
before this PR). (Bookkeeping deferred — written retroactively with PR9.)

- [x] `V4__bank_balance.sql` — `tbluser.bank INT NOT NULL DEFAULT 0`; `insertNewUser` switched
      from a positional `INSERT` to an explicit column list so it survives future columns
      (a positional insert would have silently broken); `resetUser` zeroes `bank`
- [x] `UserRepository` port + adapter: `getBank`, `depositToBank`/`withdrawFromBank` — each a
      single atomic conditional `UPDATE ... WHERE cash/bank >= ?`, so concurrent requests for
      the same player can't lose an update
- [x] New `BankService` (`deposit`/`withdraw` → typed `BankTransaction`) and `RentService`
      (`payRent()` → typed `RentPayment`, a straight extraction of
      `RentOfficeGUI.btnRentActionPerformed` with a defensive not-due guard the API needs but
      Swing never reaches); `TravelService.currentLocation()` added to back the new
      `LocationGuard` (every mutating action endpoint requires standing at the right building)
- [x] `PlayerStateDto` gains `bank`; new `BankController` (`/bank/deposit`, `/bank/withdraw`)
      and `RentController` (`/rent/pay`); `RentOfficeGUI` delegates to `RentService`, rendering
      byte-identical strings
- [x] New exceptions: `InvalidAmountException` (400), `InsufficientFundsException`,
      `RentNotDueException`, `WrongLocationException` (409 each) + handler rows
- [x] Tests: `BankServiceTest`, `RentServiceTest` (real collaborators over mocked ports),
      `TravelServiceTest` `currentLocation` cases, `BankControllerTest`, `RentControllerTest`
      (every status + problem envelope), assembler/dto() updates for `bank`
- [x] Verify: full reactor green; live smoke confirmed V4 applied at boot, deposit/withdraw
      balance updates, over-withdraw 409, wrong-location 409, rent-not-due 409 on a non-4th round

### Review — PR7
Bank is minimal and backend-only as scoped (no interest — that's the KAN-5 economy epic); the
atomic conditional-update pattern means deposit/withdraw can't lose a concurrent update without
needing a transaction. `LocationGuard` introduced here becomes the standard every later mutating
endpoint (PR9's jobs/university/food controllers) reuses. Verified end-to-end against live
MySQL. Next: PR8 (KAN-32-core typed action outcomes).

---

## Phase 3 / KAN-32-core PR8 — typed action outcomes for work/apply/study/eat/shop  (2026-07-04)
Branch `feat/kan32-core-actions`, stacked on PR7. Moves the work/apply/study/eat/groceries/
clothes rules that used to live only in Swing button handlers into typed core methods, so Swing
and the REST API (PR9) share one rule source. (Bookkeeping deferred — written retroactively
with PR9.)

- [x] New typed outcome records: `WorkOutcome`, `EatOutcome`, `PurchaseOutcome` (shared by
      groceries + clothes), `ApplyOutcome`, `EnrollOutcome`, `StudyOutcome`
- [x] `StatsService.work()/eat(price)/buyGroceries(price,weeks)/buyClothes(level,price)`;
      `workMain()`/`eatMain()` become thin formatters over them, rebuilding today's exact
      strings — all pre-existing message-pinning tests stayed green **unmodified**, proving
      byte-identical delegation
- [x] `JobService.apply(jobName)` — ctor gains `TimeService`/`ActionCosts` (rippled into
      `GameServices` wiring + every `JobServiceTest` construction); unknown-job checked first,
      before any time is charged
- [x] New `UniversityService` (`enroll()`/`study()`, mirrors `UniversityGUI`'s handlers exactly;
      constants `ENROLL_FEE=50`, `STUDIES_PER_DEGREE=10`, `MAX_EDUCATION=8`, the 8 `DEGREES`
      names); `GameServices` exposes `university()`
- [x] New domain catalogs `FastFoodItem`, `FoodPack`, `ClothingItem` (`amiss.domain.model`) —
      one canonical price/weeks/level source, replacing hardcoded Swing price switches
- [x] **Deliberate bug fix (user-approved):** `ClothesStoreGUI` called `job.setClothes()`
      *before* checking weekOver/cash, so a failed purchase still upgraded the player's clothes
      for free. `StatsService.buyClothes()` now validates and charges first; pinned with a
      regression test
- [x] Tests: `UniversityServiceTest`, `CatalogsTest`, expanded `JobServiceTest` (apply matrix)
      and `StatsServiceOrchestrationTest` (work/eat/buyGroceries/buyClothes matrices incl. the
      clothes-bug regression) — 135 → 166 core tests, all green
- [x] Verify: full reactor green; live-smoke-tested against MySQL97 (apply/work/eat/enroll/
      study/buyClothes all confirmed correct against a live save, including the clothes fix);
      `initComponents()` byte-identical per the lessons.md grep

### Review — PR8
The single-source-of-truth goal is met: every action rule Swing's button handlers implemented
now has a typed core method Swing delegates to, with the message-pinning tests proving the
delegation is byte-identical in normal play. The one deliberate behaviour change (the clothes
free-upgrade bug) is fixed and regression-tested — see `tasks/lessons.md`. Next: PR9 puts the
REST face on these rules (KAN-32-api) and does the deferred closing bookkeeping for this whole
four-PR chain.

---

## Phase 3 / KAN-32-api PR9 — employment/university/food endpoints + close out KAN-16  (2026-07-05)
Branch `feat/kan32-api-endpoints`, stacked on PR8 (`develop` tip after PR8 merged). Last PR of
the four-PR chain; also performs the deferred closing bookkeeping for PR6/PR7/PR8 above, since
none of those PRs' sessions got to it at the time.

- [x] `JobRepository.listAll()` port + adapter addition (new `JobListing` domain record) — the
      one missing read needed for `GET /api/jobs`; no other implementers, additive-only
- [x] New `EmploymentController` (`GET /api/jobs`, `POST .../jobs/apply`, `POST .../work` — the
      work location guard is inline rather than `LocationGuard` since a job's required location
      varies per job, read from `tbljobs.location`), `UniversityController` (`GET /api/courses`,
      `POST .../enroll`, `POST .../study`), `FoodController` (`GET /api/food`, `POST .../eat`,
      `POST .../groceries`, `POST .../clothes` — the clothes endpoint is the KAN-16 scope
      addition per locked decision 8)
- [x] 7 new exceptions (`UnknownJobException`, `UnknownItemException`, `NoJobException`,
      `UnderdressedException`, `NotEnrolledException`, `AlreadyEnrolledException`,
      `EducationCompleteException`) + `GlobalExceptionHandler` rows; one overload on
      `WrongLocationException(String required, Location actual)` for the free-text job-location
      guard
- [x] 17 new DTOs across the three controllers' request/response shapes (see the PR body for
      the full list)
- [x] Tests: `EmploymentControllerTest` (14), `UniversityControllerTest` (14),
      `FoodControllerTest` (16) — pinned `@WebMvcTest` slices, every endpoint × every status;
      amiss-api 44 → 79(+ pre-existing) tests, reactor total 245
- [x] Verify: full reactor green (245 tests); live smoke against MySQL97 — the entire loop
      (move → apply → work → eat → enroll → study → groceries → clothes) exercised against a
      disposable player, every wrong-location/already-enrolled/unknown-item error envelope
      confirmed, `studiesRemaining` derivation checked against real `prog` values
- [x] `tasks/todo.md` — this section plus the missing PR6/PR7/PR8 sections above
- [x] `ROADMAP.md` — ticked "Extract the game logic into a Spring Boot REST API"
- [x] JIRA — KAN-28/KAN-31/KAN-32 → Done; KAN-16 → Done with a comment on the clothes-shop
      scope addition and auth deferring to KAN-36
- [x] `tasks/lessons.md` — the clothes-bug + fix, and the free-text wrong-location guard lesson

### Review — PR9
KAN-16 ("Spring Boot REST API over the game services") is functionally complete: every action
in its acceptance (load player, move, work, study, shop, pay-rent) is now HTTP-callable, with
login/auth explicitly deferred to KAN-36. The job-location guard needed a small design decision
not covered by the existing `LocationGuard` helper — a job's location is a free-text
`tbljobs.location` value that varies per job, not a fixed board stop — solved with a `String`
overload on `WrongLocationException` rather than adding a domain-level reverse lookup from
display name back to `Location`, keeping the change scoped to the API layer. `studiesRemaining`
on `StudyResponse` is a derived convenience field (not present in the core `StudyOutcome`):
`0` when a study session just completed the degree, otherwise
`STUDIES_PER_DEGREE - progress + 1`, verified against `UniversityService.study()`'s `prog`
arithmetic both in unit tests and against the live DB. Phase 3's Spring Boot REST API work is
now complete (JPA/Hibernate, Spring Security auth, the React SPA, OpenAPI, and Docker remain as
separate, later ROADMAP items).

---

## Phase 3 / KAN-17 (JPA) + KAN-18 (Security) — six stacked PRs  (2026-07-05)
Orchestrated session: implementation delegated to cheaper subagents; planning, diff review and
final verification stay here. Chain off `develop`, each PR stacked on the previous. Design
decisions locked up front:
- JPA lives in **amiss-api** (core stays Boot-free per the reactor rule); JDBC adapters stay in
  core for Swing — KAN-34 says keep both wired.
- Entities map the Flyway schema **as-is with natural keys** (`name`/`job`/`topic`); no
  surrogate IDs (would be a schema change; `ddl-auto=validate` means Flyway owns the schema).
- KAN-17's "map SQLException to a tech-neutral exception" ships as its own mechanical PR
  **before** the KAN-34 swap, so the swap PR's diff leaves the service tests untouched (the
  acceptance proof that the ports held).
- Bank deposit/withdraw stay **atomic conditional UPDATEs** (`@Modifying` JPQL mirroring the
  JDBC SQL 1:1) — never load-modify-save; all setter-style writes mirror their JDBC SQL.
- JWT via **spring-boot-starter-oauth2-resource-server** (HS256 secret from `AMISS_JWT_SECRET`),
  no third-party jjwt. Login reuses core `PasswordHasher.matches` + legacy-plaintext rehash
  through the port — one auth rule source shared with Swing (why there's no UserDetailsService).
- KAN-37 keeps `{username}` in the path (shipped API shape) and 403s on principal mismatch.

- [x] PR A `feat/kan33-jpa-entities` — starter-data-jpa; User/UserStats(@MapsId 1:1)/Job/Help
      entities; `ddl-auto=validate`, `open-in-view=false`; context-smoke test kept DB-free;
      live boot validate green on MySQL97 *(PR #26; 245 tests green, health UP, no
      SchemaManagementException; note: no "goals" table exists — 4 real tables mapped)*
- [x] PR B `refactor/kan17-persistence-exception` — unchecked `PersistenceFailureException`
      replaces `throws SQLException` on the 4 ports (Jdbc translates once at the boundary);
      services/Swing/API/tests swapped mechanically; api-local wrapper class deleted;
      ARCHITECTURE.md leak note resolved *(PR #27; 245 tests unchanged, leak audit clean,
      live health+highscores smoke green; added message-only ctor after review)*
- [x] PR C `feat/kan34-spring-data-ports` — Spring Data repos + thin adapters implement the
      ports; PersistenceConfig swaps beans (Jdbc bean gone from the API); core untouched —
      245 tests pass unmodified + 53 new adapter tests = 298; bank ops stay atomic
      @Modifying JPQL; live parity smoke green *(PR #28; fallback defaults cross-checked
      against JdbcUserRepository line-by-line)*
- [x] PR D `feat/kan35-testcontainers` — failsafe + Testcontainers `mysql:9` ITs (Flyway
      V1→V4 from scratch + ddl-validate = CI drift check; 24 ITs: port CRUD, BCrypt
      round-trip, atomic bank ops, seeded refs, FK cascade; NOT_SUPPORTED propagation to
      mirror prod one-call-one-tx); `disabledWithoutDocker`; CI uploads failsafe reports
      *(PR #29; ITs caught a real KAN-34 defect — save() deferred the INSERT past the
      translation boundary → raw DataIntegrityViolationException; fixed with saveAndFlush
      on the KAN-34 branch (76b75da) and the IT re-pinned to the contract. Local run:
      298 unit + 24 IT green with Docker Desktop)*
- [x] PR E `feat/kan36-auth-jwt` — register (Validation rules, 409 taken) + login → short-lived
      HS256 JWT; legacy rehash parity; game endpoints stay permitAll this PR; problem+json 401
      entry point; slices import SecurityConfig *(branch `feat/kan36-auth-jwt` off
      `feat/kan35-testcontainers`, committed locally, not pushed/PR'd yet. New
      `amiss.api.security` package (`SecurityConfig` — stateless HS256 resource-server
      chain, `GET /api/auth/me` authenticated, everything else interim `permitAll()`, no
      `UserDetailsService`/`PasswordEncoder` bean by design; `AuthService`/`AuthResult`);
      `AuthController` (`/api/auth/register|login|me`) + 5 DTOs; 3 new exceptions
      (`InvalidRegistrationException` 400, `UsernameTakenException` 409,
      `InvalidCredentialsException` 401) + handler rows; `application.yml`
      `amiss.security.jwt.secret/ttl` (dev-only default ≥32 chars, `AMISS_JWT_SECRET`
      override); every existing `@WebMvcTest` slice (Board/Highscores/Player/Bank/Rent/
      Employment/University/Food/ProblemDetailContract) gained `@Import(SecurityConfig
      .class)` to survive Boot's default-lockdown trap, unchanged otherwise.
      `MySqlITSupport.MYSQL`/the class widened to `public` so the new
      `AuthRoundTripIT`/`AuthRoundTripSupport` (`@SpringBootTest(RANDOM_PORT)`) shares the
      exact same Testcontainers `mysql:9` container instead of starting a second one.
      Tests: `AuthServiceTest` (9, mocked ports + a real HS256 encoder/decoder pair),
      `AuthControllerTest` (7, `@WebMvcTest` + `jwt()` post-processor), `AuthRoundTripIT`
      (5, full stack against the shared container). Full `mvnw -B clean verify`: 314 unit
      (was 298, +16) + 29 IT (was 24, +5), all green. Live smoke against MySQL97 (root):
      register→201, login→200+token, `/api/auth/me` with Bearer→200, without→401
      `urn:amiss:unauthenticated`, wrong password→401 `urn:amiss:invalid-credentials`,
      `GET /api/board` still 200 unauthenticated (no premature lockdown); `kan36test` rows
      deleted (FK cascade took the stats row with it), server killed, port 8080
      confirmed free. `git diff --stat feat/kan35-testcontainers` touches only
      `amiss-api/**` (+ this file))*
- [x] PR F `feat/kan37-route-protection` — lock `/api/**` (permit register/login/
      highscores/actuator-health/error/OPTIONS-preflight; all else authenticated);
      `PlayerScopeFilter` = one central IDOR guard on `/api/players/{username}/**`
      (fail-closed strict equals, AccessDeniedException → shared 403 problem+json
      `urn:amiss:forbidden`); CORS from `amiss.cors.allowed-origins` (Vite dev default);
      actuator exposure → health only, show-details never; 64 existing web-slice tests
      authenticated via `jwt()`; new SecurityRulesTest (8) + SecurityLockdownIT (6)
      *(PR #31; 322 unit + 35 IT green; live smoke: anon 401, cross-player 403 read+write
      with DB row unchanged, own 200, highscores/health public, CORS allow/deny)*
- [x] Bookkeeping: JIRA — KAN-17/18 + all five subtasks In Progress with PR-link comments
      (→ Done as PRs merge); ROADMAP JPA + Security items ticked (in PR F); README/SETUP
      document AMISS_JWT_SECRET + AMISS_CORS_ALLOWED_ORIGINS; lessons.md updated (JPA
      deferred-flush, @DataJpaTest tx trap, stale-target after branch switch, Nimbus
      RS256 default, @WebMvcTest security-slice traps, Testcontainers MySQL 9 my.cnf)

### Review — KAN-17 + KAN-18 chain (PRs #26 → #31)
KAN-17 and KAN-18 shipped as a six-PR stacked chain off develop, implemented by cheaper
subagents against locked handoff packets with plan/diff-review/verification kept in the
orchestrating session. Merge order: #26 (KAN-33 entities) → #27 (KAN-17 port-exception
refactor) → #28 (KAN-34 Spring Data swap) → #29 (KAN-35 Testcontainers) → #30 (KAN-36
auth+JWT) → #31 (KAN-37 lockdown); each PR auto-retargets as its base merges. Final
state: 322 unit tests + 35 Testcontainers ITs, all green; the API runs entirely on JPA
(core JDBC adapters remain for Swing, whose behaviour is untouched); every /api/** route
outside register/login/highscores/health requires a Bearer token and players can only
touch their own state. Highlights worth remembering: the ports absorbed the whole
JDBC→JPA swap with zero core changes (the clean-architecture bet paid off measurably —
245 pre-existing tests passed unmodified through the swap PR); the new ITs immediately
caught a real adapter bug (deferred-flush exception escaping translation) before any
merge; and the API and Swing share one credential rule through PasswordHasher, so the
legacy-plaintext upgrade works identically over HTTP and desktop. Next up per ROADMAP
Phase 3: KAN-19 React SPA (KAN-38 scaffold onwards), then KAN-20 (OpenAPI + Docker).

---

## Phase 3 / KAN-19 start — KAN-38 React SPA scaffold  (2026-07-06)
Branch `feat/kan38-react-spa-scaffold` off `develop`. First PR of the KAN-19 SPA chain
(KAN-38 scaffold → KAN-39 login → KAN-40 board → KAN-41 HUD → KAN-42..44 buildings →
KAN-45 highscores/win). Locked decisions: monorepo (`frontend/`), React Query for server
state, 1 PR = 1 subtask; implementation delegated to a cheaper subagent, diff review +
verification here. Note: the API has **no refresh-token endpoint** (KAN-36 mints 60-min
access tokens only), so "refresh handling" = proactive expiry check + reactive 401 →
clear token + redirect to /login; documented in the PR.

- [x] `frontend/`: Vite + React 19 + TypeScript scaffold — **pinned `create-vite@7`**
      (the `@latest` v9 template now ships Vite 8 + oxlint + TS 6.0 and no ESLint;
      see lessons.md), plus react-router 7.18, @tanstack/react-query 5.101,
      Prettier 3.9 + eslint-config-prettier; the 8 planned npm scripts
- [x] Typed API client (hand-rolled until KAN-20 OpenAPI): `api/http.ts` fetch wrapper —
      `/api` base, Bearer header from token store, RFC 7807 problem+json → typed
      `ApiError`, 401-with-token → onUnauthorized callback (a 401 on a token-less
      request, e.g. failed login, deliberately does NOT fire it); `api/types.ts`
      mirrors the Java DTOs; `api/auth.ts` (register/login/me), `api/highscores.ts`
- [x] JWT plumbing: `auth/tokenStore.ts` (localStorage `amiss.auth.v1`, expiresAt from
      `expiresInSeconds`, 30s clock-skew, corrupt/expired → removed),
      `auth/AuthContext.tsx` (login/logout, synchronous hydrate + background
      `/api/auth/me` validation — only an ApiError logs out, so a down API doesn't
      nuke the session), `routes/RequireAuth.tsx` guard
- [x] Shell pages: `/` HomePage renders live `GET /api/highscores` via React Query
      (the KAN-38 acceptance call), `/login` placeholder (form lands with KAN-39),
      `/game` placeholder behind RequireAuth (proves the guard)
- [x] Vite dev proxy `/api` → `http://localhost:8080`; `npm run build` → `frontend/dist`
      (Docker-ready for KAN-20)
- [x] Vitest 4 (jsdom): 11 tests — tokenStore round-trip/expiry/skew/corrupt + http
      200-parse/ApiError-mapping/Bearer-attach/401-handler matrix
- [x] CI: new `frontend` job in `.github/workflows/ci.yml` (setup-node 24 + npm cache →
      `npm ci` → lint → format:check → typecheck → test → build); `build` job untouched
      (stays the required check)
- [x] Root `.gitignore`: `node_modules/`, `frontend/dist/`
- [x] Docs: README (stack row, structure, CI line), SETUP.md (Node prerequisite +
      frontend dev loop), project-specific `frontend/README.md`
- [x] JIRA: KAN-19 + KAN-38 → In Progress (done at session start); PR-link comment on
      KAN-38; → Done when the PR merges
- [x] Verify: lint/format:check/typecheck/test (11/11)/build all re-run green by the
      orchestrator; live verify against MySQL97 — API UP, Vite proxy returns the real
      highscores JSON (`[{"rank":1,"username":"rourke","round":1}]`), problem+json 401s
      pass through the proxy, and in a real Chrome session the shell page renders the
      highscores table and clicking Game unauthenticated redirects to `/login`

---

## Phase 3 / KAN-19 chain — KAN-39 → KAN-44, six stacked PRs  (2026-07-06)
Continues the SPA chain off the merged KAN-38 scaffold (PR #34 → develop). Orchestrated
session: implementation by cheaper subagents against locked handoff packets; architecture,
diff review and live browser verification stay here. **STOP before KAN-45** (highscores/win
is on hold for Rourke's design input — hold comment on the ticket).

**Shared architecture (locked, from the KAN-40/41 design comments + command):**
- One game screen at `/game`: the boardv2 bitmap (copied to
  `frontend/public/assets/board/board.png`) with 13 DOM/CSS hotspots positioned from
  `GET /api/board`'s `row`/`col` (uniform 5×4 grid → cell = 20% × 25%; data-driven, no
  hardcoded layout). The centre (cols 1–3 × rows 1–2) is the Jones-style dynamic panel:
  current location's storefront image + HUD + that location's action panel. SVG only for
  the token/path animation. No canvas engine.
- Per-location **asset manifest** (`src/assets/manifest.ts`): location id →
  `/assets/locations/<kebab-id>.png` (+ board + clock keys). Placeholders are real PNGs
  generated by `scripts/gen-frontend-placeholders.ps1` (crops each stop's tile out of
  boardv2.png, 16:9); Rourke's final art drops in by replacing files — zero code changes.
- TanStack Query owns server state. Every mutation response carries `PlayerStateDto`;
  `setQueryData(['player', username], state)` after each action so the HUD updates
  without reload. Board catalog queries (`board`, `jobs`, `courses`, `food`) are static
  and cached indefinitely.
- Building panels: registry `locationId → PanelComponent` filled over KAN-42/43/44;
  missing entry renders storefront + "opens with the KAN-5 economy epic" so every stop
  opens something.
- Errors: RFC 7807 `ApiError` already typed; charged-rejections (200 + `hired:false` /
  `ate:false`) render as outcomes, not errors. New test deps pinned by major:
  `@testing-library/react@16`, `@testing-library/user-event@14`,
  `@testing-library/jest-dom@6`; no MSW — `vi.mock` the api modules.

**PR chain (1 PR = 1 subtask, each stacked on the previous):**
- [x] **PR A — KAN-39** `feat/kan39-login-registration` (off develop): login + register
      forms with client-side validation mirroring core `Validation`
      (username `^[A-Za-z0-9_]{1,50}$`, password ≥ 4); ApiError → inline errors
      (401 invalid-credentials, 409 username-taken, 400 invalid-registration);
      register auto-logs-in; success lands on `state.from ?? /game`. Component tests.
- [x] **PR B — KAN-40** `feat/kan40-board-screen`: board asset + manifest + placeholder
      generator; `api/board.ts` + `api/player.ts`; hotspot grid + SVG token; click stop →
      cost preview (ring distance × minutesPerStep + enterBuildingMinutes) → confirm →
      `POST move` → cache update; insufficient-time / week-over as notices; centre panel
      previews the current location's storefront.
- [ ] **PR C — KAN-41** `feat/kan41-hud-week-flow`: centre-panel HUD (cash, bank, time,
      round, job, education/progress, clothing, food weeks, happiness + goal progress),
      notification feed for action results, clock in the timer cell `(3,2)`, End Week
      action + end-of-week modal from `EndWeekResponse` (fed/rentDue/debtCharged), rent-due
      warning.
- [ ] **PR D — KAN-42** `feat/kan42-bank-rent`: panel registry; Bank panel
      (deposit/withdraw, client amount validation + invalid-amount/insufficient-funds
      envelopes inline); Rent Office panel (rent due state, pay, rent-not-due).
- [ ] **PR E — KAN-43** `feat/kan43-employment-university`: Employment Office (job list
      with education/clothing requirement badges, met/unmet state, current job highlight,
      apply) — requirement-not-met blocked client-side AND surfaced from the server
      envelope; Hi-Tech U (courses, enroll, study, progress, which jobs each level
      unlocks); Work action on the stop matching `job.location` (wages toast).
- [ ] **PR F — KAN-44** `feat/kan44-food-shops`: shared store panel; Monolith Burgers
      (menu, eat → fed indicator), Black's Market (packs, groceries), QT Clothing
      (clothes purchase); small backend addition `GET /api/clothes` catalog (ClothingItem
      has no catalog endpoint — mirror of `GET /api/food`, tested); Home panel; "opens
      later" panels for Pawn Shop / Z-Mart / Socket City / Le Security Apartments;
      acceptance: every stop opens something.

**Per-PR loop:** JIRA subtask → In Progress; subagent implements from a full handoff
packet; orchestrator reviews the diff, re-runs lint/format/typecheck/test/build, verifies
the screen live in Chrome against the running API (MySQL97 + `spring-boot:run` + Vite
proxy), commits/pushes, opens the stacked PR (`gh pr create`), PR-link comment on the
ticket; → Done as PRs merge.

### Review
*(pending — filled in as the chain ships)*

---

### Review — KAN-38 (React SPA scaffold)
KAN-38 shipped on `feat/kan38-react-spa-scaffold` (implementation by a Sonnet subagent
against a locked handoff packet; scaffold decision, shared files, diff review and all
verification kept here). The one real decision this PR surfaced: `npm create vite@latest`
now scaffolds the brand-new toolchain (Vite 8, oxlint instead of ESLint, TypeScript 6.0)
— the subagent hit its stop condition and reported instead of improvising, and the
orchestrator pinned `create-vite@7` (Vite 7.3 + TS 5.8 + ESLint 9 flat config) because
the ticket names ESLint/Prettier explicitly and that's the ecosystem-proven combo;
revisit the newer template when the ecosystem catches up (a scaffold this thin upgrades
trivially). "JWT refresh handling" was scoped to reality: the API issues no refresh
tokens, so expiry (30s skew) treats the session as absent and any authenticated 401
clears it — documented in code where the constraint lives. The acceptance criterion was
verified end-to-end in a real browser: dev-server shell page renders live
`GET /api/highscores` data through the `/api` proxy, and the RequireAuth guard bounces
`/game` to `/login`. Next: KAN-39 (login/register screen over this plumbing).
