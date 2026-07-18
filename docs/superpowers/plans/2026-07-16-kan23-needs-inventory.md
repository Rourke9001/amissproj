# KAN-23 Needs/Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wiki-exact starvation/food-storage/clothes-wear/relaxation/extra-credit mechanics, plus a new shared Doctor Visit event, with minimal appliance-ownership plumbing (Fridge/Freezer/Computer/3 Books) — no full appliances catalog (that's KAN-58).

**Architecture:** New fields on `SaveState`/`SaveEntity` (domain/JPA), new hooks appended sequentially into `WeekRolloverService.endWeek()` across 5 PRs, a new `DoctorVisitService` mirroring `EconomyService`'s injected-roll pattern, a new `ApplianceItem` enum + generic `ApplianceService` mirroring the `ClothingItem`/`ShopService` pattern (not a DB-backed catalog — no port/adapter), new minimal Socket City / Z-Mart frontend panels reusing `StorePanel`.

**Tech Stack:** Java 21, Spring Boot 3.5, Flyway 11, Spring Data JPA (validate-only), JUnit 5 + Mockito, MySQL 9 Testcontainers ITs; React 19 + TS + TanStack Query 5 + Vitest 4.

**Spec:** `docs/superpowers/specs/2026-07-16-kan23-needs-inventory-design.md` — read it first; it carries the wiki quotes and locked decisions.

## Global Constraints

