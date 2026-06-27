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
