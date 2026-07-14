# KAN-55 Architecture Docs Refresh — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `docs/ARCHITECTURE.md` to describe the current full-stack reactor (amiss-core / amiss-api / frontend / amiss-coverage) with a Mermaid diagram, sweep the stale references the KAN-54 Gate C review deferred, and add a small player-facing `docs/GAMEPLAY.md` covering the KAN-48 economy odds.

**Architecture:** Docs-only PR (plus comment-only Javadoc edits — zero behaviour change). One branch `docs/kan55-architecture-refresh` off `develop`, one PR. The ticket description is the spec; the facts below were verified against the working tree at `5a82adb` (develop, all KAN-48 PRs merged) on 2026-07-14.

**Tech Stack:** Markdown, Mermaid (GitHub-rendered), Javadoc.

## Global Constraints

- Docs and comments only. No line of executable code, config, SQL, or test logic changes.
- Historical narrative in ROADMAP.md phase entries stays (it records what was true when shipped); only *forward-looking* or *current-state* prose gets corrected, via short parentheticals.
- Commit messages via `git commit -F <file>` (ASCII), ending with the `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` trailer.
- PR targets `develop`. CV highlights entry required before opening the PR (CLAUDE.md rule 6).
- Acceptance (ticket): a newcomer can locate where a rule lives and where its endpoint/panel is from the doc alone; diagram renders on GitHub; no references to deleted code remain.

## Verified facts the docs must state (source of truth for all tasks)

**Reactor:** `amiss-core` (plain Java 21 rules; no Spring), `amiss-api` (Spring Boot 3.5 REST), `amiss-coverage` (JaCoCo aggregate), plus non-Maven `frontend/` (Vite + React 19 + TS SPA).

**amiss-core packages** (all services live in `application/service/save`; the old flat `application/service` files were retired with KAN-54):
- `domain/model`: ClothingItem, DegreeSpec, FastFoodItem, FoodPack, JobListing, JobSpec, SaveState, UserGoals (**live** — per-save goals; do NOT describe as deleted, the ticket text lagged here)
- `domain/board`: Board, Location (13-stop ring)
- `domain/validation`: Validation
- `application/port`: UserRepository, SaveRepository, JobCatalog, DegreeCatalog, SaveDegrees, Turndowns (+ PersistenceFailureException) — the old UserStatsRepository/JobRepository/HelpRepository ports are gone (KAN-54)
- `application/service/save`: BankService, CourseService, EconomyService, GoalService, HiringService, RentService, SaveGameServices, ShiftService, ShopService, TravelService, WeekRolloverService, StatFormulas + outcome records (BankTransaction, EatOutcome, EconomyEvent, HireOutcome, MoveResult, PurchaseOutcome, RentPayment, ShiftOutcome)
- `application/config`: ActionCosts
- `infrastructure/security`: PasswordHasher (BCrypt)

**amiss-api:** `web/` controllers (Auth `/api/auth`, Save `/api/saves`, Player `/api/saves/{saveId}`, Bank, Rent, Employment, University, Food, Board `/api/board`) + GlobalExceptionHandler (RFC 7807) + SaveScope (per-request save ownership, 403 IDOR guard) + LocationGuard + PlayerStateAssembler; `web/dto/` (39 DTOs); `security/` (SecurityConfig, AuthService — HS256 JWT); `persistence/jpa/` (8 entities, 6 Spring Data repos, 6 `Jpa*` port adapters); `config/` (PersistenceConfig = composition root, CostsConfig/CostsProperties).

**frontend/src:** `api/` (typed fetch modules per surface + `http.ts` RFC-7807/JWT plumbing), `auth/` (AuthContext, tokenStore), `game/` (BoardScreen, Hud, EndWeekModal, NewGameSetup, WinBanner, WorkAction, `panels/` registry — one PanelProps component per board stop, DefaultPanel fallback), `pages/`, `routes/RequireAuth`.

**Migrations:** V1 baseline, V2 seed, V3 time-to-minutes, V4 bank, V5 saves/jobs/degrees (expand), V6 drop legacy state (contract), V7 economy state, V8 wage snapshot. `ddl-auto=validate`; runtime `amiss` account vs `amiss_migrator` DDL account.

**Known gaps to state honestly:** no `@Version` optimistic locking on SaveEntity (same-save double-submit race on money ops — 2026-07-12 audit, chore ticket planned pre-KAN-49); no API versioning.

**Economy odds (from `EconomyService.java`, for GAMEPLAY.md):** price = `base + base*Reading/60`, Reading −30..+90 ⇒ 50–250% of base. Weekly drift: Index += uniform(−1..+1) clamped ±3; Reading += 10×Index + uniform(−5..+5), clamped. Events only from week 8; crash requires Reading ≥ 80 AND a 1/31 roll; severity uniform ⅓ Minor/Moderate/Major = prices −5/−10/−15% (Reading −3/−6/−9), happiness −1/−2/−3, Index pinned to −3; Moderate: employed players coin-flip fired vs pay cut to 80%; Major: fired + bank wiped. If no crash fires, boom rolls 1/31: prices +10% (Reading +6), Index pinned to +3.

---

### Task 1: Branch + rewrite `docs/ARCHITECTURE.md`

**Files:**
- Modify: `docs/ARCHITECTURE.md` (full rewrite)

