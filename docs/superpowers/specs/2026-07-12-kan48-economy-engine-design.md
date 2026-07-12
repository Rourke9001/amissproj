# KAN-48 — Economy engine (wiki Index/Reading model)

**Date:** 2026-07-12 · **Ticket:** [KAN-48](https://rourke9001.atlassian.net/browse/KAN-48) (epic KAN-5)
**Canonical rules source:** jonesinthefastlane.fandom.com (Economy, Market Crash, Economic Boom, Employment Office pages)
**Requirements seed:** the "Fidelity audit 2026-07-12" comment on KAN-48.

## Scope decision

KAN-48 implements the wiki's economy model **only**: two hidden per-save values
(Index, Reading), price/wage/enroll-fee fluctuation derived from Reading, and
week-8+ market crashes/booms. The ticket's original G-list scope (item catalogue
tables G-500, per-store stock G-501, `base × demand × inflation` G-502, weekly
restock G-503) is **retired from this ticket** — the real game has no
demand/inflation/stock-quantity system, and nothing consumes an item database
until KAN-23 designs the buyable items. The KAN-48 ticket description is
updated to match when work starts.

Two corrections to the audit comment, agreed 2026-07-12:

1. **Crashes/booms are week 8+, not week 4+** — wiki: "A Market Crash can only
   occur … only on or after Week #8." We follow the wiki.
2. **There is no existing wage snapshot.** The comment assumed `tblsave`
   snapshots wage at hire; in fact `HiringService.hire` stores only `job_id`
   and `ShiftService.work` re-reads `tbljob.wage` live each shift
   (`ShiftService.java:68`). A real snapshot column is added (V8, below).

## The model (wiki-exact where documented)

### Hidden state, per save

