# KAN-54 API Cutover — Remaining Steps (Advisor-Run) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **This plan is written for a lower-cost executor model (Sonnet-class) driving
> cheap implementer subagents, with strategic decisions escalated to a Fable
> advisor.** Read the Advisor Protocol section before Task 0 and obey it for the
> whole run.

**Goal:** Finish PR 5 (`feat/saves-employment-api`, KAN-54): legacy purge + register
rewrite + V6 contract migration + migration IT + verified PR to `develop`.

**Architecture:** The V5 expand phase (save slots + job/degree catalogs) and the
save-scoped core services already shipped; Step A (web-layer cutover to
`/api/saves/{saveId}/…`) is implemented by a prior subagent. What remains is the
**contract phase**: delete every legacy reader of the old per-user game-state shapes
(compiler as the checklist), shrink `tbluser` to credentials via `V6`, prove the
V1→V6 path with a Testcontainers migration IT, then verify + PR.

**Tech Stack:** Java 21, Spring Boot 3 (amiss-api), framework-free amiss-core,
Flyway (schema owner, `ddl-auto=validate`), MySQL 9 / Testcontainers, Maven
reactor via `.\mvnw.cmd`, PowerShell 5.1 host.

**Canonical documents:**
- Parent plan: `docs/superpowers/plans/2026-07-07-jobs-degrees-saves.md` (Task 5, lines 287–317)
- Spec: `docs/superpowers/specs/2026-07-07-jobs-degrees-saves-design.md` (API section, lines 150–173)
- Ledger (authoritative progress): `.superpowers/sdd/progress.md`
- Session log: `tasks/todo.md` → "Session 2026-07-11 — PR 5 execution"

## Global Constraints

- Branch `feat/saves-employment-api`; PR targets `develop` (`gh pr create --base develop`). Only Rourke merges.
- amiss-core stays Spring-free. Flyway owns the schema; JPA runs `ddl-auto=validate` — every entity must match the post-V6 schema or boot fails.
- Wire contract: serialized job listings and save-state JSON must match NONE of `reqExperience|reqDependability|reqClothing|experience|dependability` (MockMvc regex pins exist after Step A — keep them green).
- Charged rejections are HTTP 200 outcomes with state, never errors (established contract). Ownership: unknown save → 404, other owner → 403 `urn:amiss:forbidden`; all errors RFC 7807.
- Post-V6, `tbluser` keeps **name + password only**. `bank` IS dropped (resolved deviation: parent plan omitted it; the 2026-07-11 handoff confirmed `tbluser.bank` came from V4 and `tblsave` has its own — the handoff governs).
- `tblhelp` TABLE stays; all Help **code** (port/adapter/entity/repo/tests) goes. Note it in the PR body.
- NEVER delete anything under `amiss-core/.../application/service/save/` — the legacy classes being purged include same-named classes (`BankService`, `RentService`, `TravelService`) in the parent package `application/service/`. Check the package line before every deletion.
- After ANY amiss-core change: `.\mvnw.cmd -B -pl amiss-core install -DskipTests` before an amiss-api module run will see it.
- Git: stage explicit paths only (never `git add -A`/`git add .`); message via temp file + `git commit -F <file>`; every message ends with the exact trailer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`; verify (separate command) BEFORE commit — never chain build|grep && commit && push.
- ITs need Docker Desktop running. `disabledWithoutDocker` makes them SKIP silently — after every `verify`, confirm the failsafe summary shows ITs RAN (non-zero count), not just "green".
- PR 5 breaks the SPA by design; PR 6 (KAN-45 frontend) is stacked and merged back-to-back — say so in the PR body.

---

## Advisor Protocol (Anthropic advisor pattern)

The executor (you, Sonnet-class) owns mechanical execution. **Fable owns strategy.**
You consult the advisor at the three hard gates below and on any escalation
trigger. If this plan is being executed inside the original Fable session, the
"consult" is simply Fable reasoning inline; in any other session, consult by
dispatching a synchronous advisor subagent:

```
Agent tool:
  subagent_type: "general-purpose"
  model: "fable"
  run_in_background: false
  description: "Advisor consult: <gate/trigger>"
  prompt: <the consult packet below, verbatim-filled>