- [ ] **Step 1:** `git checkout -b docs/kan55-architecture-refresh develop`
- [ ] **Step 2:** Rewrite the doc with these sections (use the verified facts above; keep the doc ≤ ~250 lines):
  1. **Intro** — what the system is now (SPA → REST API → plain-Java rules → MySQL); one line of history (Swing origin, retired KAN-51).
  2. **Mermaid diagram** — `flowchart LR` with subgraphs frontend / amiss-api / amiss-core / MySQL+Flyway; arrows show the dependency rule (web → services → ports ← JPA adapters; SPA → HTTP only).
  3. **The layers & the dependency rule** — table mapping layer → package → responsibility.
  4. **Maven reactor** — module table incl. `frontend/` as the non-Maven fourth surface.
  5. **Package tour** — the trees above, amiss-core / amiss-api / frontend.
  6. **One request end-to-end** — `POST /api/saves/{id}/work`: JWT filter → SecurityConfig → SaveScope ownership check → EmploymentController → ShiftService (core, wage snapshot + economy) → SaveRepository port → JpaSaveRepository → MySQL; ShiftOutcome → WorkResponse DTO → SPA invalidates `['save', saveId]`.
  7. **Persistence strategy** — Flyway owns schema, validate-only JPA, expand/contract with V5→V6 as the worked example, least-privilege accounts.
  8. **Ports: the load-bearing seam** — keep (rewrite) the existing rationale incl. PersistenceFailureException; proven by KAN-34 swap + KAN-51 Swing deletion + KAN-54 port reshape.
  9. **Known gaps** — @Version, API versioning.
- [ ] **Step 3:** Verify: `git grep -nE "GameContext|GameServicesFactory|UserStatsRepository|JobRepository|HelpRepository|TimeService|GameServices[^F]" docs/ARCHITECTURE.md` returns nothing (UserGoals is allowed — it's live). Confirm the Mermaid block is fenced ` ```mermaid `.
- [ ] **Step 4:** Commit `docs: rewrite ARCHITECTURE.md for the full-stack reactor (KAN-55)`.

### Task 2: Stale-reference sweep (KAN-54 Gate C deferred items)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/domain/model/JobListing.java:3` (tbljobs → tbljob_catalog)
- Modify: `amiss-api/src/main/java/amiss/api/error/WrongLocationException.java:15` (tbljobs → tbljob_catalog)
- Modify: `amiss-api/src/main/java/amiss/api/web/dto/JobDto.java:5` (tbljobs → tbljob_catalog)
- Modify: `amiss-api/src/main/java/amiss/api/config/PersistenceConfig.java:35,74` (drop stale UserStatsRepository/GameServicesFactory mentions from comments)
- Modify: `SETUP.md:192` (GameContext mention → current composition root)
- Modify: `README.md` — refresh the two "Phase 3, in progress" staleness spots (lines 25, 129–136), link `docs/ARCHITECTURE.md` from Project structure, link `docs/GAMEPLAY.md`
- Modify: `ROADMAP.md` — tick the web-frontend item (line 139, KAN-19 done); parenthetical on line 128's port list noting the KAN-54 reshape
- Leave: `MySqlITSupport.java:41` (accurate at test time — V1..V5 tables exist until V6 runs)

- [ ] **Step 1:** Make the comment-only edits above.
- [ ] **Step 2:** Verify compile is unaffected: `.\mvnw -q -B -pl amiss-core,amiss-api compile` → BUILD SUCCESS.
- [ ] **Step 3:** Commit `docs: sweep stale tbljobs/UserStats/GameContext references (KAN-55)`.

### Task 3: `docs/GAMEPLAY.md` — economy odds

**Files:**
- Create: `docs/GAMEPLAY.md`

- [ ] **Step 1:** Write a short player-facing doc: how prices work (one formula + a worked example), the weekly drift in plain words, and an odds table for crash/boom (the Economy odds facts above — when they can happen, per-week chance, severity effects). Cite `EconomyService.java` as the source and the wiki as the design reference.
- [ ] **Step 2:** Verify every number against `amiss-core/src/main/java/amiss/application/service/save/EconomyService.java` constants (EVENT_MIN_ROUND=8, CRASH_MIN_READING=80, EVENT_CHANCE=31, drops {3,6,9}, happiness {1,2,3}, boom +6).
- [ ] **Step 3:** Commit `docs: player-facing gameplay doc for the KAN-48 economy odds`.

### Task 4: CV highlights + PR

**Files:**
- Modify: `tasks/cv-highlights.md` (KAN-55 entry, newest at bottom of its phase, matching existing format)

- [ ] **Step 1:** Add the CV entry (architecture doc + C4-ish Mermaid diagram + honest-gaps section as the talking point).
- [ ] **Step 2:** Push branch; `gh pr create --base develop` — body: what changed, the acceptance line, note MySqlITSupport deliberately left, link KAN-55.
- [ ] **Step 3:** Comment + transition KAN-55 → In Progress in Jira with the PR link (Done on merge).

## Self-Review

- Spec coverage: rewrite ✓ (T1), walkthrough ✓ (T1.6), diagram ✓ (T1.2), persistence strategy ✓ (T1.7), stale sweep ✓ (T2), README link ✓ (T2), known gaps ✓ (T1.9). Extra rider: GAMEPLAY.md (T3) — user-requested in the same session, docs-only, same PR.
- No placeholders; facts embedded above.
- UserGoals correction (live, not deleted) noted so the doc doesn't repeat the ticket's stale assumption.
