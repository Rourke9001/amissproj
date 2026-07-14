# AmissProj — Gameplay: the economy

Player-facing notes on the hidden economy that moves prices and wages
(shipped with KAN-48). Design reference: the *Jones in the Fast Lane* wiki's
Economy / Market Crash / Economic Boom pages; source of truth for the numbers:
`amiss-core/src/main/java/amiss/application/service/save/EconomyService.java`.

## How prices work

Every save carries two hidden values you never see directly:

- **Index** — the trend, between −3 and +3.
- **Reading** — the economy's level, between −30 and +90.

Every displayed *and* charged price (fast food, groceries, clothes, enrolment
fees — and the wages on the Employment Office board) is:

```
price = base + base × Reading / 60
```

so prices range from **50%** of base (Reading −30) to **250%** (Reading +90).
The price you see is always the price you pay — catalogs and charge paths ask
the same service. When you're hired, the *listed* (fluctuated) wage is
snapshotted onto your save; later swings change prices and new listings, not
your pay.

## The weekly drift

At every week rollover, before anything dramatic can happen:

1. The Index drifts by −1, 0, or +1 (equal odds), clamped to ±3.
2. The Reading moves by `10 × Index` plus a small ±5 wobble, clamped to
   −30..+90.

So a run of good weeks compounds: a high Index pushes the Reading up fast —
towards crash territory.

## Booms and crashes — the odds

Nothing can happen before **week 8**. From week 8 on, each rollover after the
drift:

| Event | Precondition | Chance per week | Effect |
|-------|--------------|-----------------|--------|
| **Market crash** | Reading ≥ 80 (a near-peak economy) | **1/31 (~3.2%)** | Severity rolled uniformly, see below; Index pinned to −3 |
| **Economic boom** | none (only rolled if no crash fired) | **1/31 (~3.2%)** | Prices +10% (Reading +6), Index pinned to +3 |

Crash severity (equal 1-in-3 odds each):

| Severity | Prices | Happiness | Job & bank |
|----------|--------|-----------|------------|
| **Minor** | −5% | −1 | — |
| **Moderate** | −10% | −2 | If employed: 50% fired, otherwise pay cut to 80% of your wage |
| **Major** | −15% | −3 | Fired **and** your bank account is wiped |

Notes:

- The crash and boom rolls are per save, per week — independent weeks, so over
  a long stretch in crash territory, expect roughly one crash every ~31 weeks.
- A crash can only hit when the economy is booming-hot (Reading ≥ 80). Cheap
  weeks are safe weeks; the price of a hot economy (high wages on the board)
  is crash exposure — and a Major crash is why keeping everything in the bank
  has a downside.
- Booms can fire in any week from 8 on, whatever the Reading.
- The end-of-week summary reports the event when one hits (crash severity,
  fired/pay-cut/bank-wiped, or the boom).