```

**Consult packet format (fill every field):**

```
You are the strategic advisor for an in-flight execution of
docs/superpowers/plans/2026-07-11-kan54-api-cutover-advisor-run.md (read it,
plus .superpowers/sdd/progress.md, before answering).

GATE/TRIGGER: <name>
DECISION NEEDED: <one sentence>
OPTIONS: A) <…> B) <…> [C) …]
EVIDENCE: <file:line refs, command outputs, and paths to diff/report files —
           hand large artifacts as file paths, do not paste them>
EXECUTOR RECOMMENDATION: <option + one-paragraph why>
CONSTRAINT CHECK: <which Global Constraint / spec line bears on this>

Respond with exactly: DECISION (one of the options or a new one), RATIONALE
(short), CONSTRAINTS TO PROPAGATE (anything the remaining tasks must now honor).
```

Log every consult and its DECISION in `.superpowers/sdd/progress.md`.

**Hard gates (mandatory consults):**
1. **Gate A — Step A acceptance** (end of Task 1): advisor reads the review
   package + task-reviewer verdict and rules fix/proceed.
2. **Gate B — V6 pre-commit** (inside Task 3): destructive DDL is one-way;
   advisor reviews the migration file + entity diff before it is committed.
3. **Gate C — final whole-branch review** (end of Task 4): the SDD final review
   must run on the most capable model — that IS the advisor consult; include the
   whole-branch review package path and the ledger's Minor-findings list.

**Escalation triggers (consult immediately, any task):**
- A "legacy" deletion target turns out to have a live caller the plan didn't predict.
- A required capability is missing from `SaveGameServices` or the save ports.
- Plan text contradicts the code you find (signatures, schema, test names).
- A test failure survives 2 focused fix attempts, or a failure implicates auth
  semantics beyond the specified register rewrite.
- Anything would change shipped game-rule behaviour. (Advisor may escalate to Rourke.)

**Decision rights:**

| Decide yourself (executor) | Advisor (Fable) | Human (Rourke) |
|---|---|---|
| Commit slicing, test data values, naming that follows existing conventions | Scope/contract questions, deletion surprises, IT strategy changes, plan-vs-code conflicts, Gates A–C | Merging PRs, game-rule behaviour changes, anything the advisor flags as design |
| Ordinary red-test fix loops (≤2 attempts) | Retry-vs-redesign after failed fix loops | Approving deferred items |

---

### Task 0: Reconcile state (no code)

**Files:** read-only.

- [ ] **Step 1:** Read `.superpowers/sdd/progress.md` and run:
  `git log --oneline -8` and `git status --short`
  Expected baseline: commits `35f3517` (core save services) and `8838afd` (JPA
  adapters) exist; docs edits (`tasks/lessons.md`, `tasks/cv-highlights.md`,
  `tasks/todo.md`, possibly `.superpowers/`) may be uncommitted — leave them;
  they are committed in Task 5.
- [ ] **Step 2:** Determine Step A status. Step A (web cutover) was dispatched to
  a background subagent on 2026-07-11 with brief/report at the session scratchpad
  (`…\scratchpad\step-a-brief.md` / `step-a-report.md`). If the ledger already
  says "Step A: complete (review clean)" → skip Task 1 entirely. If Step A
  commits exist but no review is logged → do Task 1. If NO Step A commits exist
  → escalation trigger: consult the advisor before implementing Step A yourself
  from `step-a-brief.md` (the brief is the requirements; the advisor confirms
  nothing else landed).

### Task 1: Step A acceptance review (Gate A)

**Files:**
- Read: the Step A report file; the diff range BASE=`8838afd` → the last Step A commit.

- [ ] **Step 1:** Generate the review package (from the subagent-driven-development
  skill directory): `scripts/review-package 8838afd <stepA-head>` — record the
  printed path.
- [ ] **Step 2:** Dispatch a task-reviewer subagent (model: sonnet) per the SDD
  skill's `task-reviewer-prompt.md`, handing it: the Step A brief path, the
  report path, the review-package path, and the Global Constraints block above
  verbatim (especially the hidden-fields regex and the 200-charged-rejection
  contract).
- [ ] **Step 3:** **Gate A consult** — send the advisor the reviewer's verdict +
  package path. DECISION = accept / fix-list.
- [ ] **Step 4:** If fixes: dispatch ONE fix subagent (model: sonnet) with the
  complete findings list; it re-runs the covering slice tests and appends results
  to the report; re-run Steps 1–3 on the new range. Then mark Step A complete in
  the ledger with its commit range.

### Task 2: Legacy deletion sweep (compiler is the checklist)

No schema change in this task — the build must be green on the V5 schema when it ends.

**Files:**
- Delete (amiss-core, `application/service/` — NOT `service/save/`): `TimeService`,
  `StatsService`, `JobService`, `EducationService`, `UniversityService`,
  `FoodService`, `BankService`, `RentService`, `TravelService`, `TurnService`,
  `GameServices`, plus their outcome records (`ActionResult`, `WorkOutcome`,
  `EatOutcome`, `PurchaseOutcome`, `ApplyOutcome`, `EnrollOutcome`,
  `StudyOutcome`, `TimeSpend`, `WeekSummary`, `MoveResult`, `BankTransaction`,
  `RentPayment` — the versions in the LEGACY packages only; glob each name and
  check the `package` line).
- Delete (amiss-core): `domain/model/User`; ports `UserStatsRepository`,
  `JobRepository`; the Help port; every test of the above.
- Delete (amiss-api): `HighscoresController` + its DTO + `HighscoresControllerTest`
  + the `GET /api/highscores` permitAll row in `SecurityConfig` + its
  `SecurityRulesTest` rows; `GameServicesFactory` (+ test); JPA `UserStatsEntity`,
  `JobEntity`, `HelpEntity` (+ their Spring Data repos + adapters + adapter tests
  + their rows in the Testcontainers port ITs).
- Modify: `PersistenceConfig` (remove deleted beans), any straggler the compiler names.

- [ ] **Step 1:** Confirm zero live callers before deleting: for each class above run
  `Grep` for its simple name across `amiss-core/src amiss-api/src frontend/src`
  — expected hits only in the files being deleted and (for Help) nowhere at all.
  Any unexpected live caller = escalation trigger.
- [ ] **Step 2:** Delete in dependency order (tests → controllers/factory →
  services → ports/entities/adapters), running
  `.\mvnw.cmd -B -pl amiss-core install -DskipTests` then
  `.\mvnw.cmd -B -pl amiss-api -am clean test` after each coherent slice; let
  compiler errors name the next file. Expected end state: BUILD SUCCESS, 0 failures.
- [ ] **Step 3:** Verify the wire pins still pass (they live in Step A's slices) and
  that `grep -r "highscores" amiss-api/src frontend/src` finds nothing in amiss-api
  main/test (frontend hits are PR 6's problem — leave them).
- [ ] **Step 4:** Commit (explicit paths):
  `chore: delete legacy player-state services, ports and highscores (KAN-54)`
  — body notes the tblhelp decision: code deleted, table kept.

### Task 3: Register rewrite + tbluser contract (V6) — includes Gate B

**Files:**
- Modify: `AuthService` (register path), `UserRepository` port (amiss-core),
  its JPA adapter + `UserEntity` + Spring Data repo (amiss-api),
  `AuthRoundTripIT`, `SecurityLockdownIT` (they currently exercise the
  game-state insert path).
- Create: `amiss-core/src/main/resources/db/migration/V6__drop_legacy_state.sql`.

**Interfaces — normative end-state (read the current files first; adapt names
only where the current code differs mechanically):**

```java
public interface UserRepository {
    Optional<String> findPasswordHash(String name);
    void updatePassword(String name, String hash);
    void insertNewUser(String name, String hash);   // credentials only
}
```

`UserEntity` maps exactly `name` (id) + `password`. Registration persists one
row with those two columns and NOTHING else (no stats row, no game-state
columns). Login/rehash behaviour is unchanged.

- [ ] **Step 1 (RED):** Update `AuthRoundTripIT`: replace any assertion about
  game-state seeding (tbluserstats row, cash/time defaults) with: after
  register, the user can log in AND `SELECT * FROM tbluser WHERE name=?` yields
  only name+password values. Run
  `.\mvnw.cmd -B -pl amiss-api -am verify -Dit.test=AuthRoundTripIT` — expected:
  FAIL (old insert still writes game state / entity still maps old columns).
  (Start Docker Desktop first; confirm the IT ran, not skipped.)
- [ ] **Step 2 (GREEN):** Shrink the port, adapter, entity and register path to the
  normative shape. Write the migration exactly:

```sql
-- V6__drop_legacy_state.sql
-- Contract phase of the V5 expand / V6 contract pair (KAN-54).
-- Per-save state lives in tblsave (V5); tbluser shrinks to credentials.
DROP TABLE tbluserstats;
DROP TABLE tbljobs;
ALTER TABLE tbluser
  DROP COLUMN xpos,
  DROP COLUMN ypos,
  DROP COLUMN `time`,
  DROP COLUMN round,
  DROP COLUMN cash,
  DROP COLUMN bank,
  DROP COLUMN debt,
  DROP COLUMN rent,
  DROP COLUMN eat,
  DROP COLUMN clothing,
  DROP COLUMN job;
