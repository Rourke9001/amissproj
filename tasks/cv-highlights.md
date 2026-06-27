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