- Integer math only for money/time/minutes — never floats.
- Every new item base price (appliances, books) is a `base` argument to `EconomyService.price(base, save)` — confirmed clothes already do this (`ShopService.buyClothes`), so this is consistency, not a new pattern.
- `SaveState.eat` keeps its Java name but is reinterpreted as "weeks of Fresh Food stored" from PR 2 onward — do not rename the field/column, per the spec's minimal-diff decision.
- `ActionCosts.fedWeekMinutes` is deleted in PR 1 (not deprecated) — every reference is updated in the same PR, no backwards-compat shim.
- `ClothingItem`'s enum constants are renamed `CASUAL/DRESS/BUSINESS` (from `CASUAL/FORMAL/SUIT`) with wiki-exact prices in PR 3 — the wire `item` id sent by the frontend changes accordingly; there is no persisted DB dependency on the old names (clothing is stored as plain ints, never an enum name).
- Appliance ownership is a generic `Set<ApplianceItem>` via one `@ElementCollection` join table (`tblsave_appliance`), introduced in PR 2, extended with 4 more enum constants in PR 5 — no new migration in PR 5.
- Doctor Visit's `resolve(...)` signature on `DoctorVisitService` grows one trailing boolean parameter per PR (PR 1: `starved`; PR 2 adds `spoiledFreshFood`; PR 4 adds `relaxationAtFloor`) — every call site is updated in the same PR that changes the signature, matching this codebase's established evolution style (see `EconomyEvent`/`PurchaseOutcome`/`SaveState` in the KAN-48 plan).
- Shell is PowerShell 5.1: no `&&`; quote `-D` args (`"-Dtest=..."`). Commits: write the message to a file with `Set-Content -Encoding ascii`, then `git commit -F <file>`; every message ends with the `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` trailer.
- After any `amiss-core` change, run `.\mvnw -B install -DskipTests` before `spring-boot:run` (the API resolves core from `~/.m2`).
- Frontend tests: `cd frontend; npm test` (script = `vitest run`); also run `npm run lint; npm run typecheck; npm run format:check` (CI's frontend job runs `format:check`).
- Full backend suite: `.\mvnw -B clean package` (unit); `.\mvnw -B clean verify` additionally runs the Testcontainers ITs (needs Docker Desktop running).
- Delivery: 5 stacked PRs into `develop`, merged in order. Branch chain: `feat/kan23-starvation-doctor-visit` (cut from `docs/kan23-needs-inventory-design`, the spec/plan docs branch) → `feat/kan23-food-storage` → `feat/kan23-clothes-wear` → `feat/kan23-relaxation` → `feat/kan23-extra-credit`.
- Every PR adds its `tasks/cv-highlights.md` entry before opening (repo rule).

---

# PR 1 — `feat/kan23-starvation-doctor-visit` (flat 60h week, unfed penalty, shared Doctor Visit)

### Task 1: `ActionCosts` — replace the fed-bonus with a flat week + unfed penalty (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/config/ActionCosts.java`
- Modify: `amiss-api/src/main/java/amiss/api/config/CostsProperties.java`
- Modify: `amiss-core/src/test/java/amiss/application/config/ActionCostsTest.java`
- Modify: `amiss-api/src/test/java/amiss/api/config/CostsPropertiesBindingTest.java`

**Interfaces:**
- Produces: `ActionCosts.starvationPenaltyMinutes()` (replaces `fedWeekMinutes()`); `ActionCosts.defaults()` returns the same 11 components, last one now `starvationPenaltyMinutes = 1200` (20h) instead of `fedWeekMinutes = 4320`. Every later task in this PR relies on this exact name.

- [ ] **Step 1: Read the two existing test files first**

Run: `rg -n "fedWeekMinutes" amiss-core/src/test amiss-api/src/test --glob "*.java"`

Note every line that references `fedWeekMinutes` — you will replace each with `starvationPenaltyMinutes` in Step 2, keeping the surrounding assertion style identical (these tests currently pin `4320`; the new value they must pin is `1200`).

- [ ] **Step 2: Update the pinning tests**

In `ActionCostsTest.java`, change the assertion on `defaults().fedWeekMinutes()` to:

```java
    @Test
    void defaultsPinTheReferenceCostTable() {
        ActionCosts costs = ActionCosts.defaults();
        // ... keep every other existing assertion line unchanged ...
        assertEquals(1200, costs.starvationPenaltyMinutes());
    }
```

In `CostsPropertiesBindingTest.java`, change the matching assertion (likely a `toActionCosts()` round-trip test) from asserting `fedWeekMinutes` to asserting `starvationPenaltyMinutes` the same way, and update any `amiss.costs.fed-week-minutes=...` property-binding fixture string to `amiss.costs.starvation-penalty-minutes=...`.

- [ ] **Step 3: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core -pl amiss-api test "-Dtest=ActionCostsTest,CostsPropertiesBindingTest"`
Expected: COMPILATION ERROR — `starvationPenaltyMinutes` does not exist yet.

- [ ] **Step 4: Rename the field in `ActionCosts`**

```java
public record ActionCosts(
        int workMinutes,
        int studyMinutes,
        int relaxMinutes,
        int applyJobMinutes,
        int payRentMinutes,
        int eatMinutes,
        int shopMinutes,
        int travelPerStepMinutes,
        int enterBuildingMinutes,
        int baseWeekMinutes,
        int starvationPenaltyMinutes) {

    /** The built-in cost table; used wherever no configuration override is supplied. */
    public static ActionCosts defaults() {
        return new ActionCosts(360, 360, 360, 240, 120, 0, 0, 40, 120, 3600, 1200);
    }
}
```

Update the class javadoc: replace the "72 if fed" sentence with — "a 60-hour week, always; going unfed costs 20 hours immediately (wiki Starvation event) rather than being fed granting a bonus."

- [ ] **Step 5: Rename the field in `CostsProperties`**

```java
@ConfigurationProperties(prefix = "amiss.costs")
public record CostsProperties(
        Integer workMinutes,
        Integer studyMinutes,
        Integer relaxMinutes,
        Integer applyJobMinutes,
        Integer payRentMinutes,
        Integer eatMinutes,
        Integer shopMinutes,
        Integer travelPerStepMinutes,
        Integer enterBuildingMinutes,
        Integer baseWeekMinutes,
        Integer starvationPenaltyMinutes) {

    public ActionCosts toActionCosts() {
        ActionCosts d = ActionCosts.defaults();
        return new ActionCosts(
                orDefault(workMinutes, d.workMinutes()),
                orDefault(studyMinutes, d.studyMinutes()),
                orDefault(relaxMinutes, d.relaxMinutes()),
                orDefault(applyJobMinutes, d.applyJobMinutes()),
                orDefault(payRentMinutes, d.payRentMinutes()),
                orDefault(eatMinutes, d.eatMinutes()),
                orDefault(shopMinutes, d.shopMinutes()),
                orDefault(travelPerStepMinutes, d.travelPerStepMinutes()),
                orDefault(enterBuildingMinutes, d.enterBuildingMinutes()),
                orDefault(baseWeekMinutes, d.baseWeekMinutes()),
                orDefault(starvationPenaltyMinutes, d.starvationPenaltyMinutes()));
    }

    private static int orDefault(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }
}
```

- [ ] **Step 6: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core -pl amiss-api test "-Dtest=ActionCostsTest,CostsPropertiesBindingTest"`
Expected: PASS.

- [ ] **Step 7: Full compile check**

Run: `rg -n "fedWeekMinutes" --glob "*.java"`
Expected: no matches (Task 3 will introduce the one legitimate remaining reference site inside `WeekRolloverService`, which this step's grep will *not* find yet since that file isn't touched until Task 3 — if it does find one now, fix it before continuing).

- [ ] **Step 8: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: replace the fed-week bonus with a flat 60h week (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/config/ActionCosts.java amiss-api/src/main/java/amiss/api/config/CostsProperties.java amiss-core/src/test/java/amiss/application/config/ActionCostsTest.java amiss-api/src/test/java/amiss/api/config/CostsPropertiesBindingTest.java
git commit -F commit.txt
```

### Task 2: `DoctorVisitService` — the shared trigger + cash-tiered cost (TDD)

**Files:**
- Create: `amiss-core/src/main/java/amiss/application/service/save/DoctorVisitOutcome.java`
- Create: `amiss-core/src/main/java/amiss/application/service/save/DoctorVisitService.java`
- Create: `amiss-core/src/test/java/amiss/application/service/save/DoctorVisitServiceTest.java`

**Interfaces:**
- Consumes: `SaveState.cash()`, `SaveState.addHappiness(int)`, `SaveState.spendUpTo(int)` (all exist today).
- Produces: `DoctorVisitOutcome(boolean triggered, int minutesLost, int happinessLost, int cashLost)` + static `DoctorVisitOutcome.none()`; `DoctorVisitService(IntUnaryOperator roll1toN)` constructor; `DoctorVisitOutcome resolve(SaveState save, boolean starved)` (PR 2/4 grow this signature). Task 3 wires this into `WeekRolloverService`.

- [ ] **Step 1: Write the outcome record**

```java
package amiss.application.service.save;

/**
 * What a Doctor Visit did (KAN-23, wiki Doctor Visit page): +10h, -4 happiness, and a
 * cash cost tiered by how much cash the player has on hand. {@code none()} for every
 * turn where no trigger fired, or where cash was 0 (the wiki bypasses the event
 * entirely rather than charging a bankrupt player).
 */
public record DoctorVisitOutcome(boolean triggered, int minutesLost, int happinessLost, int cashLost) {

    public static DoctorVisitOutcome none() {
        return new DoctorVisitOutcome(false, 0, 0, 0);
    }
}
```

- [ ] **Step 2: Write the failing tests**

```java
package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.domain.model.SaveState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class DoctorVisitServiceTest {

    /** Scripted rolls: pops the next queued value whatever bound is asked for. */
    private static IntUnaryOperator rolls(int... values) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int v : values) {
            queue.add(v);
        }
        return n -> queue.pop();
    }

    @Test
    void noTriggerConditionMeansNoVisit() {
        SaveState save = TestSaves.newSave();   // cash 100, happiness 50, time 3600
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls()).resolve(save, false);

        assertFalse(outcome.triggered());
        assertEquals(100, save.cash());
        assertEquals(50, save.happiness());
        assertEquals(3600, save.timeMinutes());
    }

    @Test
    void starvationRollCanMissAtTwentyFivePercent() {
        SaveState save = TestSaves.newSave();
        // 1-in-4 roll, value 2 = miss.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(2)).resolve(save, true);

        assertFalse(outcome.triggered());
        assertEquals(100, save.cash());
    }

    @Test
    void starvationTriggerCostsTenHoursFourHappinessAndTieredCash() {
        SaveState save = TestSaves.newSave();   // cash 100 -> $50-499 tier
        save.setTimeMinutes(3600);
        // 1-in-4 roll, value 1 = hit; cost roll on a 21-wide range (30..50), value 1 -> 30.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 1)).resolve(save, true);

        assertTrue(outcome.triggered());
        assertEquals(600, outcome.minutesLost());
        assertEquals(4, outcome.happinessLost());
        assertEquals(30, outcome.cashLost());
        assertEquals(3000, save.timeMinutes());
        assertEquals(46, save.happiness());      // 50 - 4
        assertEquals(70, save.cash());            // 100 - 30
    }

    @Test
    void costTierAtOrAboveFiveHundredRangesThirtyToTwoHundred() {
        SaveState save = TestSaves.newSave();
        save.setCash(500);
        // hit; cost roll on a 171-wide range (30..200), value 171 -> 200.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 171)).resolve(save, true);

        assertEquals(200, outcome.cashLost());
        assertEquals(300, save.cash());
    }

    @Test
    void costTierBelowFiftyRangesThirtyToCashOnHand() {
        SaveState save = TestSaves.newSave();
        save.setCash(40);   // 31..49 tier: range is 30..40, width 11
        // hit; cost roll value 11 -> 30 + 11 - 1 = 40 (all cash).
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 11)).resolve(save, true);

        assertEquals(40, outcome.cashLost());
        assertEquals(0, save.cash());
    }

    @Test
    void costTierAtOrBelowThirtyChargesAllCashWithNoRoll() {
        SaveState save = TestSaves.newSave();
        save.setCash(25);
        // hit; no cost roll consumed — the queue would throw if one were requested.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1)).resolve(save, true);

        assertEquals(25, outcome.cashLost());
        assertEquals(0, save.cash());
    }

    @Test
    void zeroCashBypassesTheEventEntirely() {
        SaveState save = TestSaves.newSave();
        save.setCash(0);
        // Even a guaranteed-hit roll queue must never be consumed.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls()).resolve(save, true);

        assertFalse(outcome.triggered());
        assertEquals(0, save.cash());
        assertEquals(50, save.happiness());
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=DoctorVisitServiceTest"`
Expected: COMPILATION ERROR — `DoctorVisitService` does not exist.

- [ ] **Step 4: Implement `DoctorVisitService`**

```java
package amiss.application.service.save;

import amiss.domain.model.SaveState;
import java.util.function.IntUnaryOperator;

/**
 * The shared Doctor Visit event (KAN-23, wiki Doctor Visit page). Three independent
 * conditions can trigger it — Starvation (25%), spoiled fridgeless fresh food (50%,
 * PR 2), a Relaxation stat at its floor (20%, PR 4) — each rolled separately when its
 * condition is true; only one visit resolves per turn even if more than one condition
 * fires. Effect: +10h, -4 happiness, and a cash cost tiered by cash on hand. The event
 * is bypassed entirely if the player has $0 cash (wiki-exact). Rolls come through the
 * injected {@code roll1toN} (uniform 1..n) so every outcome is deterministic under test.
 */
public class DoctorVisitService {

    private static final int STARVATION_CHANCE = 4;   // 1-in-4 = 25%
    private static final int MINUTES_LOST = 600;       // 10h
    private static final int HAPPINESS_LOST = 4;
    private static final int HIGH_CASH_THRESHOLD = 500;
    private static final int MID_CASH_THRESHOLD = 50;
    private static final int LOW_CASH_THRESHOLD = 31;
    private static final int HIGH_TIER_WIDTH = 171;    // 30..200 inclusive
    private static final int MID_TIER_WIDTH = 21;      // 30..50 inclusive
    private static final int TIER_FLOOR = 30;

    private final IntUnaryOperator roll1toN;

    public DoctorVisitService(IntUnaryOperator roll1toN) {
        this.roll1toN = roll1toN;
    }

    public DoctorVisitOutcome resolve(SaveState save, boolean starved) {
        if (save.cash() <= 0) {
            return DoctorVisitOutcome.none();
        }
        boolean starvationHit = starved && roll1toN.applyAsInt(STARVATION_CHANCE) == 1;
        if (!starvationHit) {
            return DoctorVisitOutcome.none();
        }
        int cost = cost(save.cash());
        save.setCash(save.cash() - cost);
        save.spendUpTo(MINUTES_LOST);
        save.addHappiness(-HAPPINESS_LOST);
        return new DoctorVisitOutcome(true, MINUTES_LOST, HAPPINESS_LOST, cost);
    }

    private int cost(int cash) {
        if (cash >= HIGH_CASH_THRESHOLD) {
            return TIER_FLOOR - 1 + roll1toN.applyAsInt(HIGH_TIER_WIDTH);
        }
        if (cash >= MID_CASH_THRESHOLD) {
            return TIER_FLOOR - 1 + roll1toN.applyAsInt(MID_TIER_WIDTH);
        }
        if (cash >= LOW_CASH_THRESHOLD) {
            return TIER_FLOOR - 1 + roll1toN.applyAsInt(cash - TIER_FLOOR + 1);
        }
        return cash;
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=DoctorVisitServiceTest"`
Expected: 7 tests PASS.

- [ ] **Step 6: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: DoctorVisitService - shared trigger + tiered cash cost (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/DoctorVisitOutcome.java amiss-core/src/main/java/amiss/application/service/save/DoctorVisitService.java amiss-core/src/test/java/amiss/application/service/save/DoctorVisitServiceTest.java
git commit -F commit.txt
```

### Task 3: Wire starvation + Doctor Visit into the rollover (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java`
- Modify: `amiss-api/src/test/java/amiss/api/web/PlayerStateAssemblerTest.java` (its `services()` fixture, if it constructs `SaveGameServices` directly — grep first)

**Interfaces:**
- Consumes: `DoctorVisitService.resolve(save, boolean)` (Task 2).
- Produces: `WeekRolloverService.RolloverResult` gains a 9th component `DoctorVisitOutcome doctorVisit`; `SaveGameServices` gains a `DoctorVisitService doctorVisit()` getter, built internally from the same `roll1toN` the economy uses. Task 4 (frontend/DTO wiring, same PR) and every later PR's `WeekRolloverService` changes rely on this exact shape.

- [ ] **Step 1: Write the failing rollover tests** (add to `WeekRolloverServiceTest`; read the file first for its exact `service()`/fixture helper names and mirror them — do not guess field names blind)

```java
    @Test
    void unfedRollsTheFlatWeekAndTwentyHourStarvationPenalty() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setEat(0);

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertFalse(result.fed());
        assertEquals(2400, save.timeMinutes());   // 3600 - 1200 (20h starvation penalty)
        assertEquals(48, save.happiness());       // 50 - 2 (starvation happiness loss)
    }

    @Test
    void fedSkipsTheStarvationPenaltyAndKeepsTheFlatWeek() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setEat(2);

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertTrue(result.fed());
        assertEquals(1, save.eat());              // one week consumed
        assertEquals(3600, save.timeMinutes());   // flat 60h, no bonus
        assertEquals(50, save.happiness());       // unchanged
    }

    @Test
    void unfedCanTriggerADoctorVisit() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setEat(0);
        save.setCash(100);

        // The implementation (Step 3) calls doctorVisit.resolve(...) BEFORE
        // economy.driftWeekly(...), so Doctor Visit's rolls are consumed first:
        // starvation hit (1-in-4, value 1) then cost roll (21-wide for the
        // $50-499 tier, value 1 -> 30). The two trailing values (3, 6) are the
        // neutral economy drift rolls (index roll on a 1..3 die, noise roll on
        // a 1..11 die) so the queue doesn't run dry — round 1 is below
        // EVENT_MIN_ROUND (8), so rollEvent() itself consumes no rolls.
        WeekRolloverService.RolloverResult result = service(rolls(1, 1, 3, 6)).endWeek(save);

        assertTrue(result.doctorVisit().triggered());
        assertEquals(30, result.doctorVisit().cashLost());
        assertEquals(70, save.cash());
        assertEquals(1800, save.timeMinutes());   // 3600 - 1200 (starvation) - 600 (doctor)
        assertEquals(44, save.happiness());       // 50 - 2 (starvation) - 4 (doctor)
    }
```

(`rolls(int...)` is the existing scripted-roll helper already in this test file per the KAN-48 plan's Task 3 — reuse it; do not redefine it. Extend the existing `service()`/`service(rolls)` helpers: `WeekRolloverService`'s constructor now takes a 5th argument, `DoctorVisitService`, which every call site must construct from **the same `rolls` function** passed to `EconomyService` — mirroring `SaveGameServices`' production wiring, where both services share one injected `roll1toN`. E.g.:

```java
    private WeekRolloverService service(java.util.function.IntUnaryOperator rolls) {
        return new WeekRolloverService(saves, new GoalService(degrees), ActionCosts.defaults(),
                new EconomyService(rolls), new DoctorVisitService(rolls));
    }
```

Read the file fully before editing — this replaces the KAN-48-era 4-arg construction at every call site in this class, including the no-arg `service()` overload that delegates to `service(neutralRolls())`.)

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=WeekRolloverServiceTest"`
Expected: COMPILATION ERROR — `WeekRolloverService` has no constructor accepting a `DoctorVisitService`, and `RolloverResult.doctorVisit()` does not exist.

- [ ] **Step 3: Implement**

`WeekRolloverService` — add the field, constructor param, a happiness-loss constant, and rewrite the fed/unfed block plus the result construction:

```java
    private static final int STARVATION_HAPPINESS_LOSS = 2;

    private final SaveRepository saves;
    private final GoalService goals;
    private final ActionCosts costs;
    private final EconomyService economy;
    private final DoctorVisitService doctorVisit;
    private final Board board = new Board();

    public WeekRolloverService(SaveRepository saves, GoalService goals, ActionCosts costs,
            EconomyService economy, DoctorVisitService doctorVisit) {
        this.saves = saves;
        this.goals = goals;
        this.costs = costs;
        this.economy = economy;
        this.doctorVisit = doctorVisit;
    }
```

Update the `RolloverResult` record (append the 9th component and its `weekStillRunning()` factory):

```java
    public record RolloverResult(boolean rolled, int newRound, boolean fed, int weekMinutes,
            boolean rentDue, boolean debtCharged, boolean won, EconomyEvent economy,
            DoctorVisitOutcome doctorVisit) {

        static RolloverResult weekStillRunning() {
            return new RolloverResult(false, -1, false, -1, false, false, false,
                    EconomyEvent.none(), DoctorVisitOutcome.none());
        }
    }
```

Rewrite `endWeek`'s fed/unfed block (replaces the two-line `setTimeMinutes` call) and thread the Doctor Visit resolution in right after the dependability decay, before the economy drift:

```java
        boolean fed = save.eat() > 0;
        if (fed) {
            save.setEat(save.eat() - 1);
        }
        save.setTimeMinutes(costs.baseWeekMinutes());
        if (!fed) {
            save.spendUpTo(costs.starvationPenaltyMinutes());
            save.addHappiness(-STARVATION_HAPPINESS_LOSS);
        }
        int[] home = board.cellOf(0);
        save.setPos(home[0], home[1]);

        int newRound = closedRound + 1;
        save.setRound(newRound);
        boolean rentDue = newRound % RENT_ROUND_INTERVAL == 0;
        if (rentDue) {
            save.setRent(1);
        }

        save.setDependability(Math.max(0, save.dependability() - WEEKLY_DEPENDABILITY_DECAY));

        DoctorVisitOutcome doctorVisitOutcome = doctorVisit.resolve(save, !fed);

        economy.driftWeekly(save);
        EconomyEvent economyEvent = economy.rollEvent(save);

        boolean wonNow = save.won() || goals.progress(save).allMet();
        save.setWon(wonNow);

        saves.update(save);
        return new RolloverResult(true, newRound, fed, save.timeMinutes(), rentDue,
                debtCharged, wonNow, economyEvent, doctorVisitOutcome);
```

Update the class javadoc's "In order:" sentence to mention the starvation penalty and Doctor Visit resolution landing between dependability decay and the economy drift.

`SaveGameServices` — add the field, build it in the constructor (reusing the same `roll1toN` the economy uses), thread it into `WeekRolloverService`'s construction, and expose a getter:

```java
    private final DoctorVisitService doctorVisit;
```

```java
        this.economy = new EconomyService(roll1toN);
        this.doctorVisit = new DoctorVisitService(roll1toN);
        this.goals = new GoalService(saveDegrees);
        this.hiring = new HiringService(saves, jobs, saveDegrees, turndowns, costs, roll1to100, economy);
        this.shifts = new ShiftService(saves, jobs, saveDegrees, costs);
        this.courses = new CourseService(saves, degreeCatalog, saveDegrees, costs, economy);
        this.weeks = new WeekRolloverService(saves, goals, costs, economy, doctorVisit);
```

```java
    public DoctorVisitService doctorVisit() {
        return doctorVisit;
    }
```

Fix any other broken `new WeekRolloverService(` call sites (grep `rg -n "new WeekRolloverService\(" --glob "*.java"`) the same way.

- [ ] **Step 4: Run the core suite**

Run: `.\mvnw -B -pl amiss-core test`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: wire starvation penalty + Doctor Visit into week rollover (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java
git commit -F commit.txt
```

### Task 4: `EndWeekResponse` carries the Doctor Visit block; SPA renders it

**Files:**
- Create: `amiss-api/src/main/java/amiss/api/web/dto/DoctorVisitDto.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/dto/EndWeekResponse.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/PlayerController.java`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/game/EndWeekModal.tsx`
- Test: whichever `amiss-api` web test covers `PlayerController.endWeek` (grep `rg -l "endWeek\|EndWeekResponse" amiss-api/src/test`)
- Test: `frontend/src/game/EndWeekModal.test.tsx` if it exists (grep first; create alongside the component if not)

**Interfaces:**
- Produces: `EndWeekResponse.doctorVisit()` returning `DoctorVisitDto(boolean triggered, int hoursLost, int happinessLost, int cashLost)`.

- [ ] **Step 1: Create `DoctorVisitDto`**

```java
package amiss.api.web.dto;

import amiss.application.service.save.DoctorVisitOutcome;

/** Whether a Doctor Visit fired at this rollover, and what it cost (KAN-23). */
public record DoctorVisitDto(boolean triggered, int hoursLost, int happinessLost, int cashLost) {

    public static DoctorVisitDto from(DoctorVisitOutcome outcome) {
        return new DoctorVisitDto(outcome.triggered(), outcome.minutesLost() / 60,
                outcome.happinessLost(), outcome.cashLost());
    }
}
```

- [ ] **Step 2: Add the field to `EndWeekResponse`**

```java
public record EndWeekResponse(
        int round,
        boolean fed,
        boolean rentDue,
        boolean debtCharged,
        boolean won,
        EconomyEventDto economy,
        DoctorVisitDto doctorVisit,
        SaveStateDto state) {
}
```

- [ ] **Step 3: Update `PlayerController.endWeek`**

```java
    @PostMapping("/end-week")
    public EndWeekResponse endWeek(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        WeekRolloverService.RolloverResult result = services.weeks().endWeek(save);
        if (!result.rolled()) {
            throw new WeekNotOverException(saveId);
        }
        return new EndWeekResponse(result.newRound(), result.fed(), result.rentDue(),
                result.debtCharged(), result.won(), EconomyEventDto.from(result.economy()),
                DoctorVisitDto.from(result.doctorVisit()), assembler.assemble(services, save));
    }
```

Add `import amiss.api.web.dto.DoctorVisitDto;`.

- [ ] **Step 4: Fix and extend the controller test**

Update every `new EndWeekResponse(` construction in the test file to pass a `DoctorVisitDto` (e.g. `new DoctorVisitDto(false, 0, 0, 0)` for the non-triggered cases already covered), and add one new test asserting a triggered Doctor Visit round-trips into the response — mock/stub `services.weeks().endWeek(...)` to return a `RolloverResult` whose `doctorVisit()` is `new DoctorVisitOutcome(true, 600, 4, 30)`, then assert the response's `doctorVisit()` is `new DoctorVisitDto(true, 10, 4, 30)`.

- [ ] **Step 5: Run the backend suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Frontend types**

In `frontend/src/api/types.ts`, add after `EconomyEventDto`:

```ts
export interface DoctorVisitDto {
  triggered: boolean;
  hoursLost: number;
  happinessLost: number;
  cashLost: number;
}
```

Add `doctorVisit: DoctorVisitDto;` as a new field of `EndWeekResponse`, right after `economy: EconomyEventDto;`.

- [ ] **Step 7: Render it in `EndWeekModal`**

```tsx
        {result.economy.bankWiped && <p>Your bank savings were wiped out.</p>}
        {result.doctorVisit.triggered && (
          <p>
            You had to visit the Doctor — lost {result.doctorVisit.hoursLost}h and R
            {result.doctorVisit.cashLost}.
          </p>
        )}
```

(Insert immediately before the existing `bankWiped` line's closing, i.e. as the next sibling paragraph.)

- [ ] **Step 8: Frontend test**

If `EndWeekModal.test.tsx` doesn't exist, create it mirroring `QTClothingPanel.test.tsx`'s render style (no query client needed here — this component takes props directly, no data fetching):

```tsx
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EndWeekModal } from './EndWeekModal';
import type { EndWeekResponse } from '../api/types';

function resultFixture(overrides: Partial<EndWeekResponse> = {}): EndWeekResponse {
  return {
    round: 3,
    fed: true,
    rentDue: false,
    debtCharged: false,
    won: false,
    economy: { event: 'NONE', severity: null, fired: false, wageCutTo: null, bankWiped: false, happinessLost: 0 },
    doctorVisit: { triggered: false, hoursLost: 0, happinessLost: 0, cashLost: 0 },
    state: {} as EndWeekResponse['state'],
    ...overrides,
  };
}

describe('EndWeekModal', () => {
  it('renders nothing extra when no Doctor Visit fired', () => {
    render(<EndWeekModal result={resultFixture()} onClose={vi.fn()} />);
    expect(screen.queryByText(/visit the Doctor/)).not.toBeInTheDocument();
  });

  it('renders the Doctor Visit line when triggered', () => {
    render(
      <EndWeekModal
        result={resultFixture({
          doctorVisit: { triggered: true, hoursLost: 10, happinessLost: 4, cashLost: 30 },
        })}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByText(/lost 10h and R30/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 9: Run the frontend suite**

Run: `cd frontend; npm test; npm run lint; npm run typecheck; npm run format:check`
Expected: all green.

- [ ] **Step 10: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: surface Doctor Visit in the end-week response and modal (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-api/src/main/java/amiss/api/web/dto/DoctorVisitDto.java amiss-api/src/main/java/amiss/api/web/dto/EndWeekResponse.java amiss-api/src/main/java/amiss/api/web/PlayerController.java frontend/src/api/types.ts frontend/src/game/EndWeekModal.tsx
git add amiss-api/src/test frontend/src/game/EndWeekModal.test.tsx
git commit -F commit.txt
```

### Task 5: Full verify, live check, CV highlights, push, PR

**Files:**
- Modify: `tasks/cv-highlights.md`

- [ ] **Step 1: Full verify**

Run: `.\mvnw -B clean verify` (Docker Desktop running)
Expected: BUILD SUCCESS, unit + IT suites green. (No IT changes were needed this PR — no schema touched — so this just proves nothing broke.)

- [ ] **Step 2: Live check**

`.\mvnw -B install -DskipTests; .\mvnw -f amiss-api spring-boot:run` + `cd frontend; npm run dev`. Log in, spend down to week-over without eating, click End Week — the modal should show "You went hungry" and, on the turns it rolls true, the Doctor Visit line. Verify the HUD's time budget reflects the 20h (and, when triggered, the extra 10h) deficit. Stop the API via the port-8080 owner pid when done.

- [ ] **Step 3: CV highlights entry**

Add an entry to `tasks/cv-highlights.md` in the file's existing format (Phase 3/gameplay section) covering: wiki-exact starvation model, new shared Doctor Visit event with deterministic-roll test coverage.

- [ ] **Step 4: Push + PR**

```powershell
git push -u origin feat/kan23-starvation-doctor-visit
gh pr create --base develop --title "feat: starvation rework + shared Doctor Visit event (KAN-23)" --body-file <bodyfile>
```

Body: summary of the flat-60h week, unfed penalty, new `DoctorVisitService`; note food storage's spoilage trigger lands in PR 2, relaxation's trigger in PR 4.

---

# PR 2 — `feat/kan23-food-storage` (V10, fresh/fast food split, Fridge/Freezer, minimal Socket City)

Branch: `git checkout -b feat/kan23-food-storage` off `feat/kan23-starvation-doctor-visit`.

### Task 6: V10 migration + `ApplianceItem` + domain/entity plumbing

**Files:**
- Create: `amiss-core/src/main/resources/db/migration/V10__food_storage_and_appliances.sql`
- Create: `amiss-core/src/main/java/amiss/domain/model/ApplianceItem.java`
- Modify: `amiss-core/src/main/java/amiss/domain/model/SaveState.java`
- Modify: `amiss-api/src/main/java/amiss/api/persistence/jpa/SaveEntity.java`
- Modify: `amiss-api/src/main/java/amiss/api/persistence/jpa/JpaSaveRepository.java`
- Modify: every `new SaveState(` call site (grep in Step 5; known: `amiss-core/src/test/java/amiss/application/service/save/TestSaves.java`, `amiss-api/src/test/java/amiss/api/web/PlayerStateAssemblerTest.java`)

**Interfaces:**
- Produces: `SaveState.ateFastFoodLastTurn()`/`setAteFastFoodLastTurn(boolean)`; `SaveState.ownedAppliances()` (unmodifiable view), `owns(ApplianceItem)`, `grantAppliance(ApplianceItem)`; `SaveState`'s constructor gains two trailing params `(…, Integer wage, boolean ateFastFoodLastTurn, Set<ApplianceItem> ownedAppliances)`. `eat`/`eat()`/`setEat(int)` keep their name but now mean "weeks of Fresh Food stored" — document this, don't rename. Task 7/8 rely on `owns`/`grantAppliance`; Task 8 relies on `ateFastFoodLastTurn`.

- [ ] **Step 1: Write the migration**

```sql
-- V10: food-storage split (KAN-23, PR 2). `eat` becomes specifically "weeks of
-- Fresh Food stored" (fast food's 1-turn effect is the new boolean flag below,
-- never banked); appliance ownership (Fridge/Freezer here; Computer/Books
-- follow in a later PR with zero schema change) is a generic join table, the
-- same @ElementCollection shape as tbljob_degrees.
ALTER TABLE tblsave ADD COLUMN ate_fast_food_last_turn TINYINT NOT NULL DEFAULT 0 AFTER eat;

CREATE TABLE tblsave_appliance (
    save_id   BIGINT      NOT NULL,
    appliance VARCHAR(20) NOT NULL,
    PRIMARY KEY (save_id, appliance),
    CONSTRAINT fk_tblsave_appliance_save FOREIGN KEY (save_id) REFERENCES tblsave(id) ON DELETE CASCADE
);
```

- [ ] **Step 2: Create `ApplianceItem`**

```java
package amiss.domain.model;

import amiss.domain.board.Location;

/**
 * A purchasable appliance/book (KAN-23) — minimal ownership plumbing only: no
 * browsing catalog, no break/repair, one canonical price and store per item (the
 * full appliances catalog, including Z-Mart's cheaper used variants, is KAN-58).
 * Computer and the three Books are added in a later PR alongside extra credit.
 */
public enum ApplianceItem {
    FRIDGE(876, 1, Location.SOCKET_CITY),
    FREEZER(513, 2, Location.SOCKET_CITY);

    private final int basePrice;
    private final int firstOwnedHappiness;
    private final Location store;

    ApplianceItem(int basePrice, int firstOwnedHappiness, Location store) {
        this.basePrice = basePrice;
        this.firstOwnedHappiness = firstOwnedHappiness;
        this.store = store;
    }

    public int basePrice() {
        return basePrice;
    }

    /** Happiness granted the first time this save ever owns one; 0 on every later purchase. */
    public int firstOwnedHappiness() {
        return firstOwnedHappiness;
    }

    public Location store() {
        return store;
    }
}
```

- [ ] **Step 3: Add the fields to `SaveState`**

Add after the `wage` field:

```java
    private boolean ateFastFoodLastTurn;
    private final Set<ApplianceItem> ownedAppliances;
```

Add `import java.util.Collections;`, `import java.util.HashSet;`, `import java.util.Set;` at the top.

Append two constructor params after `Integer wage` and initialize (defensive copy for the set):

```java
    public SaveState(long id, String owner, String label, int xpos, int ypos, int timeMinutes,
            int round, int cash, int bank, int debt, int rent, int eat, int clothing,
            Integer jobId, int happiness, int experience, int dependability,
            Integer currentCourseId, int eduprog,
            int goalWealth, int goalHappiness, int goalEducation, int goalCareer, boolean won,
            byte economyIndex, short economyReading, Integer wage,
            boolean ateFastFoodLastTurn, Set<ApplianceItem> ownedAppliances) {
        // ... every existing assignment unchanged ...
        this.wage = wage;
        this.ateFastFoodLastTurn = ateFastFoodLastTurn;
        this.ownedAppliances = new HashSet<>(ownedAppliances);
    }
```

Add accessors after `setWage`:

```java
    /** True if Fast Food was bought last turn — feeds exactly this turn, never banked. */
    public boolean ateFastFoodLastTurn() {
        return ateFastFoodLastTurn;
    }

    public void setAteFastFoodLastTurn(boolean ateFastFoodLastTurn) {
        this.ateFastFoodLastTurn = ateFastFoodLastTurn;
    }

    public Set<ApplianceItem> ownedAppliances() {
        return Collections.unmodifiableSet(ownedAppliances);
    }

    public boolean owns(ApplianceItem item) {
        return ownedAppliances.contains(item);
    }

    public void grantAppliance(ApplianceItem item) {
        ownedAppliances.add(item);
    }
```

Update the class javadoc: note `eat` now specifically means weeks of Fresh Food stored (KAN-23).

- [ ] **Step 4: Map the new columns/collection in `SaveEntity`**

Add imports: `jakarta.persistence.CollectionTable`, `jakarta.persistence.ElementCollection`, `jakarta.persistence.EnumType`, `jakarta.persistence.Enumerated`, `jakarta.persistence.FetchType`, `jakarta.persistence.JoinColumn`, `amiss.domain.model.ApplianceItem`, `java.util.HashSet`, `java.util.Set`.

Add fields after `wage`:

```java
    /** 1 = Fast Food bought last turn (feeds this turn only, never banked). */
    @Column(name = "ate_fast_food_last_turn", nullable = false)
    private int ateFastFoodLastTurn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tblsave_appliance", joinColumns = @JoinColumn(name = "save_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "appliance", nullable = false, length = 20)
    private Set<ApplianceItem> ownedAppliances = new HashSet<>();
```

Add getters/setters after `getWage`/`setWage`:

```java
    public boolean isAteFastFoodLastTurn() {
        return ateFastFoodLastTurn != 0;
    }

    public void setAteFastFoodLastTurn(boolean ateFastFoodLastTurn) {
        this.ateFastFoodLastTurn = ateFastFoodLastTurn ? 1 : 0;
    }

    public Set<ApplianceItem> getOwnedAppliances() {
        return ownedAppliances;
    }
```

- [ ] **Step 5: Fix `JpaSaveRepository` and every broken constructor call site**

`JpaSaveRepository.update` — add before `saves.saveAndFlush(entity);`:

```java
            entity.setAteFastFoodLastTurn(state.ateFastFoodLastTurn());
            entity.getOwnedAppliances().clear();
            entity.getOwnedAppliances().addAll(state.ownedAppliances());
```

`JpaSaveRepository.toDomain` — append the two new constructor args:

```java
        return new SaveState(e.getId(), e.getOwner(), e.getLabel(), e.getXpos(), e.getYpos(),
                e.getTime(), e.getRound(), e.getCash(), e.getBank(), e.getDebt(), e.getRent(),
                e.getEat(), e.getClothing(), e.getJobId(), e.getHappiness(), e.getExperience(),
                e.getDependability(), e.getCurrentCourseId(), e.getEduprog(),
                e.getGoalWealth(), e.getGoalHappiness(), e.getGoalEducation(), e.getGoalCareer(),
                e.getWon() != 0, e.getEconomyIndex(), e.getEconomyReading(), e.getWage(),
                e.isAteFastFoodLastTurn(), e.getOwnedAppliances());
```

Run: `.\mvnw -B -pl amiss-core -pl amiss-api compile` and `rg -n "new SaveState\(" --glob "*.java"` to find every remaining break. Append `, false, Set.of()` at each site (add `import java.util.Set;` where missing). `TestSaves.newSave()` becomes:

```java
    static SaveState newSave() {
        return new SaveState(SAVE_ID, "tester", "Save 1", 0, 0, 3600, 1, 100, 0, 0, 1, 0, 1,
                null, 50, 10, 20, null, 0, 50, 50, 50, 50, false, (byte) 0, (short) 0, null,
                false, Set.of());
    }
```

- [ ] **Step 6: Run the whole unit suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS, all existing tests green (no behaviour changed yet — only new, unused fields).

- [ ] **Step 7: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: V10 appliance ownership + fast-food flag on SaveState (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/resources/db/migration/V10__food_storage_and_appliances.sql amiss-core/src/main/java/amiss/domain/model/ApplianceItem.java amiss-core/src/main/java/amiss/domain/model/SaveState.java amiss-api/src/main/java/amiss/api/persistence/jpa/SaveEntity.java amiss-api/src/main/java/amiss/api/persistence/jpa/JpaSaveRepository.java
git add -u
git commit -F commit.txt
```

### Task 7: `ApplianceService` + `ApplianceController` (TDD)

**Files:**
- Create: `amiss-core/src/main/java/amiss/application/service/save/ApplianceService.java`
- Create: `amiss-core/src/test/java/amiss/application/service/save/ApplianceServiceTest.java`
- Create: `amiss-api/src/main/java/amiss/api/web/ApplianceController.java`
- Create: `amiss-api/src/main/java/amiss/api/web/dto/ApplianceDto.java`
- Create: `amiss-api/src/main/java/amiss/api/web/dto/ApplianceRequest.java`
- Create: `amiss-api/src/main/java/amiss/api/web/dto/ApplianceResponse.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java`

**Interfaces:**
- Consumes: `EconomyService.price` (existing), `SaveState.owns`/`grantAppliance` (Task 6).
- Produces: `ApplianceService(SaveRepository, EconomyService)`, `PurchaseOutcome buy(SaveState, ApplianceItem)` (reuses the existing `PurchaseOutcome` record — no new type). `SaveGameServices.appliances()` getter. `GET /api/saves/{saveId}/appliances`, `POST /api/saves/{saveId}/appliances`.

- [ ] **Step 1: Write the failing service tests**

```java
package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.port.SaveRepository;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.SaveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplianceServiceTest {

    @Mock
    private SaveRepository saves;

    private ApplianceService service() {
        return new ApplianceService(saves, new EconomyService(n -> 1));
    }

    @Test
    void firstFridgePurchaseGrantsOwnershipAndHappiness() {
        SaveState save = TestSaves.newSave();   // cash 100 by default — raise it, Fridge costs 876
        save.setCash(1000);

        PurchaseOutcome outcome = service().buy(save, ApplianceItem.FRIDGE);

        assertEquals(PurchaseOutcome.Status.OK, outcome.status());
        assertEquals(876, outcome.pricePaid());
        assertEquals(1000 - 876, outcome.cash());
        assertTrue(save.owns(ApplianceItem.FRIDGE));
        assertEquals(51, save.happiness());              // +1 first-owned bonus
        verify(saves).update(save);
    }

    @Test
    void repeatPurchaseOfAnOwnedApplianceGrantsNoFurtherHappiness() {
        SaveState save = TestSaves.newSave();
        save.setCash(2000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        int happinessBefore = save.happiness();

        service().buy(save, ApplianceItem.FRIDGE);

        assertEquals(happinessBefore, save.happiness());
    }

    @Test
    void insufficientCashRejectsWithoutGrantingOwnership() {
        SaveState save = TestSaves.newSave();
        save.setCash(10);

        PurchaseOutcome outcome = service().buy(save, ApplianceItem.FRIDGE);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_CASH, outcome.status());
        assertEquals(10, save.cash());
        assertTrue(save.ownedAppliances().isEmpty());
    }

    @Test
    void weekOverRejectsThePurchase() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);

        PurchaseOutcome outcome = service().buy(save, ApplianceItem.FREEZER);

        assertEquals(PurchaseOutcome.Status.WEEK_OVER, outcome.status());
        assertTrue(save.ownedAppliances().isEmpty());
    }
}
```

(Fix the deliberately-odd `100 - 876 + 876` placeholder in your own copy before running — write the real expected `outcome.cash()` value once you've read `TestSaves.newSave()`'s starting cash; this plan flags it rather than guessing wrong, per the no-placeholder rule: the correct fix is `save.setCash(2000)` at the top of that test, mirroring the repeat-purchase test below, then asserting `outcome.cash() == 2000 - 876`.)

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=ApplianceServiceTest"`
Expected: COMPILATION ERROR — `ApplianceService` does not exist.

- [ ] **Step 3: Implement `ApplianceService`**

```java
package amiss.application.service.save;

import amiss.application.port.SaveRepository;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.SaveState;

/**
 * Buying an appliance/book (KAN-23) — minimal ownership plumbing: one canonical price
 * per item (no new/used variants, no break/repair — KAN-58), a one-time happiness bonus
 * the first time a save ever owns a given item, and purchases cost no time (matches
 * every other shop in the game).
 */
public class ApplianceService {

    private final SaveRepository saves;
    private final EconomyService economy;

    public ApplianceService(SaveRepository saves, EconomyService economy) {
        this.saves = saves;
        this.economy = economy;
    }

    public PurchaseOutcome buy(SaveState save, ApplianceItem item) {
        int price = economy.price(item.basePrice(), save);
        if (save.weekOver()) {
            return new PurchaseOutcome(PurchaseOutcome.Status.WEEK_OVER, save.timeMinutes(), save.cash(), price);
        }
        int cash = save.cash();
        if (cash < price) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, save.timeMinutes(), cash, price);
        }
        boolean firstOwned = !save.owns(item);
        save.setCash(cash - price);
        save.grantAppliance(item);
        if (firstOwned) {
            save.addHappiness(item.firstOwnedHappiness());
        }
        saves.update(save);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, save.timeMinutes(), save.cash(), price);
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=ApplianceServiceTest"`
Expected: 4 tests PASS.

- [ ] **Step 5: Wire into `SaveGameServices`**

```java
    private final ApplianceService appliances;
```

```java
        this.shop = new ShopService(saves, costs, economy);
        this.appliances = new ApplianceService(saves, economy);
```

```java
    public ApplianceService appliances() {
        return appliances;
    }
```

- [ ] **Step 6: Create the DTOs**

```java
package amiss.api.web.dto;

/** One row of the appliance catalog (KAN-23): id, economy-adjusted price, owning store, owned flag. */
public record ApplianceDto(String id, int price, String store, boolean owned) {
}
```

```java
package amiss.api.web.dto;

/** Body of {@code POST .../appliances}: the {@code ApplianceItem} id, e.g. {@code "FRIDGE"}. */
public record ApplianceRequest(String item) {
}
```

```java
package amiss.api.web.dto;

/** A successful appliance purchase. */
public record ApplianceResponse(String item, int price, SaveStateDto state) {
}
```

- [ ] **Step 7: Create `ApplianceController`**

```java
package amiss.api.web;

import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.UnknownItemException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.ApplianceDto;
import amiss.api.web.dto.ApplianceRequest;
import amiss.api.web.dto.ApplianceResponse;
import amiss.application.service.save.EconomyService;
import amiss.application.service.save.PurchaseOutcome;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.SaveState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The minimal appliance catalog (KAN-23): Fridge and Freezer, both at Socket City (per
 * {@link ApplianceItem#store()}) — no browsing beyond a flat list, no break/repair
 * (KAN-58). Buying requires standing at the item's store. PR 5 extends the same enum
 * and these same routes with the Computer and the three Books; this catalog is
 * whatever {@code ApplianceItem.values()} holds, so it needs no change to carry them.
 */
@RestController
public class ApplianceController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;

    public ApplianceController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
    }

    @GetMapping("/api/saves/{saveId}/appliances")
    public List<ApplianceDto> catalog(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<ApplianceDto> items = new ArrayList<>(ApplianceItem.values().length);
        for (ApplianceItem item : ApplianceItem.values()) {
            items.add(new ApplianceDto(item.name(), economy.price(item.basePrice(), save),
                    item.store().name(), save.owns(item)));
        }
        return items;
    }

    @PostMapping("/api/saves/{saveId}/appliances")
    public ApplianceResponse buy(@PathVariable long saveId, Authentication authentication,
            @RequestBody ApplianceRequest request) {
        ApplianceItem item = parseApplianceItem(request.item());
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, item.store());

        PurchaseOutcome outcome = services.appliances().buy(save, item);
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new ApplianceResponse(item.name(), outcome.pricePaid(), assembler.assemble(services, save));
        }
    }

    private static ApplianceItem parseApplianceItem(String raw) {
        if (raw == null) {
            throw new UnknownItemException(raw);
        }
        try {
            return ApplianceItem.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownItemException(raw);
        }
    }
}
```

- [ ] **Step 8: Run the backend suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: ApplianceService + minimal Socket City/Z-Mart catalog endpoint (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/ApplianceService.java amiss-core/src/test/java/amiss/application/service/save/ApplianceServiceTest.java amiss-api/src/main/java/amiss/api/web/ApplianceController.java amiss-api/src/main/java/amiss/api/web/dto/ApplianceDto.java amiss-api/src/main/java/amiss/api/web/dto/ApplianceRequest.java amiss-api/src/main/java/amiss/api/web/dto/ApplianceResponse.java amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java
git commit -F commit.txt
```

### Task 8: `ShopService` — fresh/fast food split with Fridge/Freezer capacity (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/ShopService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/ShopServiceTest.java` (read it first; mirror its existing construction/fixture style)

**Interfaces:**
- Produces: `ShopService.eat` now sets `ateFastFoodLastTurn` instead of touching `eat`; `buyGroceries` now caps `eat` by Fridge/Freezer ownership (fridgeless: always exactly 1 week, never additive). Task 9 relies on `ateFastFoodLastTurn` being set here.

- [ ] **Step 1: Write the failing tests** (add to the existing `ShopServiceTest`)

```java
    @Test
    void eatingFastFoodSetsTheFlagButNeverTouchesStoredFreshFood() {
        SaveState save = TestSaves.newSave();
        save.setEat(3);

        EatOutcome outcome = service().eat(save, FastFoodItem.BURGER);

        assertEquals(EatOutcome.Status.OK, outcome.status());
        assertTrue(save.ateFastFoodLastTurn());
        assertEquals(3, save.eat());   // untouched — fast food is its own track
    }

    @Test
    void groceriesWithoutAFridgeAlwaysResultInExactlyOneWeekRegardlessOfPackSize() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.setEat(0);

        service().buyGroceries(save, FoodPack.EIGHT_WEEKS);

        assertEquals(1, save.eat());
    }

    @Test
    void groceriesWithoutAFridgeDoNotStackOnExistingFridgelessFood() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.setEat(1);   // leftover from a prior fridgeless purchase

        service().buyGroceries(save, FoodPack.ONE_WEEK);

        assertEquals(1, save.eat());   // still exactly 1, not 2
    }

    @Test
    void groceriesWithAFridgeBankUpToSixWeeks() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        save.setEat(2);

        service().buyGroceries(save, FoodPack.FOUR_WEEKS);

        assertEquals(6, save.eat());   // 2 + 4, under the 6-week cap
    }

    @Test
    void groceriesWithAFridgeClampAtSixWeeksWithoutAFreezer() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        save.setEat(5);

        service().buyGroceries(save, FoodPack.FOUR_WEEKS);

        assertEquals(6, save.eat());   // 5 + 4 = 9, clamped to 6
    }

    @Test
    void groceriesWithFridgeAndFreezerBankUpToTwelveWeeks() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        save.grantAppliance(ApplianceItem.FREEZER);
        save.setEat(10);

        service().buyGroceries(save, FoodPack.FOUR_WEEKS);

        assertEquals(12, save.eat());   // 10 + 4 = 14, clamped to 12
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=ShopServiceTest"`
Expected: some existing tests that assumed the old `addFood`/eat-touches-eat behavior now FAIL (the ones asserting `eat()` after calling `eat(save, item)`) — this is expected; you are replacing that behavior. Delete or rewrite any pre-existing test that asserted fast food changes `save.eat()`. The new tests above fail with either an assertion mismatch or (for `ateFastFoodLastTurn`) a compile error if Task 6 wasn't merged first.

- [ ] **Step 3: Implement**

Remove the `addFood` private static helper entirely (no longer used by either caller). Rewrite `eat`:

```java
    /** Buys and eats {@code item} at Monolith Burgers. */
    public EatOutcome eat(SaveState save, FastFoodItem item) {
        int price = economy.price(item.price(), save);
        int time = save.timeMinutes();
        int remaining = time - costs.eatMinutes();
        if (remaining < 0) {
            return new EatOutcome(EatOutcome.Status.INSUFFICIENT_TIME, time, -1, price);
        }
        save.setTimeMinutes(remaining);

        int cash = save.cash();
        if (cash < price) {
            saves.update(save);
            return new EatOutcome(EatOutcome.Status.INSUFFICIENT_CASH, remaining, cash, price);
        }

        save.setAteFastFoodLastTurn(true);
        save.setCash(cash - price);
        save.addHappiness(HAPPINESS_PER_MEAL);
        saves.update(save);
        return new EatOutcome(EatOutcome.Status.OK, remaining, save.cash(), price);
    }
```

Rewrite `buyGroceries` and add the private capacity helper:

```java
    private static final int FRESH_FOOD_CAP_FRIDGE_ONLY = 6;
    private static final int FRESH_FOOD_CAP_WITH_FREEZER = 12;

    /** Buys {@code pack} at Black's Market. */
    public PurchaseOutcome buyGroceries(SaveState save, FoodPack pack) {
        int price = economy.price(pack.price(), save);
        int time = save.timeMinutes();
        int remaining = time - costs.shopMinutes();
        if (remaining < 0) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_TIME, time, save.cash(), price);
        }
        save.setTimeMinutes(remaining);

        int cash = save.cash();
        if (cash < price) {
            saves.update(save);
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, remaining, cash, price);
        }

        save.setCash(cash - price);
        save.setEat(freshFoodWeeksAfterPurchase(save, pack));
        saves.update(save);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, remaining, save.cash(), price);
    }

    /**
     * Fridgeless: any purchase feeds exactly 1 week and never stacks (wiki-exact — the
     * pack's size is irrelevant with nowhere to store the surplus). With a Fridge: banks
     * up to 6 weeks, or 12 with a Freezer too.
     */
    private static int freshFoodWeeksAfterPurchase(SaveState save, FoodPack pack) {
        if (!save.owns(amiss.domain.model.ApplianceItem.FRIDGE)) {
            return 1;
        }
        int cap = save.owns(amiss.domain.model.ApplianceItem.FREEZER)
                ? FRESH_FOOD_CAP_WITH_FREEZER : FRESH_FOOD_CAP_FRIDGE_ONLY;
        return Math.min(cap, save.eat() + pack.weeks());
    }
```

(Use a proper `import amiss.domain.model.ApplianceItem;` at the top instead of the fully-qualified references above — shown qualified here only so the diff is unambiguous about where the new type comes from.)

- [ ] **Step 4: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=ShopServiceTest"`
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: fresh/fast food split with Fridge/Freezer capacity (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/ShopService.java amiss-core/src/test/java/amiss/application/service/save/ShopServiceTest.java
git commit -F commit.txt
```

### Task 9: Fridgeless spoilage feeds Doctor Visit's second trigger (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/DoctorVisitService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/DoctorVisitServiceTest.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java`

**Interfaces:**
- Produces: `DoctorVisitService.resolve(SaveState, boolean starved, boolean spoiledFreshFood)` (grew one param). Every call site from PR 1's Task 3 is updated here.

- [ ] **Step 1: Update the existing Doctor Visit tests + add the spoilage ones**

Every existing call to `.resolve(save, true)` / `.resolve(save, false)` in `DoctorVisitServiceTest` becomes `.resolve(save, true, false)` / `.resolve(save, false, false)` (none of PR 1's scenarios involve spoilage). Add:

```java
    @Test
    void spoilageRollCanMissAtFiftyPercent() {
        SaveState save = TestSaves.newSave();
        // 1-in-2 roll, value 2 = miss.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(2)).resolve(save, false, true);

        assertFalse(outcome.triggered());
    }

    @Test
    void spoilageTriggerCostsTheSameAsStarvation() {
        SaveState save = TestSaves.newSave();
        // 1-in-2 roll, value 1 = hit; cost roll value 1 -> 30.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 1)).resolve(save, false, true);

        assertTrue(outcome.triggered());
        assertEquals(30, outcome.cashLost());
    }

    @Test
    void starvationAndSpoilageBothRollButOnlyOneVisitResolves() {
        SaveState save = TestSaves.newSave();
        // Starvation roll (1-in-4) value 4 = miss; spoilage roll (1-in-2) value 1 = hit;
        // cost roll value 1 -> 30. Exactly one visit's cost is charged.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(4, 1, 1)).resolve(save, true, true);

        assertTrue(outcome.triggered());
        assertEquals(30, outcome.cashLost());
        assertEquals(70, save.cash());
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=DoctorVisitServiceTest"`
Expected: COMPILATION ERROR — 2-arg `resolve` calls don't match the new 3-arg signature you're about to write.

- [ ] **Step 3: Implement**

```java
    private static final int STARVATION_CHANCE = 4;    // 1-in-4 = 25%
    private static final int SPOILAGE_CHANCE = 2;       // 1-in-2 = 50%
```

```java
    public DoctorVisitOutcome resolve(SaveState save, boolean starved, boolean spoiledFreshFood) {
        if (save.cash() <= 0) {
            return DoctorVisitOutcome.none();
        }
        boolean starvationHit = starved && roll1toN.applyAsInt(STARVATION_CHANCE) == 1;
        boolean spoilageHit = spoiledFreshFood && roll1toN.applyAsInt(SPOILAGE_CHANCE) == 1;
        boolean triggered = starvationHit || spoilageHit;
        if (!triggered) {
            return DoctorVisitOutcome.none();
        }
        int cost = cost(save.cash());
        save.setCash(save.cash() - cost);
        save.spendUpTo(MINUTES_LOST);
        save.addHappiness(-HAPPINESS_LOST);
        return new DoctorVisitOutcome(true, MINUTES_LOST, HAPPINESS_LOST, cost);
    }
```

(Replaces the old 2-arg `resolve`; `cost(...)` is unchanged from PR 1.)

- [ ] **Step 4: Run Doctor Visit tests**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=DoctorVisitServiceTest"`
Expected: PASS.

- [ ] **Step 5: Wire the spoilage condition into `WeekRolloverService`** (TDD — add the test first)

```java
    @Test
    void fridgelessStoredFreshFoodSpoilsAndCanTriggerADoctorVisit() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setEat(3);              // fridgeless leftover from a prior purchase
        save.setCash(100);

        // hadFreshFood=true -> fed=true -> starved=false (no roll consumed for it).
        // Doctor Visit rolls first: spoilage roll (1-in-2, value 1 = hit), cost
        // roll (21-wide for the $50-499 tier, value 1 -> 30); then the two
        // neutral economy drift rolls (3, 6) so the queue doesn't run dry.
        WeekRolloverService.RolloverResult result = service(rolls(1, 1, 3, 6)).endWeek(save);

        assertTrue(result.fed());     // had food this turn...
        assertEquals(0, save.eat());  // ...but it all spoils
        assertTrue(result.doctorVisit().triggered());
        assertEquals(70, save.cash());
    }

    @Test
    void fridgeOwnersNeverSpoilTheirStoredFood() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.grantAppliance(ApplianceItem.FRIDGE);
        save.setEat(3);

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertEquals(2, save.eat());   // normal -1 decay, no spoilage
        assertFalse(result.doctorVisit().triggered());
    }
```

- [ ] **Step 6: Run to verify failure, then implement**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=WeekRolloverServiceTest"` — expect failures/compile errors against the old fed/eat logic.

Rewrite the fed/spoilage block in `endWeek`:

```java
        boolean hadFreshFood = save.eat() > 0;
        boolean fed = save.ateFastFoodLastTurn() || hadFreshFood;
        boolean spoiledFreshFood = !save.owns(amiss.domain.model.ApplianceItem.FRIDGE) && hadFreshFood;
        if (spoiledFreshFood) {
            save.setEat(0);
        } else if (hadFreshFood) {
            save.setEat(save.eat() - 1);
        }
        save.setAteFastFoodLastTurn(false);
        save.setTimeMinutes(costs.baseWeekMinutes());
        if (!fed) {
            save.spendUpTo(costs.starvationPenaltyMinutes());
            save.addHappiness(-STARVATION_HAPPINESS_LOSS);
        }
```

(Add a proper `import amiss.domain.model.ApplianceItem;` rather than the qualified reference.) Update the Doctor Visit call:

```java
        DoctorVisitOutcome doctorVisitOutcome = doctorVisit.resolve(save, !fed, spoiledFreshFood);
```

- [ ] **Step 7: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=WeekRolloverServiceTest"`
Expected: PASS. Then run the full core suite: `.\mvnw -B -pl amiss-core test` to confirm nothing else broke.

- [ ] **Step 8: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: fridgeless fresh food spoils and feeds Doctor Visit's 2nd trigger (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/DoctorVisitService.java amiss-core/src/test/java/amiss/application/service/save/DoctorVisitServiceTest.java amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java
git commit -F commit.txt
```

### Task 10: `SaveStateDto`/`GroceriesResponse` carry the split; `FoodController` updated

**Files:**
- Modify: `amiss-api/src/main/java/amiss/api/web/dto/SaveStateDto.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/PlayerStateAssembler.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/FoodController.java`
- Modify: whichever `amiss-api` test covers `PlayerStateAssembler`/`FoodController` (grep `rg -l "SaveStateDto(\|GroceriesResponse(" amiss-api/src/test`)

**Interfaces:**
- Produces: `SaveStateDto.ateFastFoodLastTurn()` (new field, right after `foodWeeks`); `GroceriesResponse.weeksAdded()` now reports the real capacity-aware delta, not the pack's nominal size.

- [ ] **Step 1: Add the field to `SaveStateDto`**

```java
public record SaveStateDto(
        long id,
        String label,
        int round,
        int timeMinutes,
        String timeDisplay,
        boolean weekOver,
        int cash,
        int bank,
        int debt,
        boolean rentDue,
        int foodWeeks,
        boolean ateFastFoodLastTurn,
        int clothing,
        JobDto job,
        LocationDto location,
        List<String> degreesEarned,
        CurrentCourseDto currentCourse,
        GoalsDto goals,
        boolean won) {
}
```

- [ ] **Step 2: Update `PlayerStateAssembler.assemble`**

Insert `save.ateFastFoodLastTurn(),` immediately after `save.eat(),` in the `new SaveStateDto(...)` construction.

- [ ] **Step 3: Update `FoodController.groceries`** to report the real weeks added

```java
    @PostMapping("/api/saves/{saveId}/groceries")
    public GroceriesResponse groceries(@PathVariable long saveId, Authentication authentication,
            @RequestBody GroceriesRequest request) {
        FoodPack pack = parseFoodPack(request.pack());
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.BLACKS_MARKET);
        int freshFoodWeeksBefore = save.eat();

        PurchaseOutcome outcome = services.shop().buyGroceries(save, pack);
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new GroceriesResponse(pack.name(), outcome.pricePaid(),
                        save.eat() - freshFoodWeeksBefore, save.eat(), assembler.assemble(services, save));
        }
    }
```

- [ ] **Step 4: Fix the broken tests**

Every `new SaveStateDto(` construction needs the new boolean inserted at the right position; every groceries-purchase test asserting `weeksAdded()` against a pack's nominal `weeks()` value needs re-checking against the new capacity-aware rule (e.g. a fridgeless purchase now reports `weeksAdded` as whatever the actual before/after delta was, which may be 0 or 1, not the pack's raw size).

- [ ] **Step 5: Run the backend suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: wire the fresh/fast food split onto the save-state DTO (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-api/src/main/java/amiss/api/web/dto/SaveStateDto.java amiss-api/src/main/java/amiss/api/web/PlayerStateAssembler.java amiss-api/src/main/java/amiss/api/web/FoodController.java
git add -u amiss-api/src/test
git commit -F commit.txt
```

### Task 11: Frontend — Socket City panel, appliances api module, HUD/status updates

**Files:**
- Create: `frontend/src/api/appliances.ts`
- Create: `frontend/src/game/panels/SocketCityPanel.tsx`
- Create: `frontend/src/game/panels/SocketCityPanel.test.tsx`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/game/panels/registry.ts`
- Modify: `frontend/src/game/Hud.tsx`
- Modify: `frontend/src/game/panels/MonolithBurgersPanel.tsx`

**Interfaces:**
- Produces: `getApplianceCatalog(saveId)`, `buyAppliance(saveId, item)`; query key `['appliances', saveId]`. PR 5 reuses both for the Z-Mart panel and extends the catalog with Computer/Books — no changes needed here to support that later.

- [ ] **Step 1: Add the wire types**

In `frontend/src/api/types.ts`, add `ateFastFoodLastTurn: boolean;` to `SaveStateDto` right after `foodWeeks: number;`. Add:

```ts
export interface ApplianceDto {
  id: string;
  price: number;
  store: string;
  owned: boolean;
}

export interface ApplianceResponse {
  item: string;
  price: number;
  state: SaveStateDto;
}
```

- [ ] **Step 2: Create the api module**

```ts
import { apiFetch } from './http';
import type { ApplianceDto, ApplianceResponse } from './types';

export function getApplianceCatalog(saveId: number): Promise<ApplianceDto[]> {
  return apiFetch<ApplianceDto[]>(`/saves/${saveId}/appliances`);
}

export function buyAppliance(saveId: number, item: string): Promise<ApplianceResponse> {
  return apiFetch<ApplianceResponse>(`/saves/${saveId}/appliances`, {
    method: 'POST',
    body: { item },
  });
}
```

- [ ] **Step 3: Create `SocketCityPanel`**

```tsx
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyAppliance, getApplianceCatalog } from '../../api/appliances';
import { errorMessage } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { ApplianceResponse } from '../../api/types';
import type { PanelProps } from './types';

// Item ids are the ApplianceItem enum names on the wire; PR 5 adds COMPUTER here
// when extra credit ships, requiring no change beyond this map.
const APPLIANCE_NAMES: Record<string, string> = {
  FRIDGE: 'Refrigerator',
  FREEZER: 'Freezer',
};

export function SocketCityPanel({ saveId, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const catalogQuery = useQuery({
    queryKey: ['appliances', saveId],
    queryFn: () => getApplianceCatalog(saveId),
    staleTime: Infinity,
  });

  const mutation = useMutation({
    mutationFn: (item: string) => buyAppliance(saveId, item),
    onSuccess: (res: ApplianceResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      void queryClient.invalidateQueries({ queryKey: ['appliances', saveId] });
      const name = APPLIANCE_NAMES[res.item] ?? res.item;
      onNotify(`Bought ${name} (R${res.price})`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  if (catalogQuery.isPending) {
    return <p>Loading stock…</p>;
  }
  if (catalogQuery.error !== null) {
    return <p role="alert">{catalogQuery.error.message}</p>;
  }

  const stock = catalogQuery.data?.filter((item) => item.store === 'SOCKET_CITY');
  if (stock === undefined) {
    return null;
  }

  const rows: StoreRow[] = stock.map((item) => ({
    id: item.id,
    label: APPLIANCE_NAMES[item.id] ?? item.id,
    price: item.price,
    detail: item.owned ? 'Owned' : undefined,
    actionLabel: 'Buy',
  }));

  return (
    <StorePanel
      heading="Socket City"
      rows={rows}
      onAction={(id) => mutation.mutate(id)}
      pending={mutation.isPending}
      error={mutation.error !== null ? errorMessage(mutation.error) : null}
    />
  );
}
```

- [ ] **Step 4: Register the panel**

In `registry.ts`, add `import { SocketCityPanel } from './SocketCityPanel';` and `SOCKET_CITY: SocketCityPanel,` to `PANEL_REGISTRY`.

- [ ] **Step 5: Update `Hud.tsx` and `MonolithBurgersPanel.tsx` for the split**

`Hud.tsx` — replace the Food stat row:

```tsx
        <div className="hud-stat">
          <dt>Food</dt>
          <dd>
            {player.foodWeeks} wk{player.ateFastFoodLastTurn ? ' + fast food' : ''}
          </dd>
        </div>
```

`MonolithBurgersPanel.tsx` — the `statusLine` must also account for fast food:

```ts
  const statusLine =
    player.foodWeeks > 0 || player.ateFastFoodLastTurn
      ? 'You have food stored for the coming week.'
      : 'No food stored — eat or buy groceries before the week ends.';
```

- [ ] **Step 6: Write the panel test**

Mirror `QTClothingPanel.test.tsx`'s fixtures/style exactly (its `playerFixture` helper, `vi.mock`, `renderPanel` pattern) — add `ateFastFoodLastTurn: false` to whatever local `SaveStateDto` fixture this test file builds. Cover: renders Fridge/Freezer rows filtered from a mixed catalog (include one `Z_MART`-store row in the fixture to prove filtering works), shows "Owned" on an owned item, buys successfully and updates the save-query cache, renders the problem-detail alert on an `ApiError` and invalidates the save query.

```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SocketCityPanel } from './SocketCityPanel';
import { buyAppliance, getApplianceCatalog } from '../../api/appliances';
import { ApiError } from '../../api/http';
import type { ApplianceDto, SaveStateDto } from '../../api/types';

vi.mock('../../api/appliances', () => ({
  getApplianceCatalog: vi.fn(),
  buyAppliance: vi.fn(),
}));

const getApplianceCatalogMock = vi.mocked(getApplianceCatalog);
const buyApplianceMock = vi.mocked(buyAppliance);

const CATALOG_FIXTURE: ApplianceDto[] = [
  { id: 'FRIDGE', price: 876, store: 'SOCKET_CITY', owned: false },
  { id: 'FREEZER', price: 513, store: 'SOCKET_CITY', owned: true },
  { id: 'ENCYCLOPEDIA', price: 475, store: 'Z_MART', owned: false },
];

function playerFixture(): SaveStateDto {
  return {
    id: 42,
    label: 'Save 42',
    round: 3,
    timeMinutes: 3600,
    timeDisplay: '72h',
    weekOver: false,
    cash: 2000,
    bank: 100,
    debt: 0,
    rentDue: false,
    foodWeeks: 0,
    ateFastFoodLastTurn: false,
    clothing: 1,
    job: { name: 'Unemployed', hourlyWage: null, location: null },
    degreesEarned: [],
    currentCourse: null,
    goals: {
      wealth: { current: 500, target: 5000, met: false },
      happiness: { current: 50, target: 100, met: false },
      education: { current: 0, target: 100, met: false },
      career: { current: 0, target: 10, met: false },
    },
    won: false,
    location: { id: 'SOCKET_CITY', name: 'Socket City', ringIndex: 5, row: 1, col: 0 },
  };
}

function renderPanel(onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <SocketCityPanel saveId={42} player={playerFixture()} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('SocketCityPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getApplianceCatalogMock.mockResolvedValue(CATALOG_FIXTURE);
  });

  it('renders only Socket City items, filtering out Z-Mart stock', async () => {
    renderPanel();

    expect(await screen.findByText('Refrigerator')).toBeInTheDocument();
    expect(screen.getByText('Freezer')).toBeInTheDocument();
    expect(screen.queryByText(/ENCYCLOPEDIA/i)).not.toBeInTheDocument();
  });

  it('shows Owned on an owned item', async () => {
    renderPanel();

    const freezerRow = (await screen.findByText('Freezer')).closest('li') as HTMLElement;
    expect(within(freezerRow).getByText('Owned')).toBeInTheDocument();
  });

  it('buys successfully and updates the save-query cache', async () => {
    buyApplianceMock.mockResolvedValue({
      item: 'FRIDGE',
      price: 876,
      state: { ...playerFixture(), cash: 1124 },
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel();

    const fridgeRow = (await screen.findByText('Refrigerator')).closest('li') as HTMLElement;
    await user.click(within(fridgeRow).getByRole('button', { name: 'Buy' }));

    expect(buyApplianceMock).toHaveBeenCalledWith(42, 'FRIDGE');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Bought Refrigerator (R876)'));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 1124 });
  });

  it('renders the problem detail inline on an ApiError and invalidates the save query', async () => {
    buyApplianceMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at Socket City.',
      }),
    );
    const user = userEvent.setup();
    const { invalidateSpy } = renderPanel();

    const fridgeRow = (await screen.findByText('Refrigerator')).closest('li') as HTMLElement;
    await user.click(within(fridgeRow).getByRole('button', { name: 'Buy' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at Socket City.');
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
```

- [ ] **Step 7: Run the frontend suite**

Run: `cd frontend; npm test; npm run lint; npm run typecheck; npm run format:check`
Expected: all green.

- [ ] **Step 8: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: minimal Socket City panel + HUD fresh/fast food status (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add frontend/src/api/appliances.ts frontend/src/api/types.ts frontend/src/game/panels/SocketCityPanel.tsx frontend/src/game/panels/SocketCityPanel.test.tsx frontend/src/game/panels/registry.ts frontend/src/game/Hud.tsx frontend/src/game/panels/MonolithBurgersPanel.tsx
git commit -F commit.txt
```

### Task 12: V10 IT, full verify, live check, CV highlights, push, PR

**Files:**
- Modify: whichever `amiss-api` Testcontainers IT round-trips a `SaveState` mutation (grep `rg -l "SaveState" amiss-api/src/test/java/amiss/api/persistence/jpa`)
- Modify: `tasks/cv-highlights.md`

- [ ] **Step 1: Extend the adapter IT**

In the test that round-trips a `SaveState` mutation through `JpaSaveRepository.update`/`find`, additionally set and assert:

```java
        state.setAteFastFoodLastTurn(true);
        state.grantAppliance(ApplianceItem.FRIDGE);
        // ... existing update + reload ...
        assertTrue(reloaded.ateFastFoodLastTurn());
        assertEquals(Set.of(ApplianceItem.FRIDGE), reloaded.ownedAppliances());
```

- [ ] **Step 2: Full verify**

Run: `.\mvnw -B clean verify` (Docker running)
Expected: BUILD SUCCESS.

- [ ] **Step 3: Live check**

`.\mvnw -B install -DskipTests; .\mvnw -f amiss-api spring-boot:run` + `cd frontend; npm run dev`. Buy a Fridge at Socket City; buy groceries at Black's Market and confirm the HUD's food-weeks bar rises and caps at 6; buy a Freezer and confirm the cap becomes 12. Without a Fridge on a fresh save, buy any grocery pack and confirm it always reads exactly 1 week, and confirm the next End Week either shows nothing extra (missed the 50% roll) or the Doctor Visit line (hit it) with the food reset to 0. Stop the API via the port-8080 owner pid when done.

- [ ] **Step 4: CV highlights entry**

Add an entry to `tasks/cv-highlights.md` covering: wiki-exact fresh/fast food split, Fridge/Freezer capacity, minimal appliance-ownership plumbing reused by PR 5.

- [ ] **Step 5: Push + PR**

```powershell
git push -u origin feat/kan23-food-storage
gh pr create --base develop --title "feat: fresh/fast food split + Fridge/Freezer capacity (KAN-23)" --body-file <bodyfile>
```

Body: summary of V10, `ApplianceService`, the fridgeless-1-week rule, Doctor Visit's second trigger; note merge order after PR 1, and that Computer/Books extend the same `ApplianceItem` enum in PR 5 with no new migration.

---

# PR 3 — `feat/kan23-clothes-wear` (V11, three independent clothing categories, hiring gate)

Branch: `git checkout -b feat/kan23-clothes-wear` off `feat/kan23-food-storage`.

### Task 13: V11 migration + `ClothingItem` rework + `SaveState`/`SaveEntity` three-category fields

**Files:**
- Create: `amiss-core/src/main/resources/db/migration/V11__clothing_categories.sql`
- Modify: `amiss-core/src/main/java/amiss/domain/model/ClothingItem.java`
- Modify: `amiss-core/src/main/java/amiss/domain/model/SaveState.java`
- Modify: `amiss-api/src/main/java/amiss/api/persistence/jpa/SaveEntity.java`
- Modify: `amiss-api/src/main/java/amiss/api/persistence/jpa/JpaSaveRepository.java`
- Modify: every `new SaveState(` call site (grep in Step 5)

**Interfaces:**
- Produces: `ClothingItem` constants renamed `CASUAL/DRESS/BUSINESS` with `price()`, `level()` (1/2/3), `weeks()`, `happinessPerPurchase()`. `SaveState.clothing()`/`setClothing(int)` **removed**; replaced by `casualWeeks()`/`setCasualWeeks(int)`, `dressWeeks()`/`setDressWeeks(int)`, `businessWeeks()`/`setBusinessWeeks(int)`, and a new query `hasClothingLevel(int requiredLevel)`. The constructor's `clothing` slot becomes three slots in the same position. Task 14/15/16 depend on all of this.

- [ ] **Step 1: Write the migration**

```sql
-- V11: three independent clothing-category tracks (KAN-23, PR 3), replacing
-- the single non-decaying `clothing` level. Wiki-exact: Casual/Dress/Business
-- each count down in weeks and decay independently; a purchase adds to its
-- category rather than overwriting it. New-game seed matches the wiki
-- (6/0/0); existing saves get the same seed since there is no way to recover
-- which category an old flat level actually represented in weeks.
ALTER TABLE tblsave
    ADD COLUMN clothing_casual_weeks   INT NOT NULL DEFAULT 6 AFTER eat,
    ADD COLUMN clothing_dress_weeks    INT NOT NULL DEFAULT 0 AFTER clothing_casual_weeks,
    ADD COLUMN clothing_business_weeks INT NOT NULL DEFAULT 0 AFTER clothing_dress_weeks;

ALTER TABLE tblsave DROP COLUMN clothing;
```

- [ ] **Step 2: Rewrite `ClothingItem`**

```java
package amiss.domain.model;

/**
 * QT Clothing's stock (KAN-23, wiki-exact prices/durability). Three independent
 * categories — Casual, Dress, Business — each with its own weeks-remaining counter on
 * {@code SaveState}; buying adds to the category rather than replacing it. Z-Mart's
 * cheaper, shorter-lived line and its lack of a happiness bonus are KAN-58.
 */
public enum ClothingItem {
    CASUAL("Casual Clothes", 73, 1, 11, 0),
    DRESS("Dress Clothes", 125, 2, 13, 1),
    BUSINESS("Business Suit", 295, 3, 13, 2);

    private final String displayName;
    private final int price;
    private final int level;
    private final int weeks;
    private final int happinessPerPurchase;

    ClothingItem(String displayName, int price, int level, int weeks, int happinessPerPurchase) {
        this.displayName = displayName;
        this.price = price;
        this.level = level;
        this.weeks = weeks;
        this.happinessPerPurchase = happinessPerPurchase;
    }

    public String displayName() {
        return displayName;
    }

    public int price() {
        return price;
    }

    /** Ordinal matching {@code JobSpec.reqClothing}: 1 = Casual, 2 = Dress, 3 = Business. */
    public int level() {
        return level;
    }

    public int weeks() {
        return weeks;
    }

    /** Happiness on every purchase (wiki: QT only; Casual grants none). */
    public int happinessPerPurchase() {
        return happinessPerPurchase;
    }
}
```

- [ ] **Step 3: Replace `clothing` with three fields on `SaveState`**

Remove `private int clothing;`. Add in its place (same position, right after `eat`):

```java
    private int casualWeeks;
    private int dressWeeks;
    private int businessWeeks;
```

Update the constructor signature — the single `int clothing` parameter becomes three, in the same position:

```java
    public SaveState(long id, String owner, String label, int xpos, int ypos, int timeMinutes,
            int round, int cash, int bank, int debt, int rent, int eat,
            int casualWeeks, int dressWeeks, int businessWeeks,
            Integer jobId, int happiness, int experience, int dependability,
            Integer currentCourseId, int eduprog,
            int goalWealth, int goalHappiness, int goalEducation, int goalCareer, boolean won,
            byte economyIndex, short economyReading, Integer wage,
            boolean ateFastFoodLastTurn, Set<ApplianceItem> ownedAppliances) {
        // ... unchanged assignments up to eat ...
        this.eat = eat;
        this.casualWeeks = casualWeeks;
        this.dressWeeks = dressWeeks;
        this.businessWeeks = businessWeeks;
        this.jobId = jobId;
        // ... every remaining assignment unchanged ...
    }
```

Remove `clothing()`/`setClothing(int)`. Add in their place:

```java
    public int casualWeeks() {
        return casualWeeks;
    }

    public void setCasualWeeks(int casualWeeks) {
        this.casualWeeks = casualWeeks;
    }

    public int dressWeeks() {
        return dressWeeks;
    }

    public void setDressWeeks(int dressWeeks) {
        this.dressWeeks = dressWeeks;
    }

    public int businessWeeks() {
        return businessWeeks;
    }

    public void setBusinessWeeks(int businessWeeks) {
        this.businessWeeks = businessWeeks;
    }

    /**
     * Whether this save has at least one week of clothing left in {@code requiredLevel}
     * (1=Casual, 2=Dress, 3=Business) <strong>or a higher category</strong> — a Business
     * Suit satisfies a Casual-requiring job, but not vice-versa. {@code 0} (no requirement)
     * always passes.
     */
    public boolean hasClothingLevel(int requiredLevel) {
        return switch (requiredLevel) {
            case 1 -> casualWeeks > 0 || dressWeeks > 0 || businessWeeks > 0;
            case 2 -> dressWeeks > 0 || businessWeeks > 0;
            case 3 -> businessWeeks > 0;
            default -> true;
        };
    }
```

- [ ] **Step 4: Mirror the change on `SaveEntity`**

Remove the `clothing` field/getter/setter. Add:

```java
    @Column(name = "clothing_casual_weeks", nullable = false)
    private int casualWeeks;

    @Column(name = "clothing_dress_weeks", nullable = false)
    private int dressWeeks;

    @Column(name = "clothing_business_weeks", nullable = false)
    private int businessWeeks;
```

with matching getters/setters (`getCasualWeeks`/`setCasualWeeks`, etc.). In the "new save" constructor, replace `this.clothing = 1;` with `this.casualWeeks = 6;` (dress/business already default to 0).

- [ ] **Step 5: Fix `JpaSaveRepository` and every broken constructor call site**

`JpaSaveRepository.update` — replace `entity.setClothing(state.clothing());` with:

```java
            entity.setCasualWeeks(state.casualWeeks());
            entity.setDressWeeks(state.dressWeeks());
            entity.setBusinessWeeks(state.businessWeeks());
```

`JpaSaveRepository.toDomain` — replace `e.getClothing()` with `e.getCasualWeeks(), e.getDressWeeks(), e.getBusinessWeeks()` in the matching constructor position.

Run: `.\mvnw -B -pl amiss-core -pl amiss-api compile` and `rg -n "new SaveState\(|\.clothing\(\)|setClothing\(" --glob "*.java"` to find every remaining break (service code, other tests). `TestSaves.newSave()` becomes:

```java
    static SaveState newSave() {
        return new SaveState(SAVE_ID, "tester", "Save 1", 0, 0, 3600, 1, 100, 0, 0, 1, 0,
                6, 0, 0,
                null, 50, 10, 20, null, 0, 50, 50, 50, 50, false, (byte) 0, (short) 0, null,
                false, Set.of());
    }
```

- [ ] **Step 6: Run the whole unit suite**

Run: `.\mvnw -B clean package`
Expected: some pre-existing tests that asserted `save.clothing()` or constructed `ClothingItem.FORMAL`/`ClothingItem.SUIT` will fail to compile or assert wrong values — fix each as you find it (rename `FORMAL`→`DRESS`, `SUIT`→`BUSINESS`; replace any `clothing()` assertion with the matching `casualWeeks()`/`dressWeeks()`/`businessWeeks()`). This task's own scope is the compile-and-fix pass; Task 14 is where `ShopServiceTest`'s clothes-purchase behavior itself gets rewritten.

- [ ] **Step 7: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: V11 three independent clothing categories (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/resources/db/migration/V11__clothing_categories.sql amiss-core/src/main/java/amiss/domain/model/ClothingItem.java amiss-core/src/main/java/amiss/domain/model/SaveState.java amiss-api/src/main/java/amiss/api/persistence/jpa/SaveEntity.java amiss-api/src/main/java/amiss/api/persistence/jpa/JpaSaveRepository.java
git add -u
git commit -F commit.txt
```

### Task 14: `ShopService.buyClothes` adds to a category instead of overwriting (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/ShopService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/ShopServiceTest.java`

**Interfaces:**
- Produces: `buyClothes` adds `item.weeks()` to the matching category and applies `item.happinessPerPurchase()` on every purchase.

- [ ] **Step 1: Write the failing tests**

```java
    @Test
    void buyingCasualClothesAddsWeeksAndGrantsNoHappiness() {
        SaveState save = TestSaves.newSave();   // casualWeeks 6, cash 100
        int happinessBefore = save.happiness();

        service().buyClothes(save, ClothingItem.CASUAL);

        assertEquals(6 + ClothingItem.CASUAL.weeks(), save.casualWeeks());
        assertEquals(happinessBefore, save.happiness());
    }

    @Test
    void buyingDressClothesAddsToItsOwnCategoryAndGrantsHappiness() {
        SaveState save = TestSaves.newSave();
        save.setCash(300); // fixture cash 100 < DRESS price 125 — would hit INSUFFICIENT_CASH
        int happinessBefore = save.happiness();

        service().buyClothes(save, ClothingItem.DRESS);

        assertEquals(6, save.casualWeeks());        // untouched — separate category
        assertEquals(ClothingItem.DRESS.weeks(), save.dressWeeks());
        assertEquals(happinessBefore + 1, save.happiness());
    }

    @Test
    void multiplePurchasesOfTheSameCategoryStack() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);

        service().buyClothes(save, ClothingItem.BUSINESS);
        service().buyClothes(save, ClothingItem.BUSINESS);

        assertEquals(2 * ClothingItem.BUSINESS.weeks(), save.businessWeeks());
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=ShopServiceTest"`
Expected: failures against the old overwrite behavior (fix or delete any pre-existing test asserting `save.clothing()` after a purchase — Task 13 should already have flagged these).

- [ ] **Step 3: Implement**

```java
    /** Buys {@code item} at QT Clothing. */
    public PurchaseOutcome buyClothes(SaveState save, ClothingItem item) {
        int price = economy.price(item.price(), save);
        if (save.weekOver()) {
            return new PurchaseOutcome(PurchaseOutcome.Status.WEEK_OVER, save.timeMinutes(), save.cash(), price);
        }

        int cash = save.cash();
        if (cash < price) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, save.timeMinutes(), cash, price);
        }

        int time = save.timeMinutes();
        int remaining = time - costs.shopMinutes();
        if (remaining < 0) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_TIME, time, cash, price);
        }

        save.setTimeMinutes(remaining);
        save.setCash(cash - price);
        addClothingWeeks(save, item);
        save.addHappiness(item.happinessPerPurchase());
        saves.update(save);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, remaining, save.cash(), price);
    }

    private static void addClothingWeeks(SaveState save, ClothingItem item) {
        switch (item) {
            case CASUAL -> save.setCasualWeeks(save.casualWeeks() + item.weeks());
            case DRESS -> save.setDressWeeks(save.dressWeeks() + item.weeks());
            case BUSINESS -> save.setBusinessWeeks(save.businessWeeks() + item.weeks());
        }
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=ShopServiceTest"`
Expected: all PASS.

- [ ] **Step 5: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: clothing purchases stack per category with QT happiness bonus (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/ShopService.java amiss-core/src/test/java/amiss/application/service/save/ShopServiceTest.java
git commit -F commit.txt
```

### Task 15: Weekly clothing decay in the rollover (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void clothingCategoriesDecayIndependentlyAndFloorAtZero() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setCasualWeeks(1);
        save.setDressWeeks(0);
        save.setBusinessWeeks(5);

        service().endWeek(save);

        assertEquals(0, save.casualWeeks());
        assertEquals(0, save.dressWeeks());   // floors, does not go negative
        assertEquals(4, save.businessWeeks());
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=WeekRolloverServiceTest"`
Expected: FAIL (no decay wired yet).

- [ ] **Step 3: Implement**

Add right after the dependability decay line, before the Doctor Visit resolution:

```java
        save.setCasualWeeks(Math.max(0, save.casualWeeks() - 1));
        save.setDressWeeks(Math.max(0, save.dressWeeks() - 1));
        save.setBusinessWeeks(Math.max(0, save.businessWeeks() - 1));
```

- [ ] **Step 4: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=WeekRolloverServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: weekly clothing decay per category (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java
git commit -F commit.txt
```

### Task 16: Wire the dormant clothing-requirement gate into `HiringService` (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/HireOutcome.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/HiringService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/HiringServiceTest.java`

**Interfaces:**
- Produces: `HireOutcome.Reason.NOT_ENOUGH_CLOTHING` (new enum value).

- [ ] **Step 1: Write the failing test** (uses `TestSaves.BROKER`, `reqClothing = 3` — Business — already defined but never exercised for clothing per the earlier research pass)

```java
    @Test
    void insufficientClothingIsNamedAlongsideOtherShortfalls() {
        when(jobs.byId(TestSaves.BROKER.id())).thenReturn(Optional.of(TestSaves.BROKER));
        when(jobs.requiredDegrees(TestSaves.BROKER.id())).thenReturn(Set.of());
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = TestSaves.newSave();   // casualWeeks 6, dressWeeks 0, businessWeeks 0
        save.setExperience(70);
        save.setDependability(70);

        HireOutcome outcome = service.apply(save, TestSaves.BROKER.id());

        assertEquals(List.of(HireOutcome.Reason.NOT_ENOUGH_CLOTHING), outcome.reasons());
    }

    @Test
    void businessClothingSatisfiesACasualRequirement() {
        // Not COOK: Cook's alwaysHired() bypasses every check, including clothing, so it
        // can't prove the gate. CLERK (Z-Mart, reqClothing 1) does not bypass anything.
        when(jobs.byId(TestSaves.CLERK.id())).thenReturn(Optional.of(TestSaves.CLERK));
        when(jobs.requiredDegrees(TestSaves.CLERK.id())).thenReturn(Set.of());
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = TestSaves.newSave();
        save.setExperience(10);        // meets CLERK's reqExperience
        save.setDependability(10);     // meets CLERK's reqDependability
        save.setCasualWeeks(0);
        save.setBusinessWeeks(13);

        HireOutcome outcome = serviceAlwaysLucky().apply(save, TestSaves.CLERK.id());

        assertEquals(HireOutcome.Status.HIRED, outcome.status());
    }
```

(Read the existing test file first for its exact `serviceAlwaysLucky()`/luck-stubbing helper name and mirror it — do not guess.)

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=HiringServiceTest"`
Expected: COMPILATION ERROR — `HireOutcome.Reason.NOT_ENOUGH_CLOTHING` does not exist.

- [ ] **Step 3: Implement**

`HireOutcome` — add the enum value:

```java
    public enum Reason {
        NOT_ENOUGH_EDUCATION, NOT_ENOUGH_EXPERIENCE, POOR_WORK_HISTORY, NO_OPENINGS, NOT_ENOUGH_CLOTHING
    }
```

`HiringService.apply` — add the check right after the dependability branch, before the `if (!reasons.isEmpty())` guard:

```java
        if (save.dependability() < job.effectiveReqDependability()) {
            reasons.add(save.round() <= POOR_HISTORY_SUPPRESSED_UNTIL_ROUND
                    ? HireOutcome.Reason.NO_OPENINGS
                    : HireOutcome.Reason.POOR_WORK_HISTORY);
        }
        if (!save.hasClothingLevel(job.reqClothing())) {
            reasons.add(HireOutcome.Reason.NOT_ENOUGH_CLOTHING);
        }
        if (!reasons.isEmpty()) {
            return reject(save, job, reasons, charged);
        }
```

Update the class javadoc's stated check order to "education → experience → dependability → clothing → luck".

- [ ] **Step 4: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=HiringServiceTest"`
Expected: PASS. Then the full core suite: `.\mvnw -B -pl amiss-core test`.

- [ ] **Step 5: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: wire the dormant clothing-requirement hiring gate (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/HireOutcome.java amiss-core/src/main/java/amiss/application/service/save/HiringService.java amiss-core/src/test/java/amiss/application/service/save/HiringServiceTest.java
git commit -F commit.txt
```

### Task 17: DTOs carry the three categories; `FoodController` updated

**Files:**
- Modify: `amiss-api/src/main/java/amiss/api/web/dto/SaveStateDto.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/dto/ClothingItemDto.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/dto/ClothesResponse.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/PlayerStateAssembler.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/FoodController.java`
- Modify: whichever `amiss-api` test covers these (grep `rg -l "ClothesResponse(\|ClothingItemDto(\|SaveStateDto(" amiss-api/src/test`)

- [ ] **Step 1: Update `SaveStateDto`** — replace `int clothing` with three fields, in the same position:

```java
        int foodWeeks,
        boolean ateFastFoodLastTurn,
        int clothingCasualWeeks,
        int clothingDressWeeks,
        int clothingBusinessWeeks,
        JobDto job,
```

- [ ] **Step 2: Update `ClothingItemDto`**

```java
package amiss.api.web.dto;

/** One QT Clothing stock item on the wire ({@code GET /api/clothes}, KAN-23). */
public record ClothingItemDto(String id, String name, int price, int level, int weeks) {
}
```

- [ ] **Step 3: Simplify `ClothesResponse`** (drop `clothingLevel` — it no longer maps to one number):

```java
package amiss.api.web.dto;

/** A successful clothing purchase. */
public record ClothesResponse(String item, int price, SaveStateDto state) {
}
```

- [ ] **Step 4: Update `PlayerStateAssembler.assemble`**

Replace `save.clothing(),` with:

```java
                save.casualWeeks(),
                save.dressWeeks(),
                save.businessWeeks(),
```

- [ ] **Step 5: Update `FoodController`**

```java
    @GetMapping("/api/saves/{saveId}/clothes")
    public List<ClothingItemDto> clothesCatalog(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<ClothingItemDto> items = new ArrayList<>(ClothingItem.values().length);
        for (ClothingItem item : ClothingItem.values()) {
            items.add(new ClothingItemDto(item.name(), item.displayName(),
                    economy.price(item.price(), save), item.level(), item.weeks()));
        }
        return items;
    }
```

```java
    @PostMapping("/api/saves/{saveId}/clothes")
    public ClothesResponse clothes(@PathVariable long saveId, Authentication authentication,
            @RequestBody ClothesRequest request) {
        ClothingItem item = parseClothingItem(request.item());
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.QT_CLOTHING);

        PurchaseOutcome outcome = services.shop().buyClothes(save, item);
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new ClothesResponse(item.name(), outcome.pricePaid(), assembler.assemble(services, save));
        }
    }
```

- [ ] **Step 6: Fix the broken tests**

Update every `new SaveStateDto(`/`new ClothingItemDto(`/`new ClothesResponse(` construction for the new shapes; rename any `ClothingItem.FORMAL`/`SUIT` reference to `DRESS`/`BUSINESS`.

- [ ] **Step 7: Run the backend suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: wire the three clothing categories onto the save-state DTO (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-api/src/main/java/amiss/api/web/dto/SaveStateDto.java amiss-api/src/main/java/amiss/api/web/dto/ClothingItemDto.java amiss-api/src/main/java/amiss/api/web/dto/ClothesResponse.java amiss-api/src/main/java/amiss/api/web/PlayerStateAssembler.java amiss-api/src/main/java/amiss/api/web/FoodController.java
git add -u amiss-api/src/test
git commit -F commit.txt
```

### Task 18: Frontend — `QTClothingPanel` rework, HUD, types

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/game/panels/QTClothingPanel.tsx`
- Modify: `frontend/src/game/panels/QTClothingPanel.test.tsx`
- Modify: `frontend/src/game/Hud.tsx`

- [ ] **Step 1: Update the wire types**

In `types.ts`, replace `clothing: number;` on `SaveStateDto` with:

```ts
  clothingCasualWeeks: number;
  clothingDressWeeks: number;
  clothingBusinessWeeks: number;
```

Update `ClothingItemDto` to add `weeks: number;`. Update `ClothesResponse` to drop `clothingLevel: number;`.

- [ ] **Step 2: Rewrite `QTClothingPanel`** — no more "buying only ever upgrades"; every row is always buyable and shows the category's weeks-remaining

```tsx
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyClothes, getClothesCatalog } from '../../api/clothes';
import { errorMessage } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { ClothesResponse, SaveStateDto } from '../../api/types';
import type { PanelProps } from './types';

function weeksFor(player: SaveStateDto, itemId: string): number {
  switch (itemId) {
    case 'CASUAL':
      return player.clothingCasualWeeks;
    case 'DRESS':
      return player.clothingDressWeeks;
    case 'BUSINESS':
      return player.clothingBusinessWeeks;
    default:
      return 0;
  }
}

export function QTClothingPanel({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const clothesQuery = useQuery({
    queryKey: ['clothes', saveId],
    queryFn: () => getClothesCatalog(saveId),
    staleTime: Infinity,
  });

  const mutation = useMutation({
    mutationFn: (item: string) => buyClothes(saveId, item),
    onSuccess: (res: ClothesResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      const name = clothesQuery.data?.find((item) => item.id === res.item)?.name ?? res.item;
      onNotify(`Bought ${name} (R${res.price})`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  if (clothesQuery.isPending) {
    return <p>Loading stock…</p>;
  }
  if (clothesQuery.error !== null) {
    return <p role="alert">{clothesQuery.error.message}</p>;
  }

  const stock = clothesQuery.data;
  if (stock === undefined) {
    return null;
  }

  const rows: StoreRow[] = stock.map((item) => ({
    id: item.id,
    label: item.name,
    price: item.price,
    detail: `${weeksFor(player, item.id)} wk left, +${item.weeks} wk on purchase`,
    actionLabel: 'Buy',
  }));

  return (
    <StorePanel
      heading="QT Clothing"
      rows={rows}
      onAction={(id) => mutation.mutate(id)}
      pending={mutation.isPending}
      error={mutation.error !== null ? errorMessage(mutation.error) : null}
    />
  );
}
```

- [ ] **Step 3: Rewrite the panel test** — mirror the existing fixture/render-helper style; drop the "current level disables the row" tests (no longer true) and cover instead: every row always has an enabled Buy button regardless of current weeks, the detail text shows weeks-remaining, a successful purchase updates the cache and notifies without a level number, the error path still renders the alert and invalidates.

```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { QTClothingPanel } from './QTClothingPanel';
import { buyClothes, getClothesCatalog } from '../../api/clothes';
import { ApiError } from '../../api/http';
import type { ClothingItemDto, SaveStateDto } from '../../api/types';

vi.mock('../../api/clothes', () => ({
  getClothesCatalog: vi.fn(),
  buyClothes: vi.fn(),
}));

const getClothesCatalogMock = vi.mocked(getClothesCatalog);
const buyClothesMock = vi.mocked(buyClothes);

const CATALOG_FIXTURE: ClothingItemDto[] = [
  { id: 'CASUAL', name: 'Casual Clothes', price: 73, level: 1, weeks: 11 },
  { id: 'DRESS', name: 'Dress Clothes', price: 125, level: 2, weeks: 13 },
  { id: 'BUSINESS', name: 'Business Suit', price: 295, level: 3, weeks: 13 },
];

function playerFixture(overrides: Partial<SaveStateDto> = {}): SaveStateDto {
  return {
    id: 42,
    label: 'Save 42',
    round: 3,
    timeMinutes: 3600,
    timeDisplay: '72h',
    weekOver: false,
    cash: 500,
    bank: 100,
    debt: 0,
    rentDue: false,
    foodWeeks: 2,
    ateFastFoodLastTurn: false,
    clothingCasualWeeks: 3,
    clothingDressWeeks: 0,
    clothingBusinessWeeks: 0,
    job: { name: 'Unemployed', hourlyWage: null, location: null },
    degreesEarned: [],
    currentCourse: null,
    goals: {
      wealth: { current: 500, target: 5000, met: false },
      happiness: { current: 50, target: 100, met: false },
      education: { current: 0, target: 100, met: false },
      career: { current: 0, target: 10, met: false },
    },
    won: false,
    location: { id: 'QT_CLOTHING', name: 'QT Clothing', ringIndex: 4, row: 2, col: 4 },
    ...overrides,
  };
}

function renderPanel(player: SaveStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <QTClothingPanel saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, invalidateSpy, onNotify };
}

describe('QTClothingPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getClothesCatalogMock.mockResolvedValue(CATALOG_FIXTURE);
  });

  it('shows weeks-remaining per category and keeps every Buy button enabled', async () => {
    renderPanel(playerFixture());

    const casualRow = (await screen.findByText('Casual Clothes')).closest('li') as HTMLElement;
    expect(within(casualRow).getByText('3 wk left, +11 wk on purchase')).toBeInTheDocument();
    expect(within(casualRow).getByRole('button', { name: 'Buy' })).not.toBeDisabled();

    const dressRow = screen.getByText('Dress Clothes').closest('li') as HTMLElement;
    expect(within(dressRow).getByText('0 wk left, +13 wk on purchase')).toBeInTheDocument();
    expect(within(dressRow).getByRole('button', { name: 'Buy' })).not.toBeDisabled();
  });

  it('buys clothes successfully and notifies without a level number', async () => {
    buyClothesMock.mockResolvedValue({
      item: 'DRESS',
      price: 125,
      state: playerFixture({ cash: 375, clothingDressWeeks: 13 }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture());

    const dressRow = (await screen.findByText('Dress Clothes')).closest('li') as HTMLElement;
    await user.click(within(dressRow).getByRole('button', { name: 'Buy' }));

    expect(buyClothesMock).toHaveBeenCalledWith(42, 'DRESS');
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Bought Dress Clothes (R125)'));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ cash: 375 });
  });

  it('renders the problem detail inline on an ApiError and invalidates the player query', async () => {
    buyClothesMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at QT Clothing.',
      }),
    );
    const user = userEvent.setup();
    const { onNotify, invalidateSpy } = renderPanel(playerFixture());

    const suitRow = (await screen.findByText('Business Suit')).closest('li') as HTMLElement;
    await user.click(within(suitRow).getByRole('button', { name: 'Buy' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at QT Clothing.');
    expect(onNotify).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 42] });
  });
});
```

- [ ] **Step 4: Update `Hud.tsx`**

Replace the single Clothing stat row:

```tsx
        <div className="hud-stat">
          <dt>Clothing</dt>
          <dd>
            Casual {player.clothingCasualWeeks}wk / Dress {player.clothingDressWeeks}wk /
            Business {player.clothingBusinessWeeks}wk
          </dd>
        </div>
```

- [ ] **Step 5: Run the frontend suite**

Run: `cd frontend; npm test; npm run lint; npm run typecheck; npm run format:check`
Expected: all green.

- [ ] **Step 6: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: QT Clothing supports stackable per-category purchases (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add frontend/src/api/types.ts frontend/src/game/panels/QTClothingPanel.tsx frontend/src/game/panels/QTClothingPanel.test.tsx frontend/src/game/Hud.tsx
git commit -F commit.txt
```

### Task 19: V11 IT, full verify, live check, CV highlights, push, PR

**Files:**
- Modify: the same `amiss-api` Testcontainers IT touched in PR 2's Task 12
- Modify: `tasks/cv-highlights.md`

- [ ] **Step 1: Extend the adapter IT**

```java
        state.setCasualWeeks(3);
        state.setDressWeeks(7);
        state.setBusinessWeeks(0);
        // ... existing update + reload ...
        assertEquals(3, reloaded.casualWeeks());
        assertEquals(7, reloaded.dressWeeks());
        assertEquals(0, reloaded.businessWeeks());
```

- [ ] **Step 2: Full verify**

Run: `.\mvnw -B clean verify` (Docker running)
Expected: BUILD SUCCESS.

- [ ] **Step 3: Live check**

Buy Casual, Dress, and Business items at QT Clothing and confirm each category's weeks-remaining rises independently in the HUD; let a few weeks pass and confirm each decays by 1/week; try applying to a job requiring Business clothing (Broker) with Casual only owned and confirm the application is rejected citing insufficient clothing.

- [ ] **Step 4: CV highlights entry**

Add an entry to `tasks/cv-highlights.md` covering: wiki-exact three-category clothing model, wiring the dormant hiring clothing-gate.

- [ ] **Step 5: Push + PR**

```powershell
git push -u origin feat/kan23-clothes-wear
gh pr create --base develop --title "feat: three-category clothing wear + hiring gate (KAN-23)" --body-file <bodyfile>
```

Body: summary of V11, the category model, the `HiringService` gate; note merge order after PR 1→2.

---

# PR 4 — `feat/kan23-relaxation` (V12, Relax action, Doctor Visit's 3rd trigger)

Branch: `git checkout -b feat/kan23-relaxation` off `feat/kan23-clothes-wear`.

### Task 20: V12 migration + `relaxation`/`relaxedThisTurn` on `SaveState`/`SaveEntity`

**Files:**
- Create: `amiss-core/src/main/resources/db/migration/V12__relaxation.sql`
- Modify: `amiss-core/src/main/java/amiss/domain/model/SaveState.java`
- Modify: `amiss-api/src/main/java/amiss/api/persistence/jpa/SaveEntity.java`
- Modify: `amiss-api/src/main/java/amiss/api/persistence/jpa/JpaSaveRepository.java`
- Modify: every `new SaveState(` call site (grep in Step 4)

**Interfaces:**
- Produces: `SaveState.relaxation()`/`setRelaxation(int)`/`addRelaxation(int)` (clamped 10..50); `relaxedThisTurn()`/`setRelaxedThisTurn(boolean)`. Constructor gains two trailing params. Task 21/22 depend on these.

- [ ] **Step 1: Write the migration**

```sql
-- V12: Relaxation stat (KAN-23, PR 4). Starts at 10 (the wiki floor), rises to
-- a max of 50 via the Relax action, decays -1/turn (never below 10).
-- relaxed_this_turn tracks whether this turn's first-Relax happiness bonus
-- has already been paid out (wiki: only the first Relax each turn grants +2
-- happiness); reset at every rollover.
ALTER TABLE tblsave ADD COLUMN relaxation         INT     NOT NULL DEFAULT 10 AFTER wage;
ALTER TABLE tblsave ADD COLUMN relaxed_this_turn  TINYINT NOT NULL DEFAULT 0  AFTER relaxation;
```

- [ ] **Step 2: Add the fields to `SaveState`**

Add after `ownedAppliances`:

```java
    private int relaxation;
    private boolean relaxedThisTurn;
```

Append two trailing constructor params and assign them:

```java
    public SaveState(long id, String owner, String label, int xpos, int ypos, int timeMinutes,
            int round, int cash, int bank, int debt, int rent, int eat,
            int casualWeeks, int dressWeeks, int businessWeeks,
            Integer jobId, int happiness, int experience, int dependability,
            Integer currentCourseId, int eduprog,
            int goalWealth, int goalHappiness, int goalEducation, int goalCareer, boolean won,
            byte economyIndex, short economyReading, Integer wage,
            boolean ateFastFoodLastTurn, Set<ApplianceItem> ownedAppliances,
            int relaxation, boolean relaxedThisTurn) {
        // ... every existing assignment unchanged ...
        this.ownedAppliances = new HashSet<>(ownedAppliances);
        this.relaxation = relaxation;
        this.relaxedThisTurn = relaxedThisTurn;
    }
```

Add accessors after `grantAppliance`:

```java
    public int relaxation() {
        return relaxation;
    }

    public void setRelaxation(int relaxation) {
        this.relaxation = relaxation;
    }

    /** Applies {@code delta} clamped to the wiki's 10..50 band — used for both the Relax
     *  action's +3 gain and the weekly -1 decay. */
    public void addRelaxation(int delta) {
        this.relaxation = Math.max(10, Math.min(50, relaxation + delta));
    }

    public boolean relaxedThisTurn() {
        return relaxedThisTurn;
    }

    public void setRelaxedThisTurn(boolean relaxedThisTurn) {
        this.relaxedThisTurn = relaxedThisTurn;
    }
```

- [ ] **Step 3: Mirror on `SaveEntity`**

Add:

```java
    @Column(name = "relaxation", nullable = false)
    private int relaxation;

    /** 1 = this turn's first-Relax happiness bonus already paid. */
    @Column(name = "relaxed_this_turn", nullable = false)
    private int relaxedThisTurn;
```

with getters/setters (`getRelaxation`/`setRelaxation`, `isRelaxedThisTurn`/`setRelaxedThisTurn` following the `won`-style int↔boolean convention). In the "new save" constructor, add `this.relaxation = 10;` (matches the wiki floor; `relaxedThisTurn` stays 0, Java's default).

- [ ] **Step 4: Fix `JpaSaveRepository` and every broken constructor call site**

`update` — add:

```java
            entity.setRelaxation(state.relaxation());
            entity.setRelaxedThisTurn(state.relaxedThisTurn());
```

`toDomain` — append `e.getRelaxation(), e.isRelaxedThisTurn()`.

Run `rg -n "new SaveState\(" --glob "*.java"` and append `, 10, false` at every site. `TestSaves.newSave()` becomes:

```java
    static SaveState newSave() {
        return new SaveState(SAVE_ID, "tester", "Save 1", 0, 0, 3600, 1, 100, 0, 0, 1, 0,
                6, 0, 0,
                null, 50, 10, 20, null, 0, 50, 50, 50, 50, false, (byte) 0, (short) 0, null,
                false, Set.of(), 10, false);
    }
```

- [ ] **Step 5: Run the whole unit suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS, all green (no behaviour changed yet).

- [ ] **Step 6: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: V12 relaxation stat on SaveState (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/resources/db/migration/V12__relaxation.sql amiss-core/src/main/java/amiss/domain/model/SaveState.java amiss-api/src/main/java/amiss/api/persistence/jpa/SaveEntity.java amiss-api/src/main/java/amiss/api/persistence/jpa/JpaSaveRepository.java
git add -u
git commit -F commit.txt
```

### Task 21: `RelaxService` + `HomeController` (TDD)

**Files:**
- Create: `amiss-core/src/main/java/amiss/application/service/save/RelaxService.java`
- Create: `amiss-core/src/test/java/amiss/application/service/save/RelaxServiceTest.java`
- Create: `amiss-api/src/main/java/amiss/api/web/HomeController.java`
- Create: `amiss-api/src/main/java/amiss/api/web/dto/RelaxResponse.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/dto/SaveStateDto.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/PlayerStateAssembler.java`

**Interfaces:**
- Produces: `RelaxService(SaveRepository, ActionCosts)`, `RelaxOutcome relax(SaveState)`; `SaveGameServices.relax()` getter; `POST /api/saves/{saveId}/relax`; `SaveStateDto.relaxation()`.

- [ ] **Step 1: Write the failing service tests**

```java
package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelaxServiceTest {

    @Mock
    private SaveRepository saves;

    private RelaxService service() {
        return new RelaxService(saves, ActionCosts.defaults());
    }

    @Test
    void firstRelaxThisTurnGrantsTheStatAndHappinessBonus() {
        SaveState save = TestSaves.newSave();   // relaxation 10, happiness 50, time 3600

        RelaxService.RelaxOutcome outcome = service().relax(save);

        assertEquals(RelaxService.RelaxOutcome.Status.OK, outcome.status());
        assertEquals(13, save.relaxation());
        assertEquals(52, save.happiness());
        assertEquals(3600 - ActionCosts.defaults().relaxMinutes(), save.timeMinutes());
        verify(saves).update(save);
    }

    @Test
    void secondRelaxSameTurnRaisesTheStatButGrantsNoMoreHappiness() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(3600);
        service().relax(save);
        int happinessAfterFirst = save.happiness();

        service().relax(save);

        assertEquals(16, save.relaxation());
        assertEquals(happinessAfterFirst, save.happiness());
    }

    @Test
    void relaxationClampsAtFifty() {
        SaveState save = TestSaves.newSave();
        save.setRelaxation(49);

        service().relax(save);

        assertEquals(50, save.relaxation());
    }

    @Test
    void weekOverRejectsTheAction() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);

        RelaxService.RelaxOutcome outcome = service().relax(save);

        assertEquals(RelaxService.RelaxOutcome.Status.WEEK_OVER, outcome.status());
        assertEquals(10, save.relaxation());
    }

    @Test
    void insufficientTimeRejectsTheAction() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(100);   // less than the 360-minute relax cost

        RelaxService.RelaxOutcome outcome = service().relax(save);

        assertEquals(RelaxService.RelaxOutcome.Status.INSUFFICIENT_TIME, outcome.status());
        assertEquals(10, save.relaxation());
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=RelaxServiceTest"`
Expected: COMPILATION ERROR — `RelaxService` does not exist.

- [ ] **Step 3: Implement `RelaxService`**

```java
package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;

/**
 * The Relax action at Home (KAN-23), wiki-exact: 6h, +3 Relaxation (max 50), and +2
 * Happiness on the first Relax of a turn only — repeat Relaxes the same turn still
 * raise the stat but grant no further happiness.
 */
public class RelaxService {

    static final int RELAXATION_GAIN = 3;
    static final int FIRST_RELAX_HAPPINESS = 2;

    public record RelaxOutcome(Status status, int remainingMinutes, int relaxation) {
        public enum Status {
            OK, WEEK_OVER, INSUFFICIENT_TIME
        }
    }

    private final SaveRepository saves;
    private final ActionCosts costs;

    public RelaxService(SaveRepository saves, ActionCosts costs) {
        this.saves = saves;
        this.costs = costs;
    }

    public RelaxOutcome relax(SaveState save) {
        if (save.weekOver()) {
            return new RelaxOutcome(RelaxOutcome.Status.WEEK_OVER, save.timeMinutes(), save.relaxation());
        }
        int time = save.timeMinutes();
        int remaining = time - costs.relaxMinutes();
        if (remaining < 0) {
            return new RelaxOutcome(RelaxOutcome.Status.INSUFFICIENT_TIME, time, save.relaxation());
        }
        save.setTimeMinutes(remaining);
        save.addRelaxation(RELAXATION_GAIN);
        if (!save.relaxedThisTurn()) {
            save.addHappiness(FIRST_RELAX_HAPPINESS);
            save.setRelaxedThisTurn(true);
        }
        saves.update(save);
        return new RelaxOutcome(RelaxOutcome.Status.OK, remaining, save.relaxation());
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=RelaxServiceTest"`
Expected: 5 tests PASS.

- [ ] **Step 5: Wire into `SaveGameServices`**

```java
    private final RelaxService relax;
```

```java
        this.appliances = new ApplianceService(saves, economy);
        this.relax = new RelaxService(saves, costs);
```

```java
    public RelaxService relax() {
        return relax;
    }
```

- [ ] **Step 6: Add `relaxation` to `SaveStateDto`** — right after the three clothing fields:

```java
        int clothingBusinessWeeks,
        int relaxation,
        JobDto job,
```

Update `PlayerStateAssembler.assemble`: insert `save.relaxation(),` right after `save.businessWeeks(),`.

- [ ] **Step 7: Create `RelaxResponse` and `HomeController`**

```java
package amiss.api.web.dto;

/** A successful Relax action. */
public record RelaxResponse(int minutesCharged, int relaxation, SaveStateDto state) {
}
```

```java
package amiss.api.web;

import amiss.api.error.InsufficientTimeException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.RelaxResponse;
import amiss.application.config.ActionCosts;
import amiss.application.service.save.RelaxService;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Relax action at Home (KAN-23) — 6h for +3 Relaxation, +2 happiness on the first
 *  Relax of a turn only. */
@RestController
public class HomeController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;
    private final ActionCosts costs;

    public HomeController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler,
            ActionCosts costs) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
        this.costs = costs;
    }

    @PostMapping("/api/saves/{saveId}/relax")
    public RelaxResponse relax(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.LOW_COST_HOUSING);

        RelaxService.RelaxOutcome outcome = services.relax().relax(save);
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new RelaxResponse(costs.relaxMinutes(), outcome.relaxation(),
                        assembler.assemble(services, save));
        }
    }
}
```

- [ ] **Step 8: Fix every broken `new SaveStateDto(` construction and run the backend suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: RelaxService + Relax action at Home (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/RelaxService.java amiss-core/src/test/java/amiss/application/service/save/RelaxServiceTest.java amiss-api/src/main/java/amiss/api/web/HomeController.java amiss-api/src/main/java/amiss/api/web/dto/RelaxResponse.java amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java amiss-api/src/main/java/amiss/api/web/dto/SaveStateDto.java amiss-api/src/main/java/amiss/api/web/PlayerStateAssembler.java
git add -u amiss-api/src/test
git commit -F commit.txt
```

### Task 22: Weekly relaxation decay + Doctor Visit's third trigger (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/DoctorVisitService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/DoctorVisitServiceTest.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java`

**Interfaces:**
- Produces: `DoctorVisitService.resolve(SaveState, boolean starved, boolean spoiledFreshFood, boolean relaxationAtFloor)` (grew a third param).

- [ ] **Step 1: Update Doctor Visit tests + add the relaxation trigger tests**

Every existing `.resolve(save, x, y)` call becomes `.resolve(save, x, y, false)`. Add:

```java
    @Test
    void relaxationFloorRollCanMissAtTwentyPercent() {
        SaveState save = TestSaves.newSave();
        // 1-in-5 roll, value 2 = miss.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(2)).resolve(save, false, false, true);

        assertFalse(outcome.triggered());
    }

    @Test
    void relaxationFloorTriggerCostsTheSameAsTheOtherTwo() {
        SaveState save = TestSaves.newSave();
        // 1-in-5 roll, value 1 = hit; cost roll value 1 -> 30.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 1)).resolve(save, false, false, true);

        assertTrue(outcome.triggered());
        assertEquals(30, outcome.cashLost());
    }
```

- [ ] **Step 2: Run to verify failure, then implement**

```java
    private static final int RELAXATION_CHANCE = 5;   // 1-in-5 = 20%
```

```java
    public DoctorVisitOutcome resolve(SaveState save, boolean starved, boolean spoiledFreshFood,
            boolean relaxationAtFloor) {
        if (save.cash() <= 0) {
            return DoctorVisitOutcome.none();
        }
        boolean starvationHit = starved && roll1toN.applyAsInt(STARVATION_CHANCE) == 1;
        boolean spoilageHit = spoiledFreshFood && roll1toN.applyAsInt(SPOILAGE_CHANCE) == 1;
        boolean relaxationHit = relaxationAtFloor && roll1toN.applyAsInt(RELAXATION_CHANCE) == 1;
        boolean triggered = starvationHit || spoilageHit || relaxationHit;
        if (!triggered) {
            return DoctorVisitOutcome.none();
        }
        int cost = cost(save.cash());
        save.setCash(save.cash() - cost);
        save.spendUpTo(MINUTES_LOST);
        save.addHappiness(-HAPPINESS_LOST);
        return new DoctorVisitOutcome(true, MINUTES_LOST, HAPPINESS_LOST, cost);
    }
```

Run: `.\mvnw -B -pl amiss-core test "-Dtest=DoctorVisitServiceTest"` — expect PASS.

- [ ] **Step 3: Write the failing rollover test**

```java
    @Test
    void relaxationDecaysWeeklyResetsTheFirstRelaxFlagAndFloorFeedsDoctorVisit() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setAteFastFoodLastTurn(true);   // fed, with no Fridge involved either way:
        save.setEat(0);                      // isolates this test to the relaxation trigger only
        save.setRelaxation(11);
        save.setRelaxedThisTurn(true);
        save.setCash(100);

        // starved=false, spoiledFreshFood=false (fed via fast food, no fresh food to
        // spoil) -> neither consumes a roll. Doctor Visit rolls first: relaxation-floor
        // roll (1-in-5, value 1 = hit), cost roll (21-wide, value 1 -> 30); then the two
        // neutral economy drift rolls (3, 6).
        WeekRolloverService.RolloverResult result = service(rolls(1, 1, 3, 6)).endWeek(save);

        assertEquals(10, save.relaxation());          // 11 - 1, now at the floor
        assertFalse(save.relaxedThisTurn());          // reset for the new turn
        assertTrue(result.doctorVisit().triggered());  // floor feeds the 20% trigger
        assertEquals(70, save.cash());
    }

    @Test
    void relaxationAboveTheFloorNeverTriggersItsCondition() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setAteFastFoodLastTurn(true);
        save.setEat(0);
        save.setRelaxation(20);

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertEquals(19, save.relaxation());
        assertFalse(result.doctorVisit().triggered());
    }
```

- [ ] **Step 4: Run to verify failure, then implement**

Add a floor constant and the decay/reset/condition, placed after the clothing decay block, before the Doctor Visit call:

```java
    private static final int RELAXATION_FLOOR = 10;
```

```java
        save.setRelaxedThisTurn(false);
        save.addRelaxation(-1);
        boolean relaxationAtFloor = save.relaxation() == RELAXATION_FLOOR;

        DoctorVisitOutcome doctorVisitOutcome = doctorVisit.resolve(save, !fed, spoiledFreshFood, relaxationAtFloor);
```

(Replaces the old 3-arg `doctorVisit.resolve(save, !fed, spoiledFreshFood)` call from PR 2's Task 9.)

Note: TestSaves.newSave() defaults relaxation to the floor (10), so four pre-existing rollover tests with hand-scripted roll queues need a `setRelaxation(20)` grant to keep the relaxation condition roll-free (same fixture-grant pattern as PR 2's Fridge grants).

- [ ] **Step 5: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=WeekRolloverServiceTest"` then the full core suite `.\mvnw -B -pl amiss-core test`.
Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: weekly relaxation decay feeds Doctor Visit's 3rd trigger (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/DoctorVisitService.java amiss-core/src/test/java/amiss/application/service/save/DoctorVisitServiceTest.java amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java
git commit -F commit.txt
```

### Task 23: Frontend — `HomePanel` rebuild, HUD, `home.ts`

**Files:**
- Create: `frontend/src/api/home.ts`
- Modify: `frontend/src/game/panels/HomePanel.tsx`
- Modify: `frontend/src/game/panels/HomePanel.test.tsx` (create if it doesn't exist — grep first)
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/game/Hud.tsx`

- [ ] **Step 1: Add the wire type + api module**

In `types.ts`, add `relaxation: number;` to `SaveStateDto` right after `clothingBusinessWeeks: number;`. Add:

```ts
export interface RelaxResponse {
  minutesCharged: number;
  relaxation: number;
  state: SaveStateDto;
}
```

```ts
import { apiFetch } from './http';
import type { RelaxResponse } from './types';

export function relax(saveId: number): Promise<RelaxResponse> {
  return apiFetch<RelaxResponse>(`/saves/${saveId}/relax`, { method: 'POST' });
}
```

- [ ] **Step 2: Rebuild `HomePanel`**

```tsx
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { relax } from '../../api/home';
import { errorMessage } from '../../api/http';
import type { RelaxResponse } from '../../api/types';
import type { PanelProps } from './types';

export function HomePanel({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => relax(saveId),
    onSuccess: (res: RelaxResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      onNotify(`Relaxed — Relaxation now ${res.relaxation}`);
    },
    onError: (err: unknown) => {
      onNotify(errorMessage(err));
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  return (
    <div className="home-panel">
      <h2>Home</h2>
      <p>Food stored: {player.foodWeeks} wk</p>
      {player.rentDue && <p>Rent is due — the Rent Office expects R80 this round.</p>}
      <p>Relaxation: {player.relaxation} / 50</p>
      <button type="button" onClick={() => mutation.mutate()} disabled={mutation.isPending}>
        Relax
      </button>
      {mutation.error !== null && <p role="alert">{errorMessage(mutation.error)}</p>}
    </div>
  );
}
```

- [ ] **Step 3: Write the panel test**

```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HomePanel } from './HomePanel';
import { relax } from '../../api/home';
import { ApiError } from '../../api/http';
import type { SaveStateDto } from '../../api/types';

vi.mock('../../api/home', () => ({
  relax: vi.fn(),
}));

const relaxMock = vi.mocked(relax);

function playerFixture(overrides: Partial<SaveStateDto> = {}): SaveStateDto {
  return {
    id: 42,
    label: 'Save 42',
    round: 3,
    timeMinutes: 3600,
    timeDisplay: '72h',
    weekOver: false,
    cash: 500,
    bank: 100,
    debt: 0,
    rentDue: false,
    foodWeeks: 2,
    ateFastFoodLastTurn: false,
    clothingCasualWeeks: 6,
    clothingDressWeeks: 0,
    clothingBusinessWeeks: 0,
    relaxation: 10,
    job: { name: 'Unemployed', hourlyWage: null, location: null },
    degreesEarned: [],
    currentCourse: null,
    goals: {
      wealth: { current: 500, target: 5000, met: false },
      happiness: { current: 50, target: 100, met: false },
      education: { current: 0, target: 100, met: false },
      career: { current: 0, target: 10, met: false },
    },
    won: false,
    location: { id: 'LOW_COST_HOUSING', name: 'Low-Cost Housing', ringIndex: 0, row: 3, col: 2 },
    ...overrides,
  };
}

function renderPanel(player: SaveStateDto, onNotify = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <HomePanel saveId={42} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { ...utils, queryClient, onNotify };
}

describe('HomePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows the current relaxation stat', () => {
    renderPanel(playerFixture({ relaxation: 34 }));
    expect(screen.getByText('Relaxation: 34 / 50')).toBeInTheDocument();
  });

  it('relaxes successfully and updates the save-query cache', async () => {
    relaxMock.mockResolvedValue({
      minutesCharged: 360,
      relaxation: 13,
      state: playerFixture({ relaxation: 13 }),
    });
    const user = userEvent.setup();
    const { onNotify, queryClient } = renderPanel(playerFixture());

    await user.click(screen.getByRole('button', { name: 'Relax' }));

    expect(relaxMock).toHaveBeenCalledWith(42);
    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Relaxed — Relaxation now 13'));
    expect(queryClient.getQueryData(['save', 42])).toMatchObject({ relaxation: 13 });
  });

  it('renders the problem detail on an ApiError and invalidates the save query', async () => {
    relaxMock.mockRejectedValue(
      new ApiError(409, {
        type: 'urn:amiss:wrong-location',
        title: 'Conflict',
        status: 409,
        detail: 'Must be at Low-Cost Housing.',
      }),
    );
    const user = userEvent.setup();
    renderPanel(playerFixture());

    await user.click(screen.getByRole('button', { name: 'Relax' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Must be at Low-Cost Housing.');
  });
});
```

- [ ] **Step 4: Update `Hud.tsx`** — add a Relaxation stat row alongside the existing ones:

```tsx
        <div className="hud-stat">
          <dt>Relaxation</dt>
          <dd>{player.goals === undefined ? null : `${player.round >= 0 ? '' : ''}`}</dd>
        </div>
```

Replace that placeholder line with the real one (shown separately so the no-placeholder rule is unambiguous about what to actually type):

```tsx
        <div className="hud-stat">
          <dt>Relaxation</dt>
          <dd>{player.relaxation} / 50</dd>
        </div>
```

- [ ] **Step 5: Run the frontend suite**

Run: `cd frontend; npm test; npm run lint; npm run typecheck; npm run format:check`
Expected: all green.

- [ ] **Step 6: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: HomePanel Relax action + HUD relaxation stat (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add frontend/src/api/home.ts frontend/src/api/types.ts frontend/src/game/panels/HomePanel.tsx frontend/src/game/panels/HomePanel.test.tsx frontend/src/game/Hud.tsx
git commit -F commit.txt
```

### Task 24: V12 IT, full verify, live check, CV highlights, push, PR

**Files:**
- Modify: the same `amiss-api` Testcontainers IT touched in PR 2/3's equivalent tasks
- Modify: `tasks/cv-highlights.md`

- [ ] **Step 1: Extend the adapter IT**

```java
        state.setRelaxation(27);
        state.setRelaxedThisTurn(true);
        // ... existing update + reload ...
        assertEquals(27, reloaded.relaxation());
        assertTrue(reloaded.relaxedThisTurn());
```

- [ ] **Step 2: Full verify**

Run: `.\mvnw -B clean verify` (Docker running)
Expected: BUILD SUCCESS.

- [ ] **Step 3: Live check**

Click Relax at Home repeatedly within one turn and confirm the stat rises by 3 each time but happiness only rises once; let several weeks pass without relaxing and confirm the stat decays to (and stays at) 10; with a save deliberately held at 10, roll several End Weeks and confirm a Doctor Visit eventually fires from the relaxation condition. Stop the API via the port-8080 owner pid when done.

- [ ] **Step 4: CV highlights entry**

Add an entry to `tasks/cv-highlights.md` covering: wiki-exact Relaxation stat/action, Doctor Visit's third trigger, HomePanel's first real functionality since its KAN-40 placeholder.

- [ ] **Step 5: Push + PR**

```powershell
git push -u origin feat/kan23-relaxation
gh pr create --base develop --title "feat: Relaxation stat + Relax action (KAN-23)" --body-file <bodyfile>
```

Body: summary of V12, `RelaxService`, Doctor Visit's third trigger; note merge order after PR 1→2→3.

---

# PR 5 — `feat/kan23-extra-credit` (Computer/Books extend `ApplianceItem`, extra credit, Z-Mart)

Branch: `git checkout -b feat/kan23-extra-credit` off `feat/kan23-relaxation`.

### Task 25: `ApplianceItem` gains Computer + the three Books (no migration)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/domain/model/ApplianceItem.java`

**Interfaces:**
- Produces: `ApplianceItem.COMPUTER`, `.ENCYCLOPEDIA`, `.DICTIONARY`, `.ATLAS` — four new enum constants in the same `@ElementCollection` join table (no schema change: the table stores enum names as strings).

- [ ] **Step 1: Add the four constants**

```java
public enum ApplianceItem {
    FRIDGE(876, 1, Location.SOCKET_CITY),
    FREEZER(513, 2, Location.SOCKET_CITY),
    COMPUTER(1599, 3, Location.SOCKET_CITY),
    ENCYCLOPEDIA(475, 0, Location.Z_MART),
    DICTIONARY(70, 0, Location.Z_MART),
    ATLAS(55, 0, Location.Z_MART);

    // ... fields/constructor/accessors unchanged ...
}
```

Update the class javadoc — remove the "Computer and the three Books are added in a later PR" sentence (this is that PR).

- [ ] **Step 2: Run the whole unit suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS — `ApplianceController`'s catalog loop already iterates `ApplianceItem.values()`, so the new items appear automatically with zero controller changes.

- [ ] **Step 3: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: ApplianceItem gains Computer + the three Books (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/domain/model/ApplianceItem.java
git commit -F commit.txt
```

### Task 26: Extra credit reduces `CourseService`'s required study sessions (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/CourseService.java`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/CourseServiceTest.java`

**Interfaces:**
- Produces: `CourseService.study` grades against a per-save `studiesRequired(save)` (10, minus 1 for Computer, minus 1 more for all three Books, floor 8) instead of the flat `STUDIES_PER_DEGREE` constant. `STUDIES_PER_DEGREE` itself is unchanged (still the base) — only the comparison and the `GRADUATED` result's reported count use the adjusted value.

- [ ] **Step 1: Write the failing tests** (read `CourseServiceTest` first for its exact degree-fixture constant name, e.g. `JUNIOR_COLLEGE`, and mirror it)

```java
    @Test
    void ownersOfAComputerGraduateOneStudySessionEarly() {
        when(catalog.byId(1)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        SaveState save = TestSaves.newSave();
        save.setCurrentCourseId(1);
        save.setEduprog(8);
        save.grantAppliance(ApplianceItem.COMPUTER);

        CourseService.StudyResult result = service().study(save);

        assertEquals(CourseService.StudyResult.Status.GRADUATED, result.status());
        assertEquals(9, result.studiesDone());
    }

    @Test
    void ownersOfAllThreeBooksGraduateOneStudySessionEarly() {
        when(catalog.byId(1)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        SaveState save = TestSaves.newSave();
        save.setCurrentCourseId(1);
        save.setEduprog(8);
        save.grantAppliance(ApplianceItem.ENCYCLOPEDIA);
        save.grantAppliance(ApplianceItem.DICTIONARY);
        save.grantAppliance(ApplianceItem.ATLAS);

        CourseService.StudyResult result = service().study(save);

        assertEquals(CourseService.StudyResult.Status.GRADUATED, result.status());
    }

    @Test
    void owningOnlyTwoOfTheThreeBooksGrantsNoBonus() {
        when(catalog.byId(1)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        SaveState save = TestSaves.newSave();
        save.setCurrentCourseId(1);
        save.setEduprog(8);
        save.grantAppliance(ApplianceItem.ENCYCLOPEDIA);
        save.grantAppliance(ApplianceItem.DICTIONARY);

        CourseService.StudyResult result = service().study(save);

        assertEquals(CourseService.StudyResult.Status.OK, result.status());
        assertEquals(9, result.studiesDone());
    }

    @Test
    void computerAndAllThreeBooksTogetherFloorAtEight() {
        when(catalog.byId(1)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        SaveState save = TestSaves.newSave();
        save.setCurrentCourseId(1);
        save.setEduprog(7);
        save.grantAppliance(ApplianceItem.COMPUTER);
        save.grantAppliance(ApplianceItem.ENCYCLOPEDIA);
        save.grantAppliance(ApplianceItem.DICTIONARY);
        save.grantAppliance(ApplianceItem.ATLAS);

        CourseService.StudyResult result = service().study(save);

        assertEquals(CourseService.StudyResult.Status.GRADUATED, result.status());
        assertEquals(8, result.studiesDone());
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=CourseServiceTest"`
Expected: FAIL (still grades against the flat 10).

- [ ] **Step 3: Implement**

Add `import amiss.domain.model.ApplianceItem;`. Add the private helper and rewrite `study`'s graduation check:

```java
    /** 10, minus 1 for owning a Computer, minus 1 more for owning all three Books, floor 8. */
    private static int studiesRequired(SaveState save) {
        int required = STUDIES_PER_DEGREE;
        if (save.owns(ApplianceItem.COMPUTER)) {
            required--;
        }
        if (save.owns(ApplianceItem.ENCYCLOPEDIA) && save.owns(ApplianceItem.DICTIONARY)
                && save.owns(ApplianceItem.ATLAS)) {
            required--;
        }
        return required;
    }
```

```java
    public StudyResult study(SaveState save) {
        if (save.currentCourseId() == null) {
            return new StudyResult(StudyResult.Status.NOT_ENROLLED, 0, save.timeMinutes(), null);
        }
        if (save.weekOver()) {
            return new StudyResult(StudyResult.Status.WEEK_OVER, save.eduprog(), 0, null);
        }
        save.spendUpTo(costs.studyMinutes());
        int done = save.eduprog() + 1;
        int required = studiesRequired(save);
        if (done >= required) {
            int degreeId = save.currentCourseId();
            String name = catalog.byId(degreeId).map(DegreeSpec::name).orElse("degree");
            degrees.award(save.id(), degreeId);
            save.setCurrentCourseId(null);
            save.setEduprog(0);
            save.setDependability(save.dependability() + GRADUATION_DEPENDABILITY_BONUS);
            saves.update(save);
            return new StudyResult(StudyResult.Status.GRADUATED, required, save.timeMinutes(), name);
        }
        save.setEduprog(done);
        saves.update(save);
        return new StudyResult(StudyResult.Status.OK, done, save.timeMinutes(), null);
    }
```

Update the class javadoc's "ten study sessions graduate it" sentence to note the Computer/Books reduction, floor 8.

- [ ] **Step 4: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=CourseServiceTest"`
Expected: PASS. Then the full core suite: `.\mvnw -B -pl amiss-core test`.

- [ ] **Step 5: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: extra credit reduces required study sessions, floor 8 (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add amiss-core/src/main/java/amiss/application/service/save/CourseService.java amiss-core/src/test/java/amiss/application/service/save/CourseServiceTest.java
git commit -F commit.txt
```

### Task 27: Frontend — Z-Mart panel; Socket City picks up Computer for free

**Files:**
- Create: `frontend/src/game/panels/ZMartPanel.tsx`
- Create: `frontend/src/game/panels/ZMartPanel.test.tsx`
- Modify: `frontend/src/game/panels/SocketCityPanel.tsx`
- Modify: `frontend/src/game/panels/registry.ts`

**Interfaces:**
- Consumes: `getApplianceCatalog`/`buyAppliance` from PR 2's `appliances.ts` — unchanged.

- [ ] **Step 1: Add Computer to Socket City's name map** (its filter already reads every `store === 'SOCKET_CITY'` row from the catalog response, so this one-line addition is the entire Socket City change this PR needs):

```ts
const APPLIANCE_NAMES: Record<string, string> = {
  FRIDGE: 'Refrigerator',
  FREEZER: 'Freezer',
  COMPUTER: 'Computer',
};
```

- [ ] **Step 2: Create `ZMartPanel`** (mirrors `SocketCityPanel` exactly, filtered to `Z_MART`, book names instead of appliance names)

```tsx
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { buyAppliance, getApplianceCatalog } from '../../api/appliances';
import { errorMessage } from '../../api/http';
import { StorePanel } from './StorePanel';
import type { StoreRow } from './StorePanel';
import type { ApplianceResponse } from '../../api/types';
import type { PanelProps } from './types';

const BOOK_NAMES: Record<string, string> = {
  ENCYCLOPEDIA: 'Encyclopedia',
  DICTIONARY: 'Dictionary',
  ATLAS: 'Atlas',
};

export function ZMartPanel({ saveId, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const catalogQuery = useQuery({
    queryKey: ['appliances', saveId],
    queryFn: () => getApplianceCatalog(saveId),
    staleTime: Infinity,
  });

  const mutation = useMutation({
    mutationFn: (item: string) => buyAppliance(saveId, item),
    onSuccess: (res: ApplianceResponse) => {
      queryClient.setQueryData(['save', saveId], res.state);
      void queryClient.invalidateQueries({ queryKey: ['appliances', saveId] });
      const name = BOOK_NAMES[res.item] ?? res.item;
      onNotify(`Bought ${name} (R${res.price})`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['save', saveId] });
    },
  });

  if (catalogQuery.isPending) {
    return <p>Loading stock…</p>;
  }
  if (catalogQuery.error !== null) {
    return <p role="alert">{catalogQuery.error.message}</p>;
  }

  const stock = catalogQuery.data?.filter((item) => item.store === 'Z_MART');
  if (stock === undefined) {
    return null;
  }

  const rows: StoreRow[] = stock.map((item) => ({
    id: item.id,
    label: BOOK_NAMES[item.id] ?? item.id,
    price: item.price,
    detail: item.owned ? 'Owned' : undefined,
    actionLabel: 'Buy',
  }));

  return (
    <StorePanel
      heading="Z-Mart"
      rows={rows}
      onAction={(id) => mutation.mutate(id)}
      pending={mutation.isPending}
      error={mutation.error !== null ? errorMessage(mutation.error) : null}
    />
  );
}
```

- [ ] **Step 3: Register it**

In `registry.ts`, add `import { ZMartPanel } from './ZMartPanel';` and `Z_MART: ZMartPanel,` to `PANEL_REGISTRY`.

- [ ] **Step 4: Write `ZMartPanel.test.tsx`** — mirror `SocketCityPanel.test.tsx` exactly (same four cases: filters correctly, shows Owned, buys and updates cache, renders the ApiError alert), swapping the fixture to the three book ids/prices from `docs/superpowers/specs/2026-07-16-kan23-needs-inventory-design.md` (Encyclopedia $475, Dictionary $70, Atlas $55) and one `SOCKET_CITY`-store row to prove the filter excludes it.

- [ ] **Step 5: Update `SocketCityPanel.test.tsx`** — add a `COMPUTER` row to its catalog fixture and one assertion that it renders as "Computer" alongside Fridge/Freezer.

- [ ] **Step 6: Run the frontend suite**

Run: `cd frontend; npm test; npm run lint; npm run typecheck; npm run format:check`
Expected: all green.

- [ ] **Step 7: Commit**

```powershell
Set-Content -Encoding ascii commit.txt @"
feat: Z-Mart panel for the three Books; Socket City gains Computer (KAN-23)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
"@
git add frontend/src/game/panels/ZMartPanel.tsx frontend/src/game/panels/ZMartPanel.test.tsx frontend/src/game/panels/SocketCityPanel.tsx frontend/src/game/panels/SocketCityPanel.test.tsx frontend/src/game/panels/registry.ts
git commit -F commit.txt
```

### Task 28: Full verify, live check, CV highlights, push, PR

**Files:**
- Modify: `tasks/cv-highlights.md`

- [ ] **Step 1: Full verify**

Run: `.\mvnw -B clean verify` (Docker running)
Expected: BUILD SUCCESS — no new migration this PR, so the existing V1→V12 IT chain is the only proof needed; no IT changes required.

- [ ] **Step 2: Live check**

Buy a Computer at Socket City and confirm the HUD/QT panels are unaffected (it's an Employment/University concern, not needs); enroll in a course, buy the three Books at the new Z-Mart panel, and confirm graduation now happens at study session 8 instead of 10 (watch `studiesDone`/`studiesRemaining` on the University panel). Confirm Z-Mart's book rows show "Owned" once purchased and never reappear as purchasable-again duplicates in a confusing way (repeat purchases are harmless no-ops price-wise, per `ApplianceService`). Stop the API via the port-8080 owner pid when done.

- [ ] **Step 3: CV highlights entry**

Add an entry to `tasks/cv-highlights.md` covering: extra-credit study reduction, the minimal appliance-ownership plumbing now complete across Fridge/Freezer/Computer/3 Books, and a pointer to KAN-58 as the follow-on for the full browsable/breakable appliances catalog.

- [ ] **Step 4: Push + PR**

```powershell
git push -u origin feat/kan23-extra-credit
gh pr create --base develop --title "feat: extra credit + Z-Mart books; KAN-23 complete (KAN-23)" --body-file <bodyfile>
```

Body: summary of the extra-credit formula, the new Z-Mart panel, and that this is the last of the 5 stacked PRs — note KAN-23 transitions to Done in Jira once this merges, and KAN-58 (appliances catalog) is the follow-on ticket.

---

## Plan-wide verification checklist (after all 5 PRs merge)

- [ ] `.\mvnw -B clean verify` green on `develop` at the tip of PR 5.
- [ ] `cd frontend; npm test; npm run lint; npm run typecheck; npm run format:check` green on `develop`.
- [ ] Live walkthrough on `develop`: start a fresh save, go several turns without eating (confirm the 20h penalty + occasional Doctor Visit), buy a Fridge/Freezer and confirm food banks correctly, buy clothes across all three categories and let them lapse, relax repeatedly and confirm the happiness-once-per-turn rule, enroll and graduate a course early with Computer + Books owned.
- [ ] Jira: transition **KAN-23** to Done with a closing comment referencing all 5 merged PRs; confirm **KAN-58** (filed alongside the spec) is the tracked follow-on for the full appliances catalog.
- [ ] `docs/ARCHITECTURE.md`'s package tour gains `ApplianceItem`, `DoctorVisitService`, `RelaxService`, `ApplianceService` if a future docs pass touches it (not required by this plan, noted for completeness).