```

  Before finalizing: check `V1__baseline_schema.sql` (+V4/V5) for FK constraints
  touching these tables/columns (e.g. tbluserstats→tbluser cascade is fine to
  drop as a child table; if `tbluser.job` carries an FK to `tbljobs`, add
  `ALTER TABLE tbluser DROP FOREIGN KEY <name>;` before the table drop). Do NOT
  touch `tblhelp`.
- [ ] **Step 3:** Run `.\mvnw.cmd -B -pl amiss-core install -DskipTests`, then
  `.\mvnw.cmd -B -pl amiss-api -am verify -Dit.test=AuthRoundTripIT,SecurityLockdownIT,SavePortsAdapterIT`
  — expected: PASS, ITs ran (failsafe count > 0). Fix `SecurityLockdownIT`
  references to removed routes the same way Task 2 handled `SecurityRulesTest`.
- [ ] **Step 4: Gate B consult** — hand the advisor the V6 file content, the
  entity/port diff (`git diff` path range as a file), and the IT output summary.
  Only after DECISION=proceed:
- [ ] **Step 5:** Commit: `feat: register writes credentials only + V6 drops legacy state (KAN-54)`.

### Task 4: Migration IT — V1→V6 + pre-V5 survival; SaveSchemaIT reconcile; Gate C

**Files:**
- Create: `amiss-api/src/test/java/amiss/api/persistence/jpa/LegacyToSaveMigrationIT.java`
  (same package/support pattern as the existing Testcontainers ITs — reuse the
  singleton-container support class).
- Modify: `SaveSchemaIT` — its `COPY_SQL` reads legacy tables, which V6 drops.

- [ ] **Step 1:** Read `SaveSchemaIT` fully. Decide with this rule: the new IT
  below supersedes any copy-verification `COPY_SQL` performs by re-running V5's
  copy against legacy tables. If `SaveSchemaIT` still has other assertions, keep
  them and delete only the legacy-reading parts; if it becomes empty, delete the
  class. If reality doesn't match this description — escalation trigger.
- [ ] **Step 2 (RED then GREEN):** Write the IT (plain Flyway API, no Spring
  context; singleton container):

```java
@Testcontainers @DisabledIfDockerUnavailable  // match existing IT idioms
class LegacyToSaveMigrationIT {
    // 1) Flyway.configure().target(MigrationVersion.fromVersion("4")) → migrate()
    // 2) seed a legacy user:
    //    INSERT INTO tbluser (name, password, xpos, ypos, `time`, round, cash,
    //                         bank, debt, rent, eat, clothing, job)
    //    VALUES ('prev5user', '<any bcrypt hash>', …plausible values…);
    //    plus its tbluserstats row (copy the column list from V1).
    // 3) new Flyway config, no target → migrate() (runs V5 + V6)
    // 4) assert: tblsave has a row whose owner='prev5user' carrying the copied
    //    state; tbluser row still exists with ONLY name+password columns
    //    (information_schema.columns count == 2); tbluserstats and tbljobs do
    //    not exist; tblhelp DOES exist.
}
```

  Run: `.\mvnw.cmd -B -pl amiss-api -am verify -Dit.test=LegacyToSaveMigrationIT`
  — expected: PASS, and the failsafe summary proves it ran. (If V5 turns out NOT
  to copy legacy state into tblsave, that changes assertion 4 — escalation
  trigger, do not invent a copy step.)
- [ ] **Step 3:** Full reactor: `.\mvnw.cmd -B clean verify` — expected: BUILD
  SUCCESS, 0 failures, IT count > 0. Commit:
  `test: V1→V6 migration IT with pre-V5 user survival (KAN-54)`.
- [ ] **Step 4: Gate C — final whole-branch review.** Build the package:
  `scripts/review-package $(git merge-base develop HEAD) HEAD`, then consult the
  advisor with the package path, the ledger's Minor-findings list, and the spec
  path. The advisor performs the final review (superpowers:requesting-code-review
  rubric). If findings: ONE fix subagent with the complete list, re-verify,
  re-consult with the delta.

### Task 5: Live smoke, docs, PR (Step D)

- [ ] **Step 1:** Ensure MySQL97 service is running (`Get-Service MySQL97`; if
  stopped, ask Rourke to start it — needs elevation). Boot:
  `.\mvnw.cmd -B -pl amiss-api spring-boot:run` in background; wait for health:
  `Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health` → UP
  (V5+V6 apply to the live DB at boot — this is expected and one-way; the live
  DB is a dev instance).
- [ ] **Step 2:** Smoke the loop with `Invoke-WebRequest -UseBasicParsing` (or the
  Bash tool + curl if simpler): register `kan54smoke` → login (capture token) →
  `POST /api/saves` (goals or random) → `GET /api/saves` → `GET /api/jobs?location=…`
  (assert response JSON has no requirement/experience keys) → apply for Cook by
  jobId → work → enroll (degree id) → study → end-week rollover → `GET` save
  state (goals {current,target,met} + won present). Then clean up as root:
  `DELETE FROM tblsave WHERE owner='kan54smoke'; DELETE FROM tbluser WHERE name='kan54smoke';`
  Kill the server by port owner (`Get-NetTCPConnection -LocalPort 8080 -State Listen`
  → `Stop-Process -Id …`) — never by process name.
- [ ] **Step 3:** Docs + bookkeeping commit (explicit paths:
  `tasks/lessons.md tasks/cv-highlights.md tasks/todo.md`):
  tick Step boxes in the session block, add the PR 5 section to
  `tasks/cv-highlights.md` (bullets: expand/contract completed with V6; ~IDOR-proof
  save scoping; hidden-information enforced as an absent-from-the-wire contract
  pinned by tests; migration IT proving a pre-V5 user survives). Message:
  `docs: lessons distillation, CV highlights, session log (KAN-54)`.
- [ ] **Step 4:** Push and open the PR:
  `git push -u origin feat/saves-employment-api` then `gh pr create --base develop`
  with a body covering: route cutover + SaveScope, hidden-requirements pins,
  legacy purge inventory, V6 (one-way; bank included — deviation note), tblhelp
  code-deleted/table-kept, migration IT, **"PR 6 (KAN-45) is stacked and must
  merge back-to-back — this PR breaks the SPA until then"**, and the standard
  🤖 footer. Watch checks (`gh pr checks --watch`); fix reds via a fix subagent.
- [ ] **Step 5:** JIRA: comment the PR link on KAN-54 (Atlassian MCP tools; if
  unavailable in this session, list it as a leftover for Rourke). Update the
  ledger: all tasks complete + PR number. Report to Rourke: PR link, deviations,
  leftovers. Do NOT merge.
