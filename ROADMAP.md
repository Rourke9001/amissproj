# AmissProj Roadmap

Growing this from a high-school NetBeans desktop game into a **full-stack, CV-ready
Java project**. This is a living document — extended each session. Tick items off as
they ship; add new ideas to the Backlog.

Each phase notes **why it matters for a banking-sector Java CV**, so the work doubles
as portfolio signal, not just busywork.

---

## How we work each session

1. **You don't re-explain the project.** I read `README.md`, `SETUP.md`, `CLAUDE.md`
   and this file at the start of a session. Just tell me the goal.
2. **Optional kickoff prompt** (copy/paste, fill the blanks):
   > *"Let's work on **<what you want to achieve>**. (Roadmap: Phase _ / <item>.)
   > Put it on a feature branch. Plan first, then implement."*
   Even shorter works: *"Start the SQL-injection fix on a branch."*
3. **Branch per unit of work.** I cut a `feat/<name>` branch off **`develop`**, work in
   small commits, push it, and open a PR into `develop` for you to review/merge. When a
   milestone is ready, a release PR merges `develop` → `main`. `main` is protected
   (PR + green CI build required) and always runnable.
4. **Docs stay current.** Finishing an item includes ticking it off here and updating
   `SETUP.md`/`README.md` if behaviour changed.

Branch name prefixes: `feat/` (feature), `fix/` (bug), `chore/` (tooling/deps),
`docs/` (documentation), `test/` (tests), `refactor/` (no behaviour change).

---

## Status at a glance

- [x] **Phase 0 — Revival & version control** (June 2026)
- [x] **Phase 1 — Backend hygiene & security** (June 2026)
- [x] **Phase 2 — Architecture, testing & build tooling** (June–July 2026)
- [ ] **Phase 3 — Go full-stack (Spring Boot API + web UI)**
- [ ] **Phase 4 — Showcase & deploy**

---

## Phase 0 — Revival & version control ✅ (June 2026)
*Got the dead project building and running on a modern toolchain, under git.*
- [x] Reverse-engineered the project; documented setup in `SETUP.md`
- [x] Replaced dead Connector/J 5.1.22 with GPL Community 9.7.0; modernised `DB.java`
- [x] Reconstructed the MySQL schema + seed data in `db/setup.sql`
- [x] Build/run without NetBeans/Ant (`scripts/build.ps1`, `scripts/run.ps1`)
- [x] Git repo + first commits pushed to GitHub

## Phase 1 — Backend hygiene & security ✅ (June 2026)
*Why (CV): writing safe, maintainable data-access code is table stakes for banking.
These are the fixes an interviewer will look for first.*
- [x] **Parameterise all SQL with `PreparedStatement`** — killed the pervasive SQL
      injection; `DB` is now a small JdbcTemplate-style helper (all 36 queries bound)
- [x] **Hash passwords (BCrypt)** — salted hashes via `PasswordHasher`; legacy plaintext
      logins are transparently re-hashed; `User` no longer holds the password
- [x] try-with-resources for every `ResultSet`/`Statement` — owned inside `DB`, no leaks
- [x] Externalise DB config (host/user/password) to `application.properties` / env vars
      (`Config`; `AMISS_DB_URL`/`AMISS_DB_USER`/`AMISS_DB_PASSWORD` overrides)
- [x] Create a dedicated least-privilege MySQL user instead of `root`
      (`amiss`@`localhost`, only SELECT/INSERT/UPDATE on `amissdb`)
- [x] Replace `System.out` debugging with real logging (SLF4J + Logback)
- [x] Basic input validation on usernames / form fields (`Validation`)

## Phase 2 — Architecture, testing & build tooling ✅ (June–July 2026)
*Why (CV): demonstrates clean architecture, testing discipline and a modern build —
the difference between "wrote some Java" and "engineers software".*
- [x] **Migrate the build to Maven** (June 2026) — `pom.xml` with managed dependencies
      + the `mvnw` wrapper (no global Maven needed); `maven-shade-plugin` builds one
      runnable `target/AmissProj.jar`. Dropped the committed `dist/lib` jars and the dead
      `beansbinding` dep; vendored the one non-Central jar (NetBeans `AbsoluteLayout`)
      into a project-local repo; removed the NetBeans/Ant leftovers.
- [x] Introduce a layered architecture: domain model → DAO/repository → service → UI
      *(PR A, June 2026: DAO/repository layer — `amiss.repository`
      {User,UserStats,Job,Help}Repository over the `DB` helper; all ~40 scattered SQL
      statements routed through it; fixed the long-standing `HelpGUI`
      `descip`→`description` column bug. PR B, June 2026: the `amiss.service` layer —
      {Time,Education,Food,Job,Stats}Service + a `GameServices` composition root, so the
      stack is now domain → DAO → service → UI.)*
- [x] Decouple the game rules from Swing so they can run headless
      *(PR B, June 2026: extracted the rules into Swing-free services that return values
      or an `ActionResult`; dropped the `MainGameGUI.db/user` ambient statics via
      constructor injection; the GUIs are thin callers. Verified headless against live
      MySQL with a no-Swing probe.)*
