# Jones-parity Jobs, Degrees, Saves & Win Rules — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the reconstructed job/education model with the real Jones in the Fast
Lane catalog and mechanics (hidden hiring requirements, experience/dependability, the
11-degree tree), add multiple save slots per account with per-save win goals, retire
highscores and the Swing client.

**Architecture:** Expand/contract schema evolution (additive V5 → cutover V6) under
validate-only JPA; a new save-scoped service layer built alongside the old one, then a
single cutover PR rewires the API and deletes the legacy layer; the SPA follows.

**Tech Stack:** Java 21, Spring Boot 3 (amiss-api), plain-Java rules (amiss-core),
Flyway 11 / MySQL 9, Spring Data JPA (validate-only), Testcontainers, React 19 + TS +
TanStack Query + Vitest (frontend/).

**Spec:** `docs/superpowers/specs/2026-07-07-jobs-degrees-saves-design.md` — formulas,
tables and API contracts live there; this plan references them as normative.

## Global Constraints

- Branch per PR off `develop`; PRs target develop (`gh pr create --base develop`).
- Commits: `git commit -F <file>` (message via `Set-Content -Encoding ascii`), end with
  `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`. No double-quotes in messages.
- Build: `.\mvnw.cmd -B clean test` (unit), `.\mvnw.cmd -B clean verify` (ITs; needs
  Docker Desktop running). Frontend: `npm test` in `frontend/`. After core changes,
  `.\mvnw.cmd -B install -DskipTests` before running the API from amiss-api.
- Flyway owns the schema; JPA is `ddl-auto=validate`. Every merged PR leaves develop
  compiling, tests green, and the app bootable against a V-current database.
- Money/time are integers everywhere. Charged rejections are HTTP 200 + outcome.
- Experience and dependability must never appear in any wire DTO; job DTOs must never
  carry requirement fields. (MockMvc regression tests pin both.)
- Each PR: JIRA ticket → In Progress + PR link comment; → Done on merge; ROADMAP.md
  ticked when the milestone lands.
- 1 PR = 1 JIRA subtask (board convention).

## PR sequencing and merge order

| PR | Branch | JIRA | Green-ness contract |
|----|--------|------|---------------------|
| 1 | `docs/jobs-degrees-spec` | — | docs only (spec, this plan, lessons, cv-highlights) |
| 2 | `chore/retire-swing` | KAN-6 child (new) | reactor builds without amiss-swing |
| 3 | `feat/saves-jobs-degrees-schema` | KAN-5 child (new) | V5 additive; legacy code untouched |
| 4 | `feat/hiring-mechanics` | KAN-5 child (new) | new services built alongside old; nothing rewired |
| 5 | `feat/saves-employment-api` | KAN-5 child (new) | API cutover + V6 contract; old layer deleted |
| 6 | `feat/frontend-saves-employment` | KAN-45 (rescoped) | SPA cutover; merge immediately after PR 5 |

PR 5 breaks the deployed SPA's routes; merge PR 6 back-to-back with it (stacked branch),
and only release develop→main after PR 6.

---

### Task 1 (PR 1): Docs — spec, plan, lessons, cv-highlights

Executed in the authoring session (this branch). Files: the spec, this plan,
`tasks/lessons.md` (WebFetch-402/Chrome lesson), `tasks/cv-highlights.md` (Phase 2 tail
+ Phase 3 sections), `tasks/todo.md` (milestone checklist).

- [ ] Commit all five files; push; `gh pr create --base develop`.

---

### Task 2 (PR 2): Retire the Swing client

