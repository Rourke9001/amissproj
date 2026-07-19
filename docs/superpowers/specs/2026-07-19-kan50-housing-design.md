# KAN-50 — Housing expansion: apartment tiers, rent lock-in, extensions, garnishment

**Date:** 2026-07-19 · **Ticket:** [KAN-50](https://rourke9001.atlassian.net/browse/KAN-50) · **Status:** approved design, pre-plan

Canonical rules source: the *Jones in the Fast Lane* wiki —
[Rent](https://jonesinthefastlane.fandom.com/wiki/Rent) and
[Rent Office](https://jonesinthefastlane.fandom.com/wiki/Rent_Office) pages, re-read
2026-07-19. Where this spec interprets a gap in the wiki, it says so explicitly.

## Decisions taken (with Rourke, 2026-07-19)

1. **Wiki-exact economy values.** Rent adopts the wiki bases — R325 (Low-Cost
   Housing) / R475 (Le Security Apartments) per month — as base constants run
   through `EconomyService.price(base, save)`, the same single pricing authority
   every other purchasable already uses. Future rebalance = edit one constant.
2. **Core scope only.** Tiers, lease model, lock-in, extensions, switching,
   garnishment, weekly start position, robbery-immunity flag. Out of scope:
   storage capacity (follow-up ticket — see below), the robbery event itself
   (KAN-24), FE design polish (Rourke's own follow-up ticket; this slice wires
   barebones plumbing only).
3. **Eviction does not exist.** Wiki: "A player may remain in Rent Debt
   indefinitely … They will never be evicted." No eviction mechanic, ever.
4. **Full garnishment model** (the wiki's only rent-debt consequence) is in scope.
5. **Full lease model**: paid-through lease, pre-pay advances, and the Rent
   Office opening-hours rule.
6. **Approach A**: `ApartmentTier` domain enum + V13 columns on `tblsave` — no
   catalog table (two rows of two facts; matches the `ApplianceItem` /
   `FastFoodItem` / `ClothingItem` catalog-in-code precedent).

## Domain model

New enum `amiss.domain.model.ApartmentTier`:

| Tier | Base rent | Robbery-proof | Home board stop |
|---|---|---|---|
| `LOW_COST` | 325 | no | `Location.LOW_COST_HOUSING` (ring 0) |
| `SECURITY` | 475 | yes | `Location.LE_SECURITY_APARTMENTS` (ring 11) |

`robberyProof()` is the entire KAN-24 hook — the robbery roll will read it; nothing
else is stored for it.

**Listing price** (what switching costs and what the panel shows) =
`EconomyService.price(tier.baseRent(), save)` — floats with the economy, so a
market crash halves listings (wiki-consistent) and the wiki's "reduce rent"
double-switch trick emerges without any dedicated code.

**Locked-in rent**: the rent you signed at (`rentMonthly`) never moves while you
keep the apartment. Only listings float.

## Schema — `V13__housing.sql` on `tblsave`

Add seven columns, drop one. Saves are junk/test data — defaults only, no backfill.

| Column | Type | Meaning | New-save default |
|---|---|---|---|
| `apartment_tier` | varchar | current tier | `LOW_COST` |
| `rent_monthly` | int | locked-in signed rent | 325 — wiki: the starting rent is *always* $325, economy-independent |
| `lease_paid_through` | int | last round the lease covers | 4 (first month paid; first due week is round 4) |
| `extension_until` | int nullable | extension grace deadline (round) | null |
| `extensions_granted` | tinyint | approvals so far — drives the odds table | 0 |
| `extension_asked_round` | int nullable | last round an extension was requested (once-per-round guard; persisted so a reload can't re-roll the odds) | null |
| `ever_rent_debt` | bool | ever entered Rent Debt | false |

Dropped: the `rent` 1/0 due-flag — "rent due" is now derived (below). The existing
`debt` column becomes real Rent Debt. `SaveState` (core) and `SaveEntity` (JPA)
gain the mirrored fields; Hibernate stays validate-only against the Flyway schema.

## Rules (all in existing core services)

Derived state, used everywhere below. The office opens by **calendar**, not by
lease state (wiki: "It is the last Week of the Month, in which case the Rent
Office is open to everyone") — this is what makes repeat-click advances possible
after your due rent is already paid.

- **monthEnd** ⇔ `round % 4 == 0`.
- **rentDue** ⇔ `round >= leasePaidThrough` (due this month-end, or overdue on
  an extension).
- **onExtension** ⇔ `extensionUntil != null && round <= extensionUntil`.
- **servicesOpenFor(save)** ⇔ monthEnd ∨ onExtension. All four services require
  this.
- **officeOpenFor(save)** ⇔ servicesOpen ∨ player's job is at the Rent Office
  (employment grants *access* every week — for Work — but no services, matching
  the wiki NOTE verbatim).
- **nextMonthEndAfter(round)** = the smallest multiple of 4 strictly greater
  than `round` — keeps `leasePaidThrough` on month boundaries even when a
  payment or switch happens during an extension week.

### RentService

- **payRent** — servicesOpen + at `RENT_OFFICE` + time for `payRentMinutes`:
  - In debt: requires `cash >= debt`; pays and clears the whole debt
    (wiki: "erase your entire Rent Debt"). Lease is then due-normal next month
    (`leasePaidThrough` was already advanced when the debt was taken — below).
  - Not in debt: pays `rentMonthly` cash. If rentDue:
    `leasePaidThrough = nextMonthEndAfter(round)` and any extension clears.
    If not due (lease already covers this month): **advance** —
    `leasePaidThrough += 4`. Repeat calls stack advances while cash lasts.
- **payDebt** ("Pay Garnishment") — same clears-whole-debt path as payRent-in-debt,
  exposed as its own action/endpoint so the panel can label it distinctly.
- **requestExtension** — once per round (persisted `extensionAskedRound` guard —
  a reload must not re-roll the odds), only while rentDue.
  Auto-**denied** if `everRentDebt` (wiki: garnishment history bars extensions
  for the rest of the game). Otherwise approval odds by `extensionsGranted`:
  0 → 100 %, 1 → 75 %, 2 → 50 %, ≥3 → 25 % (floor). Approved:
  `extensionUntil = round + 1`, `extensionsGranted += 1` — the office stays open
  for this player through that round, and consecutive re-requests are legal.
  Denied: −1 happiness (floor 0), pay by end of round or enter debt.
- **switchApartment** — servicesOpen + at `RENT_OFFICE`: pay the *other* tier's
  current listing in cash. Effects: `apartmentTier` flips; `rentMonthly` = the
  listing just paid (new lock-in);
  `leasePaidThrough = nextMonthEndAfter(round)` (one month bought — any prior
  advance is thereby forfeited, wiki rule); extension cleared; `debt` unchanged
  (carries).

RNG for the extension roll is injected (same seedable pattern EconomyService
uses) so tests are deterministic.

### WeekRolloverService.endWeek()

- **Debt entry:** if the closing round `r >= leasePaidThrough` and no extension
  covering beyond `r` (`extensionUntil == null || extensionUntil <= r`) →
  `debt += rentMonthly`, `everRentDebt = true`,
  `leasePaidThrough = nextMonthEndAfter(r)`.
  *Interpretation:* the wiki fixes initial debt at one month's rent but is
  silent on later unpaid months; we accrue one month per unpaid month
  (otherwise indefinite debt would be strictly cheap). Flagged as our call.
- **Expired extensions** are cleared once past `extensionUntil`.
- **Home reset is tier-aware:** the week-start position becomes
  `save.apartmentTier().homeStop()` instead of hardcoded ring 0 —
  `WeekRolloverService` *and* the mirrored fallback in `PlayerStateAssembler`
  (both spots found in the 2026-07-19 code scan). This lands Rourke's
  2026-07-06 design note; `SaveStateDto.location` already drives the SPA, so it
  is backend-only.
- The old `rentDue`-flag maintenance disappears with the flag.

### ShiftService.work()

If `debt > 0`: garnish `g = min(wage/2, debt)`; `debt -= g`; shift payout =
`wage − g`; charge **R2 interest** (from cash) *except* on the shift where the
garnishment fully clears the debt (wiki: partial final garnish pays no
interest).

## API surface (`RentController`, `/api/saves/{saveId}/rent/...`)

Guards on every route: `SaveScope` ownership, `LocationGuard` at `RENT_OFFICE`,
plus a new **office-closed → 409** RFC 7807 problem (`RentOfficeClosedException`)
per the servicesOpenFor rule (the GET is allowed whenever the *location* is
accessible, so the panel can render the closed state). Charged-rejection semantics follow the house rule:
guards reject *before* charging; anything charged returns 200 with an outcome.

- `GET /rent` — the panel's single read: `officeOpen`, `rentDue`, `apartmentTier`,
  `rentMonthly` (locked), `leasePaidThrough`, `extensionUntil`,
  `canRequestExtension` (rentDue ∧ ¬everRentDebt ∧ not-yet-asked-this-round),
  `debt`, and
  both tiers' current listings.
- `POST /rent/pay` — month / advance / clear-debt per rules above.
- `POST /rent/debt/pay` — Pay Garnishment.
- `POST /rent/extension` — rolls the odds; 200 body says approved/denied (a
  denial is a game outcome, not an HTTP error).
- `POST /rent/switch` — switch tiers at listing.

`SaveStateDto` gains `apartmentTier`, `rentMonthly`, and keeps a **derived**
`rentDue` boolean (rentDue ∨ debt outstanding drives the existing HUD banner
unchanged); `debt` is already on the wire. Time costs: `payRentMinutes` covers
pay/debt-pay; extension and switch get their own `ActionCosts`/`CostsConfig`
entries (config-backed, not hardcoded).

## Frontend (barebones wiring only — design lands in Rourke's follow-up ticket)

- `api/rent.ts`: typed functions for the GET + four POSTs; TS types updated.
- `RentOfficePanel`: minimal unstyled rework — closed notice when
  `officeOpen=false`; otherwise plain rows (due/debt status, listings) + four
  buttons firing mutations that `setQueryData` the `['save', saveId]` cache on
  success and **invalidate on rejection** (pinned lesson: the server may have
  charged).
- Registry: `LE_SECURITY_APARTMENTS` → the same `HomePanel` as Low-Cost;
  `HomePanel` gains a residence guard off `player.apartmentTier` ("You don't
  live here") so relax-at-home works only at your own stop.
- No other FE work: HUD banner runs off the derived `rentDue`; week-start
  position needs zero FE change.

## Testing

- **Core unit:** full lease lifecycle (due → pay / advance / miss → debt),
  extension odds with seeded RNG (incl. first-always-approved, floor 25 %,
  ever-debt auto-deny, once-per-round), switch pricing + advance forfeiture +
  debt carry, rollover debt accrual + tier-aware home reset, garnishment maths
  incl. the final-shift no-interest rule.
- **API:** MockMvc per endpoint incl. closed-office 409, wrong-location 409,
  denial-as-200.
- **IT:** Testcontainers proves the V1→V13 chain + round-trip of the new columns.
- **FE:** a couple of Vitest smoke tests on the panel actions.

## Delivery

Two stacked PRs off `develop`:

1. **PR 1 — backend complete:** V13 + domain enum + core rules + JPA mapping +
   endpoints/DTOs + all backend tests + docs (`GAMEPLAY.md` housing/rent rules
   incl. the odds table; `ARCHITECTURE.md` migration list → V13; CV-highlights
   entry). The JPA entity lives in `amiss-api`, so core and API ship together.
2. **PR 2 — FE boilerplate** as above.

Jira: KAN-50 → In Progress now, Done when PR 2 merges. Follow-up ticket to
create: **storage capacity** (per-tier inventory bound — deferred until the
inventory has quantities to bound; reference wiki). Comment on KAN-24 pointing
at the `robberyProof()` hook.

## Known interpretations / risks

- **Debt accrues monthly** while unpaid (wiki-silent; our call, flagged above).
- **Once-per-round extension attempts** are guarded by the persisted
  `extension_asked_round` column (resolved during spec self-review — a
  transient guard would let a reload re-roll the denial odds).
- **Rent Office worker access**: employment opens the *location* every week but
  exposes no services unless due/extended — mirrors the wiki NOTE verbatim.
- Existing `RentService` tests around the old flag are rewritten, not patched.
