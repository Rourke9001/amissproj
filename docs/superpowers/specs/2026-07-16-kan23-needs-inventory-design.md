# KAN-23 — Needs system + items & inventory (wiki-exact)

**Date:** 2026-07-16 · **Ticket:** [KAN-23](https://rourke9001.atlassian.net/browse/KAN-23) (epic KAN-5)
**Canonical rules source:** jonesinthefastlane.fandom.com (Starvation, Doctor Visit,
Relaxation, Clothes, Refrigerator, Freezer, Computer, Encyclopedia, Dictionary, Atlas pages)
**Requirements seed:** the "Fidelity audit 2026-07-12" comment on KAN-23, corrected against
the wiki below where the comment's shorthand undersold the real mechanic.

## Scope decision

KAN-23 implements six needs/consumable mechanics — starvation, food storage, clothes wear,
relaxation, extra credit, and a **minimal** appliance-ownership concept — plus Doctor Visit,
which the comment assumed was KAN-24 territory but turns out to be small enough to build
for real here. Split out of this ticket, by agreement 2026-07-16:

- **Appliances catalog** (browsable/purchasable storefront, break/repair odds, new-vs-used
  price variants, differential store odds) moves to a **follow-on ticket**. KAN-23 ships only
  the ownership plumbing four items actually need (Fridge, Freezer, Computer, the 3 Books),
  with no browsing catalog, no break/repair, one canonical price per item.
- **Apartment Robbery (Wild Willy)** stays KAN-24 + KAN-50 — it needs Durables tracking and
  Low-Cost-Apartment location state neither of which exist yet. KAN-23 only wires the
  Relaxation-stat *input* the wiki says gates it; KAN-24 builds the actual robbery roll.
- **Computer's passive income event** (1/7 chance/turn, $20–100 + happiness), **Hot Tub**
  (stops relaxation decay — a 7th appliance the audit comment missed), and any **new/used
  price variant** (e.g. Z-Mart's cheaper Refrigerator, Z-Mart's cheaper/shorter-lived
  clothes) are explicitly deferred to the follow-on appliances ticket.

Corrections to the audit comment, agreed 2026-07-16 (verified against the wiki, since the
comment pre-dates this session and was written as a quick audit pass, not a full re-read):

1. **Doctor Visit is one shared, fully-specified event**, not three separate per-mechanic
   flags. Three independent trigger rolls (Starvation 25%, spoiled fridgeless fresh food
   50%, Relaxation-at-floor 20%) feed one resolution; only one visit fires per turn even if
   multiple conditions are true. Effect: **+10h, −4 happiness, cash cost by tier**
   (≥$500 cash: random $30–200; $50–499: random $30–50; $31–49: random $30–cash on hand;
   ≤$30: all cash on hand; bypassed entirely if cash = $0). Small and self-contained enough
   to implement in full here rather than stub a flag for KAN-24.
2. **Clothes are three independent countdown tracks** (Casual / Dress / Business
   weeks-remaining), not one upgrading level with weeks bolted on. Each starts at 6/0/0,
   decays −1/week independently, is topped up per-purchase, and working a job checks
   "≥1 week left in the required category **or a higher one**."
3. **Exact appliance/book numbers**, all sourced from the wiki (base prices below are
   arguments to `EconomyService.price()`, per Rourke's confirmation that every wiki dollar
   figure here is a base subject to the existing Index/Reading fluctuation — the same
   pattern `FastFoodItem`/`FoodPack`/`ClothingItem` already use via `ShopService`).

## The model (wiki-exact where documented)

### 1. Starvation

Replaces the current `fedWeekMinutes` (72h) bonus entirely — the wiki has no "fed bonus,"
only an "unfed penalty" against a flat week:

- Every week is a flat **60h** (`ActionCosts.baseWeekMinutes`, unchanged).
- At turn start, if the player bought **no** Fast Food last turn **and** has **0** weeks of
  Fresh Food stored, Starvation fires: **+20h** advances the clock immediately (so the
  player effectively gets 40h that turn), **−2 happiness**, and a **25%** Doctor Visit roll.
- `ActionCosts.fedWeekMinutes` is deleted; its pinning tests (`ActionCostsTest`,
  `CostsPropertiesBindingTest`) are updated as part of this change, not preserved.
- **Sequencing note:** PR 1 ships this using the *existing* single `SaveState.eat` field as
  its fed/unfed proxy (`fed = eat() > 0`, the same condition the current code already
  computes) — no schema change, no fresh/fast distinction yet. PR 2 (below) replaces `eat`
  with the real fresh/fast split; the starvation trigger's outward behavior is unchanged for
  the common case, and PR 2 adds the spoilage-specific 50% trigger as a genuinely new,
  independent condition alongside it.

### 2. Doctor Visit (new, shared)

One resolution per turn, checked once each of its trigger conditions is true this session
(condition sources arrive across PRs 1/2/4 below; robbery, a fourth theoretical trigger, is
explicitly out of scope):

| Condition | Chance | Introduced in |
|---|---|---|
| Starvation | 25% | PR 1 |
| Fridgeless fresh food spoiled | 50% | PR 2 |
| Relaxation stat at floor (10) | 20% | PR 4 |

Only one visit fires per turn (first true roll wins, or an equivalent single combined roll
— implementation detail for the plan). Effect, once triggered: **+10h**, **−4 happiness**,
and a cash cost gated on `cash > 0` (bypassed entirely otherwise):

| Cash on hand | Cost |
|---|---|
| ≥ $500 | random $30–$200 |
| $50–$499 | random $30–$50 |
| $31–$49 | random $30–cash on hand |
| ≤ $30 | all cash on hand |

All rolls (trigger chance and cost amount) go through the same injectable roll pattern
`EconomyService` already established (`IntUnaryOperator`-style suppliers wired via
`SaveGameServices`), so tests script every outcome deterministically.

### 3. Food storage (Fridge / Freezer)

- **Fast Food** (Monolith Burgers) keeps a player fed for exactly the *next* turn only — no
  stockpiling. This is a simple "ate fast food last turn" flag, not a weeks-counter.
- **Fresh Food** (Black's Market groceries) without a Fridge: any purchase keeps the player
  fed for exactly 1 week, then **all** of it spoils at the next turn start — triggering the
  50% Doctor Visit roll. It does not stack.
- **Fresh Food** with a **Fridge**: banks up to **6 weeks**, decaying −1/week, no spoilage
  risk within capacity.
- **Fridge + Freezer**: cap raises to **12 weeks**. A Freezer alone (no Fridge) does nothing.
- Both purchasable at a new minimal **Socket City** panel: Fridge **$876** (+1 happiness,
  once, on first purchase), Freezer **$513** (+2 happiness, once, on first purchase).

### 4. Clothes wear

Three independent per-category counters replace the single `clothing` int:

| Category | Store (KAN-23 scope: QT Clothing only) | Base price | Weeks | Happiness (per purchase) |
|---|---|---|---|---|
| Casual | QT Clothing | $73 | 11 | — |
| Dress | QT Clothing | $125 | 13 | +1 |
| Business | QT Clothing | $295 | 13 | +2 |

- All saves start at **6 weeks Casual, 0 Dress, 0 Business**.
- At turn start, each category with > 0 weeks decrements by 1 (independently — a player can
  own lapsing Casual *and* fresh Dress at once).
- A purchase **adds** the category's weeks (not an overwrite) — multiple purchases stack.
- Working a job requires ≥1 week remaining in the job's required category **or higher**
  (Business counts for a Casual-requiring job; Casual does not count for a Business-requiring
  one). `HiringService.apply()` currently has `JobSpec.reqClothing`/`tbljob.req_clothing` as
  live data but never checks it — this PR wires the missing `reasons.add(...)` branch.
- Z-Mart's cheaper/shorter-lived clothes line (Casual $35/9wk, Dress $90/9wk, no Business,
  no happiness bonus) is a real wiki mechanic but is **deferred** — QT Clothing is the only
  clothes store KAN-23 builds against, consistent with picking one canonical price/store per
  item elsewhere in this spec.

### 5. Relaxation

- New per-save stat, **start 10, max 50, floor 10**.
- Decays **−1 at each turn start** (a future Hot Tub appliance would suppress this — out of
  scope, noted above).
- New **Relax** action at Home: **6h**, **+3 relaxation** (clamped to 50), and **+2
  happiness** — but only on the **first** Relax of a given turn; repeat Relaxes in the same
  turn raise the stat with no further happiness.
- When the stat is at the floor (10) at turn start, it feeds the 20% Doctor Visit trigger.
- `ActionCosts.relaxMinutes` (360 = 6h) already exists and is unconsumed — this PR is its
  first consumer. `HomePanel.tsx`'s placeholder ("Purely informational for now") is replaced
  with the real Relax action.

### 6. Extra credit

- Owning a **Computer** (Socket City, **$1599**, +3 happiness once on first purchase, +1
  extra credit) reduces `CourseService.STUDIES_PER_DEGREE` (10) by 1.
- Owning **all three Books** — Encyclopedia (Z-Mart, **$475**), Dictionary (Z-Mart, **$70**),
  Atlas (Z-Mart, **$55**) — reduces it by 1 more. Individual books give no bonus alone.
- Combined floor: **8** lessons (10 − 1 computer − 1 all-books).
- Books are sold at a new minimal **Z-Mart** panel (Z-Mart's other wiki roles — used
  Refrigerator, cheaper clothes, the pawn-adjacent break/repair angle — stay deferred).

### Appliance ownership (minimal plumbing)

A new `amiss.domain.model.ApplianceItem` enum — `FRIDGE`, `FREEZER`, `COMPUTER`,
`ENCYCLOPEDIA`, `DICTIONARY`, `ATLAS` — each carrying `basePrice()` and a one-time
`firstOwnedHappiness()` (0 for the books). Ownership is a `Set<ApplianceItem>` on
`SaveState`, persisted via `@ElementCollection` on `SaveEntity` — the same join-table
pattern `JobCatalogEntity` already uses for its degree requirements, not a new DB-backed
catalog (no port/adapter, no price-per-store, no stock). All purchases route through
`economy.price(item.basePrice(), save)`, identical to how `ShopService` already prices
`FastFoodItem`/`FoodPack`/`ClothingItem` — confirmed by reading `ShopService.java`: this
pattern is already uniform today, KAN-23 just extends it to two new item enums.

## Architecture

Everything follows the existing layering: new fields live on `SaveState` (domain, core);
weekly decay/rollover logic is new hooks inside `WeekRolloverService.endWeek()` (core), in
this order — fed/starvation determination → fresh-food spoilage check → clothes decay →
relaxation decay → Doctor Visit resolution (using whichever of the three conditions fired
this turn) → existing economy tick → existing win check. A new `DoctorVisitService` (core)
owns the shared roll+resolve logic, constructed with an injected roll supplier exactly like
`EconomyService`. New endpoints live under `/api/saves/{saveId}/...` (api), new frontend
panels follow the shared `StorePanel` registry pattern already used for Monolith/Black's/QT.
No game rule enters the presentation layer; no persistence type enters core.

## Persistence

One Flyway migration per PR touching schema, continuing the V-numbering from V8
(`wage_snapshot`):

- **V9** (PR 2 — food storage): `tblsave` gains fresh-food-weeks and a fast-food-fed-next-turn
  flag, replacing the ambiguous single `eat` column's meaning; `SaveEntity`'s appliance
  `@ElementCollection` join table is introduced here too (Fridge/Freezer are its first
  members).
- **V10** (PR 3 — clothes wear): `tblsave.clothing` is dropped and replaced with three
  `clothing_casual_weeks` / `clothing_dress_weeks` / `clothing_business_weeks` columns
  (defaults 6/0/0) **in the same migration** — an expand+contract combined into one step,
  justified because nothing outside this PR's own code reads the old `clothing` column
  (unlike the V5→V6 production cutover, which needed a live transition window).
- **V11** (PR 4 — relaxation): `tblsave.relaxation` (default 10).
- Appliance ownership's join table gains the Computer and Book enum values in PR 5 (no new
  migration version needed — it's the same `@ElementCollection`, new enum constants only).

Exact column names/types are a plan-level decision; this spec fixes the *shape* (what state
must exist), not the final DDL.

## API surface

- `POST /api/saves/{id}/relax` — new Relax action (PR 4).
- `GET /api/saves/{id}/appliances`, `POST /api/saves/{id}/appliances/{item}/buy` — Socket
  City (PR 2, extended PR 5) and Z-Mart (PR 5) both call the same minimal endpoint shape,
  differing only in which `ApplianceItem`s each panel lists.
- `EndWeekResponse` gains a `doctorVisit` block (`triggered: boolean`, `hoursLost`,
  `happinessLost`, `cashLost`), alongside the existing `economy` block from KAN-48.
- `PlayerStateDto`/`SaveStateDto` gain: fresh-food-weeks + fed-next-turn flag (replacing the
  single `foodWeeks`), the three clothing category weeks (replacing single clothing level),
  `relaxation`, and an `ownedAppliances` list. This ripples through
  `frontend/src/api/types.ts`, `Hud.tsx`, `BlacksMarketPanel.tsx`, `MonolithBurgersPanel.tsx`,
  `QTClothingPanel.tsx` — expected, flagged up front rather than discovered mid-PR.

## Frontend

- HUD gains bars/counters for fresh-food-weeks, each clothing category, and relaxation
  (ticket's "a HUD shows the bars" acceptance criterion).
- `HomePanel.tsx` rebuilt off its placeholder: Relax action + relaxation display.
- `QTClothingPanel.tsx` reworked from "buying only ever upgrades" to "each purchase adds
  weeks to its category; multiple categories can be owned at once."
- New minimal Socket City panel (PR 2, extended PR 5) and Z-Mart panel (PR 5), both thin
  wrappers over the shared `StorePanel` component already used elsewhere.

## Testing

- `WeekRolloverServiceTest` extended per PR for each new decay/trigger, asserting hook
  ordering (starvation → spoilage → clothes → relaxation → doctor visit → economy → win, per
  the Architecture section).
- `DoctorVisitServiceTest`: scripted roll suppliers for each trigger condition and each cash
  tier, mirroring `EconomyServiceTest`'s style.
- `ShopServiceTest`/new `ApplianceServiceTest`: purchase outcomes, economy-scaled pricing,
  one-time happiness bonuses.
- `HiringServiceTest`: the newly-wired clothing-gate rejection path.
- `CourseServiceTest`: lesson-count reduction at 0/1/2 extra-credit sources.
- Frontend: Vitest/RTL per new/changed panel; `TestSaves.newSave()` gains the new fields.
- No new Testcontainers IT beyond proving each migration in the V1→V11 chain (existing
  `SaveSchemaIT` pattern).

## Delivery — 5 stacked PRs off `develop`

1. `feat/kan23-starvation-doctor-visit` — flat 60h week, unfed penalty (using the existing
   `eat` field as the fed/unfed proxy — no migration), new `DoctorVisitService` with the
   starvation trigger wired in.
2. `feat/kan23-food-storage` — V9, fresh/fast food split, Fridge/Freezer ownership + minimal
   Socket City panel, spoilage → Doctor Visit's second trigger.
3. `feat/kan23-clothes-wear` — V10, three-category clothes model, `buyClothes()` rework,
   `HiringService` clothing-gate wiring, QTClothingPanel rework.
4. `feat/kan23-relaxation` — V11, Relax action, HomePanel rebuild, Doctor Visit's third
   trigger.
5. `feat/kan23-extra-credit` — Computer added to Socket City, new Z-Mart panel for the 3
   Books, `CourseService.study()` lesson reduction.

Each PR ships independently and is playable on its own, same discipline as KAN-48's 3 PRs.

## Out of scope (tracked elsewhere)

Appliances catalog — browsing/break/repair/new-vs-used odds, Z-Mart's cheaper clothes line,
Computer's passive income event, Hot Tub (**KAN-58**, filed 2026-07-16 alongside this
spec's approval). Wild Willy apartment robbery (**KAN-24**, needs Durables + Low-Cost-Apartment
state — also **KAN-50** for housing tiers). Apartment/inventory *capacity bounded by housing
tier* (**KAN-50**) — sidestepped entirely here since Fridge/Freezer themselves grant food
capacity independent of apartment tier, per the wiki; no forward dependency on KAN-50 exists
in this ticket after all.

## Design freedoms taken (wiki does not specify)

Exact DB column names/types (fixed at plan level, not spec level); whether the three Doctor
Visit trigger conditions are checked as one roll-of-rolls or short-circuit in a fixed order
(no behavioral difference wiki-side, since only one visit can fire per turn); Socket
City/Z-Mart panel visual layout (follows the existing shared `StorePanel` component,
unspecified by the wiki which has no UI to match against).
