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
3. **Branch per unit of work.** I cut a `feat/<name>` branch off `main`, work in small
   commits, push it, and open a PR for you to review/merge. `main` stays runnable.
4. **Docs stay current.** Finishing an item includes ticking it off here and updating
   `SETUP.md`/`README.md` if behaviour changed.

Branch name prefixes: `feat/` (feature), `fix/` (bug), `chore/` (tooling/deps),
`docs/` (documentation), `test/` (tests), `refactor/` (no behaviour change).

---

## Status at a glance

- [x] **Phase 0 — Revival & version control** (June 2026)
- [x] **Phase 1 — Backend hygiene & security** (June 2026)
- [ ] **Phase 2 — Architecture, testing & build tooling**
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

## Phase 2 — Architecture, testing & build tooling
*Why (CV): demonstrates clean architecture, testing discipline and a modern build —
the difference between "wrote some Java" and "engineers software".*
- [x] **Migrate the build to Maven** (June 2026) — `pom.xml` with managed dependencies
      + the `mvnw` wrapper (no global Maven needed); `maven-shade-plugin` builds one
      runnable `target/AmissProj.jar`. Dropped the committed `dist/lib` jars and the dead
      `beansbinding` dep; vendored the one non-Central jar (NetBeans `AbsoluteLayout`)
      into a project-local repo; removed the NetBeans/Ant leftovers.
- [ ] Introduce a layered architecture: domain model → DAO/repository → service → UI
- [ ] Decouple the game rules from Swing so they can run headless
- [ ] **JUnit 5** unit tests for the game logic; aim for meaningful coverage
- [ ] **GitHub Actions CI**: compile + run tests on every push / PR
- [ ] Flyway (or Liquibase) DB migrations to version the schema (replaces `setup.sql`)

## Phase 3 — Go full-stack
*Why (CV): the headline. A real Spring backend + web frontend is exactly what Java
banking roles hire for.*
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
- [ ] Standardize **screen sizes** / window sizing across all screens
- [ ] Consistent **fonts, colours, button styling & spacing** (a shared theme/helper)
- [ ] Replace placeholder art with real, cohesive backgrounds + board
- [ ] Consider scaling backgrounds to the window instead of fixed-size labels

### Other ideas
- (add ideas as they come up)
