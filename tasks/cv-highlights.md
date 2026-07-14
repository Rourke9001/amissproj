# CV Highlights — AmissProj

Ready-made, résumé-grade bullets for each shipped roadmap item, plus an interview
**talking point** that explains *why it matters* (skewed toward banking-sector Java:
security, correctness, maintainability, reproducible builds).

Lift a few bullets straight onto a CV, or use the talking points to walk an interviewer
through a real decision. One section per phase; newest item at the bottom of each.

> Framing line for the top of a CV / portfolio:
> *Took a dead high-school NetBeans desktop game and re-engineered it into a secured,
> tested, Maven-built Java application — incrementally, on feature branches with PRs,
> driving toward a Spring Boot + web full-stack rebuild.*

---

## Phase 0 — Revival & version control

- Reverse-engineered an undocumented, non-building legacy NetBeans/Swing + MySQL
  application; produced a `SETUP.md` runbook so it builds and runs on a modern toolchain
  (JDK 20, MySQL 9) from a clean machine.
- Diagnosed a hard failure where the bundled **MySQL Connector/J 5.1.22 (2012)** could not
  authenticate to MySQL 8/9 (which dropped `mysql_native_password`); upgraded to
  Connector/J 9.7 and migrated `DB.java` to the modern `com.mysql.cj.jdbc.Driver`.
- Reconstructed the lost MySQL schema and reference data from inline SQL in the code into
  a single idempotent, re-runnable `db/setup.sql`.
- Replaced the IDE-locked Ant build with a scripted `javac`/`jar` build, then put the
  whole project under Git with a clean history and pushed it to GitHub.

**Talking point:** *"The driver upgrade is a good story about understanding the stack
end-to-end — the bug surfaced as 'access denied', but the root cause was an auth-plugin
change three layers down in the database. I fixed the cause, not the symptom."*

---

## Phase 1 — Backend hygiene & security

- **Eliminated pervasive SQL injection**: rewrote all 36 data-access queries to use bound
  `PreparedStatement` parameters behind a small JdbcTemplate-style `DB` helper — no string
  concatenation reaches the database.
- **Hardened credential storage**: replaced plaintext passwords with salted **BCrypt**
  hashes (`PasswordHasher`), with transparent on-login re-hashing so existing accounts
  upgrade automatically and no password is ever stored or logged in clear.
- **Closed JDBC resource leaks**: every `ResultSet`/`Statement` is owned by try-with-
  resources inside `DB`, so cursors can't leak under error paths.
- **Applied least privilege**: the app connects as a dedicated `amiss` MySQL user granted
  only `SELECT/INSERT/UPDATE` (no DDL/DELETE) instead of `root` — verified at runtime that
  destructive operations are correctly denied.
- **Externalised configuration**: moved DB connection settings out of source into
  `application.properties` with `AMISS_DB_*` environment-variable overrides (`Config`),
  so credentials aren't baked into the build.
- **Added real observability and input validation**: structured logging via SLF4J +
  Logback (console + rolling file), replacing `System.out`; centralised username/password
  validation (`Validation`).

**Talking point:** *"This is the checklist an interviewer looks for first: parameterised
queries, hashed credentials with a safe migration path, least-privilege DB access,
secrets out of source, and resource-safe JDBC. I can point at the exact commit for each
and explain the threat it closes."*

---

## Phase 2 — Architecture, testing & build tooling

### Migrate the build to Maven
- Migrated an ad-hoc PowerShell `javac`/`jar` build with seven hand-committed jars to a
  declarative **Maven** build (`pom.xml`) with managed, versioned dependencies.
- Added the **Maven Wrapper** (`mvnw`) pinned to a fixed Maven version, so the project
  builds reproducibly with **no global Maven install** — a one-line `./mvnw clean package`
  on any machine or CI runner.
- Packaged the app as a single runnable uber-jar with **maven-shade-plugin**, including a
  `ServicesResourceTransformer` to merge `META-INF/services` so the SLF4J/Logback provider
  and the JDBC driver still resolve from the shaded jar.