| Field | Range | Start / migration default |
|---|---|---|
| `economyIndex` | −3 … +3 | 0 |
| `economyReading` | −30 … +90 | 0 (= exactly today's prices; zero migration shock) |
| `wage` | ≥ 0, nullable | NULL; backfilled to base wage for employed saves |

Index/Reading are **never exposed on any DTO** — same policy as
`experience`/`dependability`.

### Price formula (wiki-exact)

> "Item Price = Base Price + Base Price × Reading/60" — 50% to 250% of base.

Implemented as integer math: `price = base + Math.floorDiv(base * reading, 60)`
(`floorDiv` so negative readings round predictably). One formula serves every
charge path **and** every catalog read path — display equals charge by
construction. Base prices in tables/enums are never mutated.

Applies to: fast-food items, grocery packs, clothes, Hi-Tech U enroll fee
(base $50, `CourseService.ENROLL_FEE`), Employment Office wage listings.
Does **not** apply to: the player's held wage (snapshot, below), current rent
(flat R80 until KAN-50's apartment tiers), bank balances (no interest —
wiki-correct, `BankService`'s doc note stands), pawn shop (KAN-49).

### Weekly update (design freedom — wiki declines to document its formula)

Run inside `WeekRolloverService.endWeek`, after dependability decay and
**before the win check** (a crash that fires you or wipes your bank counts
against goals immediately):

1. **Drift:** `index += uniform{−1, 0, +1}`, clamp ±3; then
   `reading += 10 × index + uniform{−5 … +5}`, clamp −30 … +90.
   The `10 × index` momentum term gives the wiki's "a strong economy is likely
   to get stronger". All constants named and tunable.
2. **Crash/boom roll** (only when the new round ≥ 8):
   - Crash checked first: eligible only if `reading ≥ 80` (wiki); chance
     **1/31** per week (wiki CD-ROM formula at one player).
   - If no crash fired (ineligible **or** the roll missed), a boom roll,
     chance **1/31**. (The wiki's boom bound of "Reading ≤ 120" exceeds the
     +90 cap, so booms are always eligible.)
3. **Jolt:** crash slams `index = −3` and subtracts 3/6/9 Reading points —
   exactly the wiki's flat −5/−10/−15% price drop, since price% = reading/60.
   Boom slams `index = +3`, `reading += 6` (+10%). Severity uniform over
   Minor/Moderate/Major.
4. **Employment effects (wiki-exact):**
   - Minor: prices only.
   - Moderate: 50% roll — fired (`jobId`/`wage` → null), else wage cut to 80%
     (`wage = wage * 4 / 5`).
   - Major: always fired **and** `bank = 0`.
   - Happiness −1/−2/−3 by severity, floored at 0. (The wiki's extra stock
     penalties/bonuses wait for KAN-49's stocks.)

All randomness flows through the existing injected `IntSupplier roll1to100`
pattern (`SaveGameServices`), so tests script every outcome.

### Wage snapshot

`HiringService.hire` stores the **listed** (economy-adjusted) wage into
`save.wage`; `ShiftService.work` pay and the player-state DTO read the
snapshot, never `tbljob`. Wiki-faithful: hire in a boom and the high wage is
locked until a raise (KAN-49) or a crash pay-cut.

## Architecture

A new `EconomyService` in `amiss.application.service.save` is the single
pricing authority (`price(base, save)`) and owns the weekly tick
(`tick(save, round)` returning an event outcome). Core charge paths
(food/clothes/enroll), `HiringService`, `WeekRolloverService`, and the API's
DTO assemblers all call it. Wired in `SaveGameServices` with the injected
roll supplier. No game rule enters the presentation layer; no persistence
type enters core — the pattern the Phase 3 refactor established.

Rejected alternatives: scaling at the DTO layer only (display/charge
divergence; rule in presentation), and a materialized per-save weekly price
table (redundant derivable state).

## Persistence

Expand/contract, one migration per PR (V5→V6 precedent):

- **V7** (PR 1): `tblsave` + `economy_index TINYINT NOT NULL DEFAULT 0`,
  `economy_reading SMALLINT NOT NULL DEFAULT 0`.
- **V8** (PR 2): `tblsave` + `wage INT NULL`, with backfill
  `UPDATE tblsave s JOIN tbljob j ON s.job_id = j.id SET s.wage = j.wage
  WHERE s.job_id IS NOT NULL`.

JPA entities updated in the same PRs (`ddl-auto=validate`).

## API surface

- Catalog routes become **save-scoped** (prices depend on the save's Reading):
  `GET /api/saves/{saveId}/jobs`, `/food`, `/clothes`, and the courses/fee
  read. The old global routes are deleted in the same PR that moves each —
  the SPA is the only consumer and updates in lockstep. Nesting under
  `/api/saves/{saveId}/` inherits the existing 403 player-scope guard.
- `EndWeekResponse` gains an `economy` block:
  `{ event: NONE|BOOM|CRASH, severity?: MINOR|MODERATE|MAJOR, fired: boolean,
  wageCutTo?: int, bankWiped: boolean, happinessLost: int }`.
- `PlayerStateDto.job.wage` reads the snapshot; field shape unchanged.

## Frontend

- Panels point their catalog queries at the save-scoped endpoints; query keys
  gain the saveId; end-week success invalidates catalog queries so next week's
  prices display fresh.
- The end-week modal + feed render the `economy` block (crash/boom, fired,
  wage cut, bank wiped). No trend UI — Index/Reading stay hidden (agreed
  visibility decision).
- The hardcoded "R80" rent strings (`HomePanel`, `RentOfficePanel`,
  `EndWeekModal`) stay — current rent genuinely does not fluctuate.

## Testing

- `EconomyServiceTest`: price math (rounding at negative readings, 50%/250%
  extremes), drift clamps, week-8 and reading≥80 gates, each severity's
  effects — scripted `IntSupplier`s, like the hiring-luck tests.
- `WeekRolloverServiceTest`: tick ordering (employment effects land before the
  win check), event propagation.
- `HiringServiceTest`/`ShiftServiceTest`: snapshot written at listed wage; pay
  reads the snapshot.
- Testcontainers ITs (SaveSchemaIT pattern) for V7/V8, including the backfill.
- Frontend: end-week modal/feed rendering per event type; panel tests
  re-pointed at save-scoped endpoints. `TestSaves.newSave()` gains the fields.

## Delivery — 3 stacked PRs off `develop`

1. `feat/kan48-economy-core` — V7, `EconomyService` (price + drift), rollover
   hook, save-scoped catalogs, scaled item/enroll prices (charge + display).
2. `feat/kan48-wage-snapshot` — V8 + backfill, scaled Employment Office
   listings, hire snapshot, shift/DTO cutover.
3. `feat/kan48-crash-boom` — week-8 crash/boom mechanics,
   `EndWeekResponse.economy`, SPA end-week modal/feed.

Each PR ships independently: after PR 1 prices breathe, after PR 2 wages do,
PR 3 adds the drama. The KAN-48 description is rewritten to this scope when
the ticket moves to In Progress.

## Out of scope (tracked elsewhere)

Item database & store stock (KAN-23), stocks/T-bills & pawn/loan/raise
formulas (KAN-49), apartment tiers & available-rent scaling (KAN-50),
newspaper/lottery fixed prices & event pipeline (KAN-24).

## Design freedoms taken (wiki does not specify)

Drift formula and its constants; starting values (0/0); crash/boom expressed
as Reading-point jolts; no multi-turn recession state (a jolt biases the
trend via the slammed Index, which the wiki supports qualitatively).