- [x] **JUnit 5** unit tests for the game logic; aim for meaningful coverage
      *(June 2026: 87 tests over the `amiss.service` rules, run via Maven Surefire.
      Mockito mocks the `amiss.repository` DAOs so the rules are unit-tested with no
      MySQL — fast, deterministic, CI-ready. JaCoCo reports ≈88% instruction / 82% line
      / 78% branch coverage of `amiss.service` (Validation 100%). Characterization tests
      lock current behaviour and documented two quirks — the `getNewTime` single-digit
      minute (`"2:5"` not `"2:05"`) and a dead `getMulti` branch.)*
- [x] **GitHub Actions CI**: compile + run tests on every push / PR
      *(July 2026: `.github/workflows/ci.yml` — `./mvnw -B clean package` (the
      89-test JUnit suite + JaCoCo) on pushes to main/develop and on every PR; test + coverage reports
      published as artifacts with a coverage table in the run summary; coverage badges
      pushed to the orphan `badges` branch and wired into the README alongside the
      build badge. The `build` job is the required status check that gates PRs into
      the protected `main`.)*
- [x] Flyway DB migrations to version the schema (replaces `setup.sql`)
      *(July 2026: Flyway 11 — `V1__baseline_schema` + `V2__seed_reference_data`
      under `src/main/resources/db/migration`, applied at app start by
      `FlywayMigrator` as a dedicated `amiss_migrator` account, so the runtime
      `amiss` user keeps zero DDL rights. `baselineOnMigrate` preserves
      pre-Flyway saves; `db/setup.sql` retired for a users-only
      `db/bootstrap.sql`; a Migrations CI workflow proves a clean MySQL 9
      container is built purely from the migrations. Running the game now needs
      a JDK 17+ runtime — Flyway 11's floor — while the source still targets 8.)*

## Phase 3 — Go full-stack
*Why (CV): the headline. A real Spring backend + web frontend is exactly what Java
banking roles hire for.*
- [x] **Clean-architecture groundwork** (July 2026) — restructured the flat `amiss` package
      into layered `domain` / `application` / `infrastructure` / `presentation` packages with
      repository **ports** (interfaces) and JDBC **adapters**, a `GameContext` composition root,
      and no JDBC types in the UI. Behaviour-preserving (all 87 tests green); makes the Spring
      swap below a drop-in. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- [ ] Extract the game logic into a **Spring Boot REST API**
- [ ] Persistence via **Spring Data JPA / Hibernate** (entities replace raw JDBC)
- [ ] **Spring Security** auth (hashed credentials, sessions or JWT)
- [ ] A **web frontend** consuming the API (Thymeleaf, or a React/TypeScript SPA)
- [ ] OpenAPI / Swagger API documentation
- [ ] **Dockerfile + docker-compose** (app + MySQL) → clone-and-run in one command

## Phase 4 — Showcase & deploy
*Why (CV): a live link and a polished README are what make a recruiter stop scrolling.*
- [ ] Deploy to a container host / cloud free tier (live demo URL)
- [ ] README polish: screenshots, architecture diagram, demo link, CI + coverage badges
- [ ] Stretch: real-time / multiplayer high-score board, or an accessibility pass

---

## Backlog / ideas
*Stray thoughts land here until they're promoted into a phase.*

### Epic: UI standardization & polish
*Make the Swing screens look like one coherent app before any bigger UI work.*
- [x] **UI foundation** (June 2026) — removed the NetBeans coupling that blocked
      hand-editing: deleted the `.form` designer files, fixed the IDE classpath
      (`.vscode/settings.json` → `dist/lib`), and replaced the dead hard-coded
      `C:\Users\The Rourke\...` image paths with portable, jar-bundled assets loaded via
      `amiss.Assets` (placeholders generated by `scripts/gen-placeholders.ps1`).
- [x] **Board & turn-timer rework** (July 2026) — rebuilt the board as the original
      *Jones in the Fast Lane* **13-stop clockwise loop on a 5×4 grid** (turn timer in the
      bottom-middle cell), via a clean `domain.board.Board` ring model that replaces the old
      `TwoDGrid` + `getMulti` distance hack. The turn timer is now in **hours** (60/week, 72
      if fed; **+2h** to enter a building, **6h** work/study/relax, **4h** apply-for-job, **0h**
      shopping). Added navigable placeholder screens for **Pawn Shop, Z-Mart & Le Security
      Apartments**; renamed screens to **Monolith Burgers / QT Clothing / Socket City /
      Hi-Tech U / Black's Market** with `tbljobs.location` kept in lockstep; folded the Relax
      action into Home (retired Public Pool & Pizza Palace).
- [ ] Standardize **screen sizes** / window sizing across all screens
- [ ] Consistent **fonts, colours, button styling & spacing** (a shared theme/helper)
- [ ] Replace placeholder art with real, cohesive backgrounds + board
- [ ] Consider scaling backgrounds to the window instead of fixed-size labels

### Other ideas
- (add ideas as they come up)