- Pruned dependencies to what's actually used: dropped the dead `beansbinding` jar, and
  for the one library not on Maven Central (NetBeans `AbsoluteLayout`) set up a committed
  **project-local Maven repository** so the build stays reproducible and offline-capable.
- Restructured the source to the Maven standard layout and removed the obsolete NetBeans/
  Ant artefacts (`build.xml`, `nbproject/`, `manifest.mf`).

**Talking point:** *"The interesting bit wasn't writing a pom — it was the edge cases:
shading the uber-jar broke service discovery until I merged the `META-INF/services`
files, and one dependency wasn't on Maven Central, so I vendored it through a project-local
repo rather than committing a jar onto the classpath. The wrapper means CI needs zero
setup."*

### Introduce a persistence (DAO/repository) layer
- Extracted ~40 inline SQL statements scattered across ten classes (game logic + Swing
  screens) into a dedicated `amiss.repository` package — `UserRepository`,
  `UserStatsRepository`, `JobRepository`, `HelpRepository` — each a thin, testable DAO
  over the existing JdbcTemplate-style `DB` helper, with no SQL left in the UI or rules.
- Designed the repositories Swing-free and constructor-injected with their data source,
  so they already run headless — deliberate groundwork for the service layer and unit
  tests that follow in Phase 2.
- Modelled "row may not exist" honestly with `Optional` (`findByName` / `findPasswordHash`
  / `findDescription`) while keeping each call site's historical default, so the refactor
  is behaviour-preserving by construction.