**Files:**
- Modify: root `pom.xml` (drop `<module>amiss-swing</module>`; drop the shade/vendored
  config if it lives only in the root for Swing's sake — verify first)
- Delete: `amiss-swing/` (whole module), `vendor-repo/` + `vendor/` (AbsoluteLayout was
  Swing-only — verify no other consumer), `scripts/build.ps1` + `scripts/run.ps1` if
  they only build/run the Swing jar (verify), `scripts/gen-placeholders.ps1` (Swing art)
- Maybe delete (verification-gated): `amiss-core` JDBC adapters
  (`amiss.infrastructure.persistence.jdbc.*`), `amiss.infrastructure.GameContext`,
  `amiss.infrastructure.config.Config` — Swing's composition path. KEEP
  `FlywayMigrator`, `PasswordHasher`, Flyway resources (used by amiss-api).
- Modify: `README.md`, `SETUP.md`, `ROADMAP.md` (Swing retired note in Backlog/UI epic),
  `.github/workflows/*.yml` (only if a job names amiss-swing)

**Steps:**
- [ ] `git grep -l "amiss-swing\|GameContext\|infrastructure.persistence.jdbc\|AbsoluteLayout"`
      outside `amiss-swing/` — build the definitive delete list from evidence, not this plan.
      `JdbcPooledModeTest` in amiss-core dies with the adapters it tests. FlywayMigrator's
      connection handling must not depend on deleted `Config`/`Jdbc` classes — read it first;
      if it does, move the needed pieces rather than keep the whole package.
- [ ] Delete module + dead code; fix root pom.
- [ ] `.\mvnw.cmd -B clean test` → BUILD SUCCESS, remaining test count noted in PR body.
- [ ] Boot check: `.\mvnw.cmd -B install -DskipTests` then run amiss-api; `GET /actuator/health` → UP.
- [ ] Docs sweep (README badges/run instructions, SETUP, ROADMAP note).
- [ ] Commit, push, PR; JIRA chore ticket In Progress + link.

---

### Task 3 (PR 3): Additive schema V5 + JPA for the new tables

**Files:**
- Create: `amiss-core/src/main/resources/db/migration/V5__saves_jobs_degrees.sql`
- Create in `amiss-api/src/main/java/amiss/api/persistence/jpa/`:
  `DegreeEntity.java`, `JobCatalogEntity.java` (maps `tbljob`), `SaveEntity.java`,
  `SaveDegreeEntity.java` (+ id class), `SaveTurndownEntity.java` (+ id class),
  and Spring Data repos `DegreeJpaRepository`, `JobCatalogJpaRepository`,
  `SaveJpaRepository`, `SaveDegreeJpaRepository`, `SaveTurndownJpaRepository`.
- Test: `amiss-api/src/test/java/amiss/api/persistence/jpa/SaveSchemaIT.java`
  (Testcontainers, singleton-container pattern, `@Transactional(NOT_SUPPORTED)`).

**Interfaces (produced for Task 4/5):**
- `JobCatalogEntity { Integer id; String job; String location; int wage;
  int reqExperience; int reqDependability; int reqClothing; }`
- `DegreeEntity { Integer id; String name; Integer prereqDegreeId; }`
- `SaveEntity { Long id; String owner; String label; Instant createdAt; Instant updatedAt;
  int xpos, ypos, time, round, cash, bank, debt, rent, eat, clothing;
  Integer jobId; int happiness, experience, dependability;
  Integer currentCourseId; int eduprog;
  int goalWealth, goalHappiness, goalEducation, goalCareer; boolean won; }`
- `SaveJpaRepository.findByOwnerOrderByUpdatedAtDesc(String owner)`.

**V5 content (normative — the full SQL ships in the migration):**
- `CREATE TABLE tbldegrees` / `tbljob` / `tbljob_degrees` / `tblsave` /
  `tblsave_degrees` / `tblsave_turndowns` exactly as the spec's data-model section.
- Seed `tbldegrees` (ids 1–11, prereqs): 1 Junior College (—), 2 Trade School (—),
  3 Business Administration (1), 4 Academic (1), 5 Electronics (2),
  6 Pre-Engineering (2), 7 Graduate School (4), 8 Engineering (6),
  9 Post-Doctoral (7), 10 Research (9), 11 Publishing (10).
- Seed `tbljob` with all 39 wiki rows (location strings verbatim from
  `domain.board.Location` display names — all 9 verified to match:
  Z-Mart, Monolith Burgers, QT Clothing, Socket City, Hi-Tech U, Factory, Bank,
  Black's Market, Rent Office). Uniform → req_clothing: Casual=1, Dress=2, Business=3.
  Wage/exp/dep per the wiki table in the spec's extraction
  (scratch copy in the spec; the authoritative rows are restated here):

  | # | Location | Job | Wage | Exp | Dep | Clothing | Degrees (ids) |
  |---|----------|-----|------|-----|-----|----------|----------------|
  | 1 | Z-Mart | Clerk | 5 | 10 | 10 | 1 | — |
  | 2 | Z-Mart | Assistant Manager | 7 | 20 | 20 | 2 | — |
  | 3 | Z-Mart | Manager | 8 | 30 | 30 | 3 | 1 |
  | 4 | Monolith Burgers | Cook | 5 | 0 | 10 | 1 | — |
  | 5 | Monolith Burgers | Clerk | 6 | 10 | 20 | 1 | — |
  | 6 | Monolith Burgers | Assistant Manager | 7 | 20 | 30 | 1 | — |
  | 7 | Monolith Burgers | Manager | 8 | 30 | 40 | 2 | 1 |
  | 8 | QT Clothing | Janitor | 6 | 10 | 20 | 1 | — |
  | 9 | QT Clothing | Salesperson | 8 | 30 | 30 | 2 | — |
  | 10 | QT Clothing | Assistant Manager | 9 | 40 | 40 | 3 | 1 |
  | 11 | QT Clothing | Manager | 12 | 50 | 50 | 3 | 3 |
  | 12 | Socket City | Clerk | 6 | 10 | 20 | 1 | — |
  | 13 | Socket City | Salesperson | 7 | 30 | 30 | 2 | — |
  | 14 | Socket City | Electronics Repairman | 11 | 40 | 40 | 1 | 5 |
  | 15 | Socket City | Manager | 14 | 40 | 40 | 3 | 5, 1 |
  | 16 | Hi-Tech U | Janitor | 5 | 10 | 10 | 1 | — |
  | 17 | Hi-Tech U | Teacher | 11 | 40 | 50 | 2 | 4 |
  | 18 | Hi-Tech U | Professor | 20 | 50 | 60 | 2 | 10 |
  | 19 | Factory | Janitor | 7 | 10 | 20 | 1 | — |
  | 20 | Factory | Assembly Worker | 8 | 30 | 30 | 1 | 2 |
  | 21 | Factory | Secretary | 9 | 40 | 40 | 2 | 1 |
  | 22 | Factory | Machinist's Helper | 10 | 40 | 40 | 1 | 6 |
  | 23 | Factory | Executive Secretary | 18 | 50 | 50 | 3 | 3 |
  | 24 | Factory | Machinist | 19 | 50 | 50 | 1 | 8 |
  | 25 | Factory | Department Manager | 22 | 60 | 60 | 3 | 1, 8 |
  | 26 | Factory | Engineer | 23 | 60 | 60 | 3 | 1, 8 |
  | 27 | Factory | General Manager | 25 | 70 | 70 | 3 | 3, 8 |
  | 28 | Bank | Janitor | 6 | 10 | 20 | 1 | — |
  | 29 | Bank | Teller | 10 | 40 | 40 | 2 | 1 |
  | 30 | Bank | Assistant Manager | 14 | 50 | 50 | 3 | 3 |
  | 31 | Bank | Manager | 19 | 60 | 60 | 3 | 3 |
  | 32 | Bank | Broker | 22 | 70 | 70 | 3 | 3, 4 |
  | 33 | Black's Market | Janitor | 6 | 10 | 10 | 1 | — |
  | 34 | Black's Market | Checker | 8 | 20 | 20 | 1 | — |
  | 35 | Black's Market | Butcher | 12 | 30 | 30 | 1 | 2 |
  | 36 | Black's Market | Assistant Manager | 15 | 40 | 40 | 2 | 1 |
  | 37 | Black's Market | Manager | 18 | 50 | 50 | 3 | 3 |
  | 38 | Rent Office | Groundskeeper | 7 | 10 | 20 | 1 | — |
  | 39 | Rent Office | Apartment Manager | 9 | 30 | 30 | 1 | 1 |

  (Escape `Black''s Market` in SQL. Reference-data tables are V5-owned:
  DELETE+INSERT like V2, so baselined installs converge.)
- Migrate accounts: `INSERT INTO tblsave (owner, label, xpos, ypos, time, round, cash,
  bank, debt, rent, eat, clothing, happiness, experience, dependability, goal_wealth,
  goal_happiness, goal_education, goal_career, won) SELECT u.name, 'Save 1', u.xpos,
  u.ypos, u.time, u.round, u.cash, u.bank, u.debt, u.rent, u.eat, u.clothing,
  s.happiness, 10, 20, 50, 50, 50, 50, FALSE FROM tbluser u JOIN tbluserstats s ON
  s.name = u.name;` (job NULL, no degrees — career/education reset per spec).
  Do NOT drop anything: legacy tables/columns stay until V6.

**Steps:**
- [ ] Write `SaveSchemaIT` first: migrate a clean container, assert 11 degrees + 39 jobs
      + degree-requirement rows (spot-check Broker → {3,4}, Cook → {}), insert a
      tbluser+tbluserstats pair before pointing Flyway at it in a second schema? — no:
      cover the copy rule by seeding a user in the test *before* running the app
      context is impractical under Flyway-at-startup; instead assert the SELECT-INSERT
      shape by inserting a user + stats row, running the V5 `INSERT…SELECT` statement
      text directly, and asserting the produced save row. Plus entity round-trips for
      each new repository.
- [ ] Run IT → fails (no migration/entities). Write V5, entities, repos. IT green.
- [ ] `.\mvnw.cmd -B clean verify` all green; boot the API against local MySQL97 (V5
      applies; app still runs on legacy paths).
- [ ] Commit, push, PR (stacked on PR 2 if unmerged); JIRA In Progress + link.

---

### Task 4 (PR 4): Save-scoped rules — the new core service layer

Built ALONGSIDE the legacy services; nothing existing is modified except adding new
files (plus `GameServices` untouched). All DB-free: new ports mocked in unit tests.

**Files (amiss-core):**
- Create ports in `application/port/`:
  - `SaveRepository` — load/store the full save state:
    `Optional<SaveState> find(long saveId)`, `void update(SaveState state)`,
    `List<SaveSummary> listByOwner(String owner)`, `long create(NewSave seed)`,
    `void delete(long saveId)`, `Optional<String> ownerOf(long saveId)`
  - `JobCatalog` — `Optional<JobSpec> byId(int jobId)`, `List<JobSpec> byLocation(String location)`,
    `List<JobSpec> all()`, `Set<Integer> requiredDegrees(int jobId)`
  - `DegreeCatalog` — `List<DegreeSpec> all()` (id, name, prereqId)
  - `SaveDegrees` — `Set<Integer> earned(long saveId)`, `void award(long saveId, int degreeId)`
  - `Turndowns` — `boolean isTurnedDown(long saveId, int jobId, int round)`,
    `void record(long saveId, int jobId, int round)`
- Create domain records in `domain/model/`: `SaveState` (mutable holder or record+wither
  — follow ActionCosts-era style: prefer a mutable class with explicit fields, it is
  updated wholesale via `SaveRepository.update`), `SaveSummary`, `NewSave`, `JobSpec`
  (id, name, location, wage, reqExperience, reqDependability, reqClothing),
  `DegreeSpec`, `GoalTargets`, `GoalProgress`.
- Create services in `application/service/save/`:
  - `HiringService.apply(SaveState s, int jobId)` → `HireOutcome`
  - `ShiftService.work(SaveState s)` → `ShiftOutcome`
  - `CourseService.available/enroll/study(SaveState s, …)` → outcomes
  - `GoalService.progress(SaveState s)` → `GoalProgress`; `GoalService.checkWin(SaveState s)`
  - `WeekService.rollover(SaveState s)` (clock reset + food + rent + dep −3 + win check)
  - `SaveGameServices` composition root (ports + `IntBinaryOperator`-style injected RNG:
    `IntSupplier roll1to100`).
- Test: `amiss-core/src/test/java/amiss/application/service/save/…Test.java` per service.

**Key algorithms (normative code):**

```java
// caps — recomputed on hire and used on every paying shift
static int maxExperience(JobSpec job, int degrees) { return 10 + job.reqExperience() + 5 * degrees; }
static int maxDependability(JobSpec job, int degrees) { return 20 + job.reqDependability() + 5 * degrees; }

// hiring luck; roll1to100 > luck  =>  NO_OPENINGS
static int luck(int dependability, int experience, int degrees) {
    return 30 + (10 + dependability + experience + 8 * degrees) / 3;   // int division
}

// apply check order (Cook @ Monolith Burgers: jobId 4 short-circuits to HIRED)
// 1. missing degrees            -> NOT_ENOUGH_EDUCATION      (collect, don't return yet)
// 2. exp < reqExp               -> NOT_ENOUGH_EXPERIENCE     (collect)
// 3. dep < effectiveReqDep      -> POOR_WORK_HISTORY, where effectiveReqDep =
//        (job.reqDependability() == 10 ? 0 : job.reqDependability());
//        if round <= 4 this failure is reported as NO_OPENINGS instead (collect)
// any collected failures -> rejected with the full list; 4h charged; happiness -1
// 4. turndown check (same round) -> NO_OPENINGS
// 5. roll1to100 > luck(...)      -> NO_OPENINGS + record turndown; 4h charged; happiness -1
// hired: wage = job.wage(); experience += 2 (cap-exempt); if (dep < 10) dep = 10;
//        happiness += 3; 4h charged.

// shift (at job's location, uniform ok):
// if (dep < effectiveReqDep - 5) -> FIRED: jobId=null, happiness -= 3, no time charged
// warning = dep >= effectiveReqDep - 5 && dep <= effectiveReqDep - 3
// hoursWorked = min(6, hoursRemaining); pay = job.wage() * 8 * hoursWorked / 6; // int
// garnish rule unchanged (half + R2 interest when in debt)
// if pay > 0: exp += (exp < maxExp ? 1 : 0); dep += (dep < maxDep ? 1 : 0)

// graduation: award degree; dep += 5 (NOT capped); education derived, never stored
static int educationStat(int degrees) { return 1 + 9 * degrees; }
static int careerStat(boolean employed, int dependability) {
    return employed ? (int) Math.floor(1.25 * dependability) : 0; }
static int wealthStat(int cash, int bank) { return (cash + bank) / 100; }

// week rollover: dep = max(0, dep - 3); then win = won || all four stats >= targets
```

**Test list (write first, one behaviour each):** luck boundary (roll == luck hires,
roll == luck+1 refuses), start-luck 43 / post-Cook 44 / all-degrees floor 66
reproduction, check-order collection (edu+exp both reported), weeks 1–4 dep masking
(and week 5 unmasking), reqDep-10-means-0, Cook bypass, turndown persists within round
and clears across rounds, hire side-effects (+2 exp over cap, dep floor 10, +3
happiness), rejection charges 4h and −1 happiness, fired boundary (dep == req−6 fired,
== req−5 works with warning, == req−2 no warning), pro-rated pay (6h→wage×8, 3h→wage×4,
0h→week-over), exp/dep cap stops increment, graduation +5 dep over cap, education/career/
wealth stat functions, rollover decay floor 0, win requires all four and is sticky.

**Steps:** per service: failing tests → implement → green → commit. Finish with
`.\mvnw.cmd -B clean test` (whole reactor) and PR.

---

### Task 5 (PR 5): API cutover — saves CRUD, routes, hidden listings, V6

**Files (amiss-api unless noted):**
- Create: `web/SaveController.java` (`GET/POST /api/saves`, `DELETE /api/saves/{id}`),
  `web/dto/SaveSummaryDto.java`, `web/dto/CreateSaveRequest.java`
  (label + goals {wealth,happiness,education,career} 10–100 or `random:true`),
  `web/SaveScope.java` (replaces PlayerScopeFilter: resolve save → owner == JWT subject,
  else 403; unknown save 404).
- Create: JPA adapters implementing the Task-4 ports over the Task-3 repositories
  (`persistence/adapter/…`).
- Modify: every controller — routes `/api/players/{username}/…` → `/api/saves/{saveId}/…`,
  backed by `SaveGameServices`; `EmploymentController.jobs()` → returns
  `{id, name, location, wage}` only, optional `?location=`; apply takes `{jobId}`;
  work response gains `warning`/`fired`; university endpoints take degree ids and list
  EARNED/AVAILABLE/LOCKED; state assembler → save-centric DTO with goals
  {current,target,met}×4 + `won`, degrees list, NO experience/dependability fields.
- Delete: highscores endpoint + DTO, legacy services/ports (`JobService`,
  `EducationService`, `UniversityService`, `StatsService` work/education parts,
  `GameServices`, `GameServicesFactory`, old entities for dropped shapes) — the compiler
  is the checklist; nothing legacy survives that reads dropped columns.
- Create: `amiss-core/src/main/resources/db/migration/V6__drop_legacy_state.sql` —
  `DROP TABLE tbluserstats; DROP TABLE tbljobs;` + `ALTER TABLE tbluser` dropping
  xpos, ypos, `time`, cash, round, job, clothing, rent, eat, debt (name+password remain).
- Tests: MockMvc per controller; ownership 403/404; **pin the hidden-requirements
  contract** (serialize job listing + save state, assert JSON contains none of
  `reqExperience|reqDependability|reqClothing|experience|dependability` keys);
  Testcontainers IT: V1→V6 on clean DB + a seeded pre-V5 user surviving to a save.

**Steps:** adapters+ITs → controllers+slices → V6 → full `clean verify` → boot & smoke
(register, create save, apply Cook, work, enroll, rollover) via curl/Invoke-RestMethod →
PR (stacked on PR 4).

---

### Task 6 (PR 6): SPA cutover — saves, goal setup, employment flow, win

**Files (frontend/src):**
- Create: `pages/SavesPage.tsx` (+test) — list/continue/delete(confirm)/new;
  `game/NewGameSetup.tsx` (+test) — four 10–100 sliders + Randomise + start;
  `game/WinBanner.tsx` (+test) — first `won` transition → victory overlay, dismissible,
  play continues.
- Modify: `api/*.ts` → `/api/saves/{saveId}` paths + new DTO types (`types.ts`);
  `App.tsx`/routing (`/saves`, `/play/:saveId`); query keys `['save', saveId]`;
  `Hud.tsx` — goal bars vs per-save targets, degrees shown, no exp/dep anywhere;
  `panels/EmploymentOfficePanel.tsx` — two-step (workplace grid → jobs with `R{wage}/h`,
  all Apply buttons enabled, officer rejection copy incl. multi-reason and No-Openings);
  `panels/UniversityPanel.tsx` — course tree earned/available/locked + prereq labels.
- Delete: highscores api/page/nav, `HomePage` leaderboard.
- Tests: Vitest for each new screen + updated suites; the employment panel test asserts
  no requirement text/disabled state ever renders.

**Steps:** api/types first → screens with tests → route plumbing → full `npm test` +
live loop in Chrome (create save → hire Cook → work → study → graduate → win a
10/10/10/10-goal save) → PR (stacked on PR 5, merged straight after it).

---

## Self-review notes

- Spec coverage: every spec section maps to a task (schema→3, mechanics→4, API→5,
  frontend→6, Swing→2, docs/JIRA→1). Raises/economy explicitly out of scope.
- The V5 IT strategy tests the `INSERT…SELECT` statement directly because Flyway runs
  at context start — keeps the copy rule covered without a second migration path.
- Type names used across tasks are consistent: `JobSpec`, `SaveState`, `SaveGameServices`,
  `HireOutcome`, `ShiftOutcome` (Task 5 consumes exactly these).
- PR 4/5 split is expand/contract at the code level: PR 4 adds, PR 5 rewires+deletes;
  each PR alone leaves develop green and bootable.
