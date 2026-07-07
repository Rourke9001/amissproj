# Jones-parity jobs & degrees, hidden-requirement hiring, save slots, wiki win rules

**Date:** 2026-07-07 · **Status:** approved by Rourke (design conversation, this date)
**Rules source:** jonesinthefastlane.fandom.com (canonical per tasks/lessons.md; extracted
tables verified against the wiki's worked examples). WebFetch 402s on fandom — use the
Chrome tools to re-check pages.

## Goals

1. Replace the reconstructed 17-row `tbljobs` with the real Jones catalog: 39 jobs across
   9 workplaces, each with wage, experience, dependability, degree and uniform requirements.
2. Replace the linear education level (0–8) with the real 11-degree tree.
3. Employment Office: pick a workplace first, then see that workplace's jobs showing **only
   name and wage** — no requirement is visible anywhere (UI or API response). You learn
   whether you qualify by applying (4h) and being accepted or rejected.
4. Winning per the wiki Goals rules: four per-save goal targets (Wealth, Happiness,
   Education, Career, each 10–100) chosen at new-game time; win when all four are met at a
   week rollover.
5. **Save slots:** an account can hold multiple saved games, each with independent
   progress and goals. Highscores are retired (meaningless across differing goals).
6. Retire the `amiss-swing` module (Rourke's call, 2026-07-07).

Out of scope (noted on JIRA): economy/wage fluctuation and raises (a raise requires the
listed wage to exceed yours, impossible with fixed wages — KAN-48/49 territory), market
crash events (KAN-24), multiplayer/"Who's Winning".

## Decisions locked in conversation

- Fixed base wages from the wiki (no economy).
- Full experience/dependability mechanics, not stubs.
- Requirements are absent from job-listing DTOs entirely (not just hidden in the UI).
- Experience and dependability are hidden stats: never rendered in the HUD; career goal
  progress (1.25×dependability) is the only indirect view, as in the original.
- Save-breaking migration is acceptable: existing accounts get one migrated save keeping
  cash/bank/happiness/round; career and education reset (old linear levels don't map).
- Work payout moves to wiki rule: wage × 8 per full 6h session, pro-rated below 6h
  (`wage*8*hoursWorked/6`, clamp to remaining clock, succeed — supersedes the flat
  per-shift `tbljobs.salary` payout and the reject-on-short-clock behaviour; both were
  already logged as wiki deviations for KAN-5).

## Data model (Flyway V5, MySQL)

New reference tables (V5 owns their contents, DELETE+INSERT like V2):

```
tbldegrees   (id PK, name UNIQUE, prereq_degree_id NULL FK→tbldegrees)
tbljob       (id PK, job VARCHAR, location VARCHAR, wage INT,
              req_experience INT, req_dependability INT, req_clothing INT,
              UNIQUE(location, job))          -- names repeat across workplaces
tbljob_degrees (job_id FK, degree_id FK, PK(job_id, degree_id))   -- 0–2 rows per job
```

Migration is expand/contract because JPA runs validate-only against a live schema: V5
(PR 3) only ADDS tables (`tbljob` is a new name; the legacy `tbljobs` stays untouched)
and copies account state into saves; V6 (PR 5, the API cutover) drops `tbljobs`,
`tbluserstats` and `tbluser`'s state columns once no code reads them. Between V5 and V6
both shapes exist and every merged PR keeps develop bootable and green.

Degrees (prereq in parens): Junior College (—), Trade School (—), Business
Administration (Junior College), Academic (Junior College), Electronics (Trade School),
Pre-Engineering (Trade School), Graduate School (Academic), Engineering
(Pre-Engineering), Post-Doctoral (Graduate School), Research (Post-Doctoral),
Publishing (Research).

Jobs: the 39 wiki rows (including the two CD-ROM-only ones) — full table in the wiki
extraction (Location | Job | Wage | Exp | Dep | Degrees | Uniform); uniform maps
Casual=1, Dress=2, Business=3 onto the existing clothing levels. Wage is the wiki base
wage, now meaning R/hour with the wage×8-per-session payout.

Account/save split:

```
tbluser      (name PK, password)                    -- identity only; JWT subject
tblsave      (id PK, owner FK→tbluser ON DELETE CASCADE, label,
              created_at, updated_at,
              xpos, ypos, time, round, cash, bank, debt, rent, eat, clothing,
              job_id NULL FK→tbljobs,
              happiness, experience, dependability,
              current_course_id NULL FK→tbldegrees, eduprog,
              goal_wealth, goal_happiness, goal_education, goal_career,
              won BOOLEAN)
tblsave_degrees   (save_id FK, degree_id FK, PK(save_id, degree_id))
tblsave_turndowns (save_id FK, job_id FK, round INT, PK(save_id, job_id))
```

`tbluserstats` folds into `tblsave` and is dropped. Migration creates one save per
existing user ("Save 1"): carries xpos/ypos/time/round/cash/bank/debt/rent/eat/clothing/
happiness; job→NULL, experience=10, dependability=20, no degrees, goals default 50/50/50/50
(player can't re-pick mid-game; acceptable for the two dev accounts). New saves seed:
unemployed, experience 10, dependability 20, the existing new-player seeds for the rest
(cash 100 etc., unchanged), goals from the create request.

## Mechanics (amiss-core)

All formulas verified against the wiki's worked examples.

**Stats.** Experience starts 10, never decreases. Dependability starts 20, −3 at every
week rollover (floor 0). Caps recomputed on hire:
`maxExp = 10 + job.reqExp + 5×degrees` · `maxDep = 20 + job.reqDep + 5×degrees`.
Each paying work session: +1 exp and +1 dep, each only while below its cap.
Graduating: +5 dep, allowed to exceed the cap (decays via the weekly −3).
Hired with dep < 10 → dep set to 10.

**Apply** (Employment Office only, 4h, charged win or lose, −1 happiness on any
rejection, +3 on hire). Check order:
1. Education: missing any required degree → `NOT_ENOUGH_EDUCATION`.
2. Experience: exp < reqExp → `NOT_ENOUGH_EXPERIENCE`.
3. Dependability: dep < reqDep → `POOR_WORK_HISTORY`, except weeks 1–4 where it is
   reported as `NO_OPENINGS` (wiki suppression rule). Jobs listing reqDep 10 actually
   require 0 (wiki anti-frustration rule).
4. Luck: roll 1–100 > `Luck = 30 + (10 + dep + exp + 8×degrees) / 3` (integer division)
   → `NO_OPENINGS`, and the job goes on the save's turn-down list for the current round
   (subsequent applies that round short-circuit to `NO_OPENINGS`). Formula reproduces
   the wiki's three data points: start luck 43, luck 44 after taking Cook, floor 66 with
   all 11 degrees. Rejections may name multiple lacking stats (collect failures from
   steps 1–3 before returning).
   Cook @ Monolith Burgers skips every check — always hired.
   Hired: wage = listed wage, +2 experience (the job-switch bonus, cap-exempt per wiki
   wording "in addition to normal work gains"), dep floored to 10, caps recomputed,
   turn-down list untouched. RNG injected (seedable) for tests.

**Work** (at the job's own location). Uniform check unchanged. Firing check before the
clock is touched: dep < reqDep − 5 → `FIRED` (job cleared; happiness −3 — the wiki says
the only penalty for losing a job is happiness loss, amount unspecified; −3 mirrors the
hire bonus).
dep within [reqDep−5, reqDep−3] → work proceeds with a `WARNING` flag on the outcome.
Payout `wage × 8 × hoursWorked/6`, hoursWorked = min(6, hoursRemaining), integer floor;
0 hours remaining → week-over rejection as today. Debt garnish rule unchanged. +1
exp/+1 dep on any paying session.

**University.** Enroll targets a specific degree: available iff prereq earned (or no
prereq), not already earned, not already enrolled in another course. Fee stays R50,
10 study sessions per degree (existing rule). Graduation awards the degree, +5 dep,
unlocks dependents. `UniversityService.DEGREES`/`MAX_EDUCATION` and the linear
`education`/`setEducation` model are deleted.

**Goals & win.** `wealthStat = (cash + bank) / 100` (integer division; debt not
subtracted — wiki counts liquid assets) · `happinessStat = happiness` ·
`educationStat = 1 + 9×degrees` · `careerStat = floor(1.25 × dependability)`, 0 while
unemployed. At week rollover, after the −3 dep decay: if all four stats ≥ their goal
targets, set `won` (sticky). State DTO carries per-goal current/target and `won`.

**Services refactor.** `GameServices` and every service re-key from `username` to a save:
ports become save-scoped (`SaveRepository`, `SaveDegreeRepository`, …; `UserRepository`
shrinks to credentials). `JobService`'s string-typed legacy accessors go typed
(`JobListing` by id). JDBC adapters + `GameContext` (Swing's composition root) are
removed with the Swing module; JPA adapters are the only persistence
(FlywayMigrator stays — it's Boot-startup, not Swing).

## API (amiss-api)

- Routes move `/api/players/{username}/…` → `/api/saves/{saveId}/…`. `PlayerScopeFilter`
  becomes save-ownership enforcement: resolve the save's owner, 403 unless it equals the
  JWT subject (404 unknown save). Register/login unchanged.
- New: `GET /api/saves` (own saves: id, label, round, cash, won, updated_at) ·
  `POST /api/saves` {label, goals{wealth,happiness,education,career} each 10–100, or
  `"random": true`} · `DELETE /api/saves/{id}`.
- `GET /api/jobs?location=X` (and unfiltered) returns `{id, name, location, wage}`
  ONLY — requirement fields are gone from the DTO. `GET /api/board` already lists
  locations; the 9 hiring workplaces are those with jobs.
- `POST /api/saves/{id}/jobs/apply` {jobId} → outcome: `HIRED` (wage) or rejection
  reasons (`NOT_ENOUGH_EDUCATION` / `NOT_ENOUGH_EXPERIENCE` / `POOR_WORK_HISTORY` /
  `NO_OPENINGS`, possibly several of the first three), minutes charged, new state.
  Charged rejections stay HTTP 200 (established contract).
- Work response gains the `WARNING`/`FIRED` outcomes and the pro-rated payout.
- `GET /api/saves/{id}/courses`: every degree with status EARNED / AVAILABLE / LOCKED
  (+prereq name); enroll/study take a degree id.
- Deleted: `GET /api/highscores`, `HighscoreEntryDto`, the goals constants in
  `PlayerStateAssembler` (goals now per-save).
- State DTO: rename `PlayerStateDto` → save-centric (id, label, plus current fields);
  stats section drops `education` level/progress in favour of degrees earned + current
  course; **experience and dependability are NOT exposed**; goals = four
  {current, target, met} plus `won`.

## Frontend

- Post-login: **saves screen** — list (label, round, cash, won badge, last played),
  continue, delete (confirm), "New game" → goal setup (four 10–100 sliders + Randomise,
  wiki flavour text) → create → board.
- All api modules re-path to `/api/saves/{saveId}`; active save id lives in route or
  context; query keys move from `['player', username]` to `['save', saveId]`.
- Employment Office panel: step 1 workplace list (9), step 2 that workplace's jobs
  (name + R{wage}/h, current-job marker, every Apply enabled), officer-style outcome
  messages in the feed. No requirement badges, no disabled buttons.
- University panel: course tree (earned/available/locked + prereq), enroll into a chosen
  course; study as today; graduation toast.
- HUD: goal bars use per-save targets (cash bar → wealth stat vs target); education
  shows degrees; no experience/dependability display. Work outcomes surface warning
  ("your boss is unhappy…") and fired messages.
- Win: when state reports `won` (first transition), a victory screen over the board;
  save stays playable (wiki: play continues after winning). Highscores page/nav removed.
- HomePage's highscore table removed with the endpoint.

## Testing

- Core: unit tests per mechanic — cap formulas, decay/floor, luck boundaries (seeded
  RNG), apply check order + weeks-1–4 masking, turn-down persistence per round, Cook
  bypass, fire/warning bands, pro-rated payout, graduation +5 dep over cap, win check.
- Persistence: Testcontainers ITs for the new repositories + V5 on a clean MySQL 9 and
  on a baselined pre-V5 database (migration of an existing user exercised).
- API: MockMvc slices for saves CRUD, ownership 403/404, apply outcomes, jobs DTO
  contains no requirement fields (regression-pin the hidden-requirements guarantee).
- Frontend: Vitest for saves screen, goal setup validation, two-step employment flow,
  outcome rendering; existing suites updated for the route change.

## Delivery (branches off develop, sequential PRs)

1. `docs/jobs-degrees-spec` — this spec, lessons.md 402 entry, cv-highlights refresh.
2. `chore/retire-swing` — drop amiss-swing from the reactor (+ scripts/docs/README), and
   the now-dead JDBC adapters/GameContext if nothing else uses them.
3. `feat/saves-jobs-degrees-schema` — V5 + JPA entities/repos/ports (both shapes:
   account/save split and the three catalog tables), seed data.
4. `feat/hiring-mechanics` — core services refactor to save-scoping + all mechanics +
   unit tests.
5. `feat/saves-employment-api` — saves CRUD, route move, scope filter, employment/
   university/goals endpoints, highscores removal, ITs.
6. `feat/frontend-saves-employment` — saves/goal-setup screens, panel rework, win
   screen, highscores removal.

Each PR: green CI, JIRA ticket In Progress + PR comment, Done on merge. ROADMAP.md and
README/SETUP updated where behaviour changes (definition of done).