- Fixed a latent production bug uncovered while consolidating the SQL: the in-game Help
  queried a non-existent column (`descip` vs the schema's `description`), so every Help
  topic had silently thrown `SQLException` since the schema was rebuilt. Verified the fix
  against live MySQL.

**Talking point:** *"This is the first slice of a layered architecture — domain → DAO →
service → UI. I kept it deliberately small and behaviour-preserving: move the SQL behind
repositories first, prove identical behaviour, then decouple the rules from Swing in a
second PR. Consolidating the queries also surfaced a real bug — a wrong column name that
had broken the Help screen — which is exactly the kind of thing that hides in copy-pasted
SQL and disappears once there's one place that owns each query."*

### Decouple the game rules into a headless service layer
- Extracted the game rules out of five Swing-coupled classes into a Swing-free
  **`amiss.service`** layer (`TimeService`, `EducationService`, `FoodService`,
  `JobService`, `StatsService`) — the rules no longer take `JTextArea`/`JLabel`
  parameters; they return values and a small immutable `ActionResult` (notification
  text + new timer/money) that the screen renders.
- Removed a global mutable bus: the rules previously reached into static
  `MainGameGUI.db` / `MainGameGUI.user` fields. Replaced it with **constructor
  injection** behind a single `GameServices` **composition root** that wires each
  service to its repositories and the current user — no statics, no Swing dependency.
- Refactor was **behaviour-preserving by construction** (1:1 method mapping, unchanged
  side-effect ordering); persistence-failure messages now go to the SLF4J log instead of
  the UI (normal play is byte-identical). Verified the generated NetBeans
  `initComponents()` layout code was untouched in the diff.
- Proved the payoff directly: a **no-Swing probe** builds `GameServices` and reads a live
  player's full state through the services against MySQL — the rules now run headless,
  which is the groundwork for the JUnit suite and the eventual Spring Boot extraction.

**Talking point:** *"The rules used to be welded to the UI — methods took a text area and
a couple of labels and wrote messages straight into them, and they read the DB and the
current user out of static fields on the main window. I turned that into a real service
layer: the services take their dependencies by constructor, return plain values or a tiny
result object, and a `GameServices` composition root does the wiring. The proof it worked
is a 30-line headless program that runs the game logic against MySQL with no Swing loaded
at all — which is exactly what makes the logic unit-testable and portable into a Spring
backend later. I kept it safe by mapping every method 1:1 and checking that none of the
generated UI layout code changed in the diff."*

### Unit-test the game logic (JUnit 5 + Mockito)
- Wrote an **87-test JUnit 5** suite over the game-rule service layer, run through **Maven
  Surefire** with `./mvnw clean test` — the project's first automated tests.
- **Mocked the repository (DAO) layer with Mockito** so the rules are unit-tested in
  complete isolation from MySQL: the tests are deterministic, run in ~1s, and need no
  database — which means the upcoming CI can run them on any runner with zero setup.
- Measured coverage with **JaCoCo**: ≈**88% instruction / 82% line / 78% branch** of the
  service layer (100% of the validation helper), focused on the real decision logic —
  board-distance maths, time/wage formatting, and the buy/eat/work branches.
- Wrote them as **characterization tests** (lock current behaviour before later change),
  which surfaced two genuine defects I documented rather than silently changed: a
  minutes-formatting bug (`"2:5"` instead of `"2:05"`) and a **provably dead `else if`
  branch** in the distance calculation.
- Tested the cross-service `workMain`/`eatMain` orchestration with **real collaborators
  over mocked repositories** (mirroring the production composition root) because the code
  calls `toString()` on a collaborator, which a mock can't stub — choosing the right test
  double per case rather than mocking dogmatically.

**Talking point:** *"I deliberately mocked the repositories rather than spinning up an
in-memory database. The thing I'm testing is the game rules, not JDBC — and the `DB` class
is hardcoded to connect to MySQL, so a real/in-memory DB would have meant refactoring
production code just to test it. Mocking keeps the tests hermetic and CI-friendly. The
nice payoff of writing them as characterization tests is that they immediately caught two
bugs — a minute that prints as `2:5`, and an `else if` whose condition is a subset of the
`if` above it, so it can literally never run. I left both as documented findings, each with
a passing test that pins the current (quirky) behaviour, because the task was 'add tests
without changing behaviour' — fixing them is a separate, intentional commit."*

### GitHub Actions CI
- Set up **GitHub Actions CI** running `./mvnw -B clean package` (full test suite +
  JaCoCo) on every push to `main`/`develop` and on every PR; the `build` job is the
  required status check gating PRs into the protected `main`.
- Publish test and coverage reports as build artifacts with a coverage summary table in
  the run overview; coverage **badges** are generated by CI and pushed to an orphan
  `badges` branch so the README stays current without writing to the protected branch.
- Hardened the pipeline against Windows-authored repos: fixed the execute bit on `mvnw`
  in the git index (Linux runners can't chmod a checkout) and verified the wrapper makes
  runners need zero pre-installed toolchain.

**Talking point:** *"Small pipeline, but the details are the point: the required status
check is what makes branch protection mean something, and the badge publishing had to
work around a protected main — CI pushes the SVGs to an orphan branch instead. It's the
difference between 'tests exist' and 'nothing unmerged can dodge them'."*

### Flyway database migrations
- Replaced the hand-run `setup.sql` with **versioned Flyway migrations**
  (`V1__baseline_schema`, `V2__seed_reference_data`) applied automatically at app start,
  so schema history is in source control and every environment converges on the same DDL.
- **Split migration and runtime credentials**: migrations run as a dedicated
  `amiss_migrator` account (DDL rights); the app's `amiss` user keeps zero DDL — the
  production-style privilege split.
- Preserved existing installs with `baselineOnMigrate`: pre-Flyway databases are
  baselined at V1 (saves intact) and only newer migrations apply; reference data is
  reseeded idempotently (DELETE+INSERT) so clean and upgraded DBs are identical.
- Added a **Migrations CI workflow** that builds a throwaway MySQL 9 container purely
  from the migrations — proof the schema stands on its own.

**Talking point:** *"The interesting decisions were operational: separate migrator vs
runtime credentials, a baseline strategy so existing saves survive the cutover, and a CI
job that proves a clean database can be built from migrations alone. That's the shape
schema management takes in a bank — nobody runs ad-hoc SQL against prod."*

---

## Phase 3 — Go full-stack (Spring Boot API + React SPA)

### Clean-architecture restructure (ports & adapters)
- Restructured the flat package into layered **`domain` / `application` /
  `infrastructure` / `presentation`** with repository **ports** (interfaces) in the
  application layer and JDBC **adapters** in infrastructure; a `GameContext` composition
  root is the only place that names concrete implementations.
- Kept the refactor behaviour-preserving (all 87 tests green with only import changes) —
  the Mockito mocks survived because the tests always mocked the repository *type*,
  which became the port interface.
- The payoff landed immediately: the later Spring Data JPA swap replaced the persistence
  technology **without touching a single rule in `amiss-core`**.

**Talking point:** *"Hexagonal architecture is easy to describe and hard to retrofit.
The proof mine is real: when I swapped raw JDBC for Spring Data JPA two milestones
later, the diff touched adapters and configuration only — the services never noticed.
That's the property the pattern promises, demonstrated on a codebase that didn't start
with it."*

### Spring Boot REST API (`amiss-api`)
- Split the build into a **multi-module Maven reactor** (`amiss-core` rules,
  `amiss-api` Spring Boot 3 / Java 21) and exposed every game rule over JSON/HTTP —
  state, movement, banking, rent, jobs, university, food — across six stacked,
  individually-reviewed PRs.
- Standardised errors on **RFC 7807 `ProblemDetail`** across the API, with typed outcome
  objects (not exceptions) for domain rejections that still charge the player — an
  API contract distinction (409 vs charged-200) that the frontend relies on.
- Modelled money/time as integers end-to-end (minutes, not floats) and pinned action
  costs in configuration, keeping the API and the legacy client on one rule source.

**Talking point:** *"The design rule I'm proudest of: a rejected action that still costs
the player time is a 200 with an outcome payload, not an error — because it mutated
state. Getting that wrong is how clients end up with stale caches; we found the one place
it leaked (a move landing on exactly zero minutes) through live verification and pinned
it with a regression test."*

### Spring Data JPA + Testcontainers
- Mapped the Flyway-owned schema with JPA entities in **validate-only** mode
  (`ddl-auto=validate`) — Flyway remains the sole schema owner, Hibernate just has to
  agree with it — and swapped the JDBC adapters for **Spring Data repositories** behind
  the same ports.
- Proved every adapter against a real database with a **Testcontainers MySQL 9**
  integration suite (singleton container, non-transactional port-contract tests so
  `@Modifying` JPQL is exercised the way production runs it).
- The ITs caught a real bug unit tests can't: `save()` with an assigned id defers the
  INSERT to commit, so constraint violations escaped the translation boundary —
  fixed with `saveAndFlush()` where the port contract promises translated exceptions.

**Talking point:** *"Validate-only JPA over Flyway is the grown-up configuration: one
owner for the schema, and the ORM fails fast if the mapping drifts. And the
Testcontainers suite earned its keep immediately — it caught a transaction-boundary bug
that every mocked test happily missed."*

### Spring Security (stateless JWT)
- Added register/login issuing **stateless HS256 JWTs** (Spring Security oauth2 /
  Nimbus), reusing the existing `PasswordHasher` BCrypt rule — including its transparent
  legacy-plaintext upgrade — so both clients agree on who can log in.
- Locked down every `/api/**` route behind the token and added a central
  **player-scope filter**: a valid token for one player gets **403** on another player's
  resources — closing the IDOR class, not just requiring authentication.
- Wired CORS for the SPA origin explicitly and reduced the actuator health probe to
  status-only; the JWT secret comes from the environment, never the repo.

**Talking point:** *"Authentication was the easy half. The part interviewers should care
about is authorization: without the scope filter, any logged-in user could read or write
any player's state by changing the username in the path — a textbook IDOR. The filter
makes cross-player access a 403 by construction, tested at the MockMvc layer with the
security config actually loaded."*

### React SPA (Vite + TypeScript)
- Built a **React 19 + TypeScript SPA** (Vite, React Router, TanStack Query, Vitest —
  131 frontend tests) consuming the API: login/registration with JWT storage and
  401-driven re-auth, the 13-stop board with data-driven hotspots, a Jones-style HUD,
  and a panel per building (bank, rent, employment, university, food, shops) behind a
  location-panel registry.
- Established the client-side cache-consistency rule matching the API contract: every
  mutation updates from the response on charged rejections and **invalidates on
  errors** — the server is the source of truth, the cache is never trusted after a
  failure.
- Kept all art behind an asset manifest with generated placeholders, so final artwork
  drops in with zero code changes.

**Talking point:** *"The SPA's hardest problem was cache honesty against an API where
'failed' actions can still charge you. The rule we landed on — apply server state from
every response, invalidate on every error — came out of a live bug where a 409'd move
had actually moved the player. It's a small discipline that eliminates a whole class of
stale-UI bugs."*

### Retiring the legacy desktop client
- Sunset the original Swing client once the web stack reached feature parity: one
  deletion PR removed the whole module (−4,700 lines) plus its Swing-only plumbing,
  leaving the REST API + SPA as the single product surface.

**Talking point:** *"Knowing when to delete is a skill: the desktop client had been the
safety net through every refactor, and the moment the SPA covered its last screen it
became pure maintenance drag. Because the rules lived behind ports, deleting an entire
client was a low-risk PR, not a rewrite."*

### Save slots & Jones-parity catalog schema (expand phase, Flyway V5)
- Designed an **expand/contract schema migration**: an additive V5 introduces multiple
  save slots per account (`tblsave` with per-save goal targets) and a normalized
  job/degree catalog (`tbljob`, `tbldegrees`, a `tbljob_degrees` prerequisite mapping,
  per-save application history) — while the legacy tables kept serving the live API
  untouched; the destructive V6 lands only after the code cutover.
- Mapped the new schema with validate-only JPA entities and Spring Data repositories,
  proven against real MySQL 9 with Testcontainers integration tests before any consumer
  code existed.

**Talking point:** *"This is the zero-downtime schema-change discipline banks expect:
expand first (additive, old code still runs), migrate the code, contract last. The
migration history shows exactly that sequence — V5 additive, cutover PR, V6 drops the
legacy shapes."*

### Jones-parity hiring & progression mechanics (save-scoped core rules)
- Implemented the reference game's employment model as pure, framework-free core
  services behind ports: probabilistic hiring (odds reconstructed from the community
  wiki as a function of experience, dependability and degrees), multi-reason rejection
  outcomes with per-save turn-down history, pro-rated shift pay (wage × 8) with
  warning/fired dependability bands, a prerequisite-driven degree tree, and per-save
  goals with a sticky win condition — all TDD'd with a comprehensive unit suite.
- Kept the game's hidden-information rule enforceable at the API boundary by design:
  job requirements, experience and dependability live only in core outcome types, so
  the web layer can expose player-visible results without ever leaking the hidden stats.

**Talking point:** *"The fun part was reverse-engineering the 1990 game's hiring odds
from wiki notes; the engineering part was where the rules live: save-scoped services
behind ports with typed outcomes, so the API cutover that followed was a pure web-layer
change — and 'the UI shouldn't show job requirements' became 'the wire contract has no
requirement fields, pinned by tests', which is the difference between hiding data and
not sending it."*

### Multi-save API cutover + destructive contract migration (Flyway V5/V6)
- Completed the expand/contract pair started by the V5 schema: every mutating route
  moved from a single implicit per-user game state to `/api/saves/{saveId}/...`,
  resolved through one ownership guard (`SaveScope`) shared by every controller —
  closing the IDOR class for save access the same way the earlier `PlayerScopeFilter`
  closed it for per-user routes, but now for a save a user can own several of.
  Unknown save → 404, someone else's save → 403, one problem+json shape either way.
  Legacy player-state services, ports and the high-score board were then deleted
  wholesale (compiler-driven), and `V6__drop_legacy_state.sql` performed the
  destructive half of the migration — shrinking `tbluser` to credentials only — behind
  a dedicated pre-commit review of the migration and FK impact, since a `DROP TABLE`
  can't be undone once it ships.
- Kept the hidden-information rule enforced as an **absence from the wire**, not a
  filtered field: job listings and save-state DTOs are structurally incapable of
  carrying `experience`/`dependability`/requirement data, pinned by tests that assert
  against the raw JSON body so a leak via a renamed field would still be caught.
- Proved the two-phase migration end-to-end with a purpose-built integration test that
  runs Flyway to the pre-expand version, seeds a legacy account exactly as pre-cutover
  registration would have, then migrates the rest of the way — asserting the account's
  state survives into its new save row, its credentials survive V6's column drop, and
  the dropped legacy tables are actually gone, against a real MySQL 9 container.

**Talking point:** *"The riskiest line in this PR was a `DROP TABLE` — once it ships,
there's no rolling it back without a restore. So before that commit landed, the
migration file and the full FK/entity diff went through a dedicated review focused on
exactly one question: does this drop anything something else still reads? The
migration integration test is the proof: it doesn't just check the schema shape, it
seeds a real pre-migration account and watches it survive the full V1-to-latest path,
which is the actual guarantee an existing user cares about."*

### Frontend cutover to the save-scoped API — closes out the goal
Six-task frontend rewrite that completes the goal started by the schema expand/contract
and save-scoped core rules above: cut the whole React SPA over from a single implicit
per-user game to `/api/saves/{saveId}/...`, and shipped the player-facing UI for the
hidden-information hiring model and the multi-save/goals system.
- Rebuilt the Employment Office as a two-step workplace-then-job drill-down where every
  listing is enabled — there is no requirement data to grey a button against, because the
  wire contract never carries it. The hidden-information rule that started as a backend
  DTO shape became a UI that structurally cannot leak it, closed by a test that asserts
  the *absence* of requirement/experience/dependability text rather than trusting the
  component not to render it.
- Built an 11-degree course tree (earned/available/locked, prerequisite chains) and a
  saves screen (list/continue/delete, four-goal creation sliders) replacing what used to
  be one implicit save per account.
- Live-verified the entire acceptance path against the real running backend rather than
  trusting mocked component tests alone: registered a disposable account, played through
  employment and university, inspected the raw `GET /api/jobs` network response byte-for-
  byte to confirm the hidden-information contract holds on the wire (not just in the
  component tree), and drove a real `end-week` win-flow round trip end-to-end.
- That live pass caught a real bug no unit test could: a win-banner styled with an
  explicit white background and no explicit text color rendered invisible white-on-white
  text under the app's dark color scheme — a defect class (`background`/`color` set
  without its pair, under a `color-scheme: light dark` default) that a follow-up
  whole-branch review then found a second instance of elsewhere in the same PR, fixed
  before merge.

**Talking point:** *"Component tests proved the DOM was correct; they couldn't prove the
pixels were legible. A live browser pass against the real backend caught a win banner
rendering white text on a white card — invisible,100% test-covered, and completely
unusable. The fix was one line, but the lesson generalizes: in a codebase whose CSS
resets to the browser's dark-mode defaults, any rule that sets `background` or `color`
explicitly has to set the other too, or it's a coin flip per visitor's OS theme. I wrote
that rule into the project's lessons file as a review checkpoint, not just a one-off
patch — the same review that caught it also went looking for, and found, a second
instance of the identical mistake before it shipped."*

### Economy engine core — one pricing authority over hidden state (KAN-48 PR 1)
- Implemented the reference game's economy as wiki-exact rules: two **hidden per-save
  values** (Index −3..+3, Reading −30..+90) drifting weekly, with every price derived as
  `base + base×reading/60` (50%–250% of base) in **integer-only math** (`Math.floorDiv`,
  never floats) — base prices in the catalog enums and `tbljob` are never mutated.
- Made a single `EconomyService` in the core application layer the **one pricing
  authority**: the charge paths (shop, enroll) and the API's catalog/DTO reads both call
  it, so the price a player is shown is the price they are charged, by construction —
  pinned by tests that stub a formula-based economy so identity pricing cannot pass.
- Extended the expand/contract discipline with an additive **V7 migration** whose
  defaults (0/0) reproduce exactly the pre-economy prices, so existing saves migrate
  with zero price shock; Hibernate's validate-only mode then caught a real
  entity-vs-schema drift (fields retyped to match V7's `TINYINT`/`SMALLINT`), proven
  against real MySQL 9 with Testcontainers.
- Generalised the injected-randomness pattern (`IntUnaryOperator` roll) so every
  probabilistic economy outcome is scriptable in tests, and kept the hidden-information
  rule intact: `economyIndex`/`economyReading` never appear on a wire DTO.

**Talking point:** *"The design decision that matters is 'one pricing authority'. The
moment two code paths each compute a price, they eventually disagree — and in this game
a rejected purchase can still charge you, so shown-vs-charged drift is a real bug class.
Routing both the catalog reads and the charge paths through one service makes the
invariant structural, and the tests enforce it with a non-identity economy stub so a
lazy mock can't fake compliance. The migration is the banking habit again: additive,
default-safe, and the ORM's validate mode caught the one place the mapping drifted."*

### Wage snapshot at hire — temporal consistency for pay (KAN-48 PR 2)
- Added **V8** (`wage INT NULL` on `tblsave`) with a backfill deliberately scoped
  `WHERE job_id IS NOT NULL`, and proved the MySQL-specific `ALTER`/`UPDATE`-join
  syntax against a real MySQL 9 container rather than trusting H2-alike leniency.
- Implemented **wage snapshotting**: hiring locks the listed (economy-adjusted) wage
  onto the save, shift pay derives from the snapshot (`wage × 8`), and being fired
  clears it — so later economy drift can never retroactively change agreed pay. The
  reviewer traced every job-assignment site to confirm the pair (`jobId`, `wage`) is
  always set and cleared together.
- Cut job listings over to save-scoped, economy-priced wages end-to-end (API DTO →
  React Employment Office), live-verified against the running stack: listings repriced
  with the economy, a hire locked wage 10, the snapshot survived an economy reset, and
  a worked shift paid exactly 80.

**Talking point:** *"Snapshot-at-agreement is a pattern straight from financial systems:
the rate you contracted at is the rate you settle at, whatever the market does after.
Here that meant the wage moves from being derived (recomputed from the economy every
read) to being recorded (written once at hire) — and the interesting review question was
lifecycle: every path that sets or clears the job must set or clear the snapshot with
it, which we verified by tracing all assignment sites rather than assuming."*

### Market crashes & booms — ordered events over deterministic randomness (KAN-48 PR 3)
- Implemented the reference game's week-8+ market events as a typed
  `EconomyEvent` outcome from one `rollEvent` step: crashes only off a near-peak
  economy (Reading ≥ 80), with wiki-uniform severities — MINOR drops prices, MODERATE
  is a coin flip between a pay cut to 80% and being fired, MAJOR fires you **and wipes
  your bank account** — and a boom as the upside jolt.
- Made event **ordering** an explicitly tested invariant, not an accident of statement
  order: the event rolls after the weekly drift and *before* the win condition is
  evaluated, pinned by a test where a save that would otherwise win has its bank wiped
  by a MAJOR crash first — sequencing semantics (what settles before what is judged)
  treated with the care a payments system gives value-dating.
- Kept every probabilistic branch **deterministic in tests** via the injected-roll
  pattern: scripted rolls are consumed from a queue that throws on over-consumption, so
  the tests pin not just outcomes but the exact roll-stream contract (an ineligible
  crash consumes no die; an unemployed MODERATE crash consumes no fire coin) —
  replay-grade determinism over hidden randomness.
- Surfaced events to the player as **consequences, never internals**: the end-week wire
  DTO carries fired/pay-cut/bank-wiped/happiness facts while the hidden Index and
  Reading stay structurally absent from every response, and the React end-week modal
  renders one line per consequence, each pinned by exact-text tests.

**Talking point:** *"The line I'd defend in review is the ordering test: a crash that
wipes your bank has to land before the win check, or a player can win with money that
no longer exists. That's a sequencing bug class banks care about deeply — settlement
before judgment — and the test constructs exactly the pathological case. The other
piece I like is the roll-queue discipline: because injected rolls throw when
over-consumed, the tests prove which random draws each branch consumes, not just what
it returns. That's what makes a probabilistic system auditable — same seed, same
story, byte for byte."*
