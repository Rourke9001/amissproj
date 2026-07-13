# KAN-48 Economy Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The wiki-exact Jones economy: two hidden per-save values (Index −3..+3, Reading −30..+90) drifting weekly, every price/wage/fee = `base + base×Reading/60`, wage snapshotted at hire, and week-8+ market crashes/booms surfaced in the end-week report.

**Architecture:** One new `EconomyService` in `amiss-core`'s application layer is the single pricing authority; core charge paths and the API's catalog/DTO reads both call it, so displayed price always equals charged price. Economy state persists on `tblsave` (V7/V8, expand/contract). Randomness is an injected `IntUnaryOperator roll1toN` (generalising the existing `IntSupplier roll1to100` pattern) so every outcome is scriptable in tests.

**Tech Stack:** Java 21, Spring Boot 3.5, Flyway 11, Spring Data JPA (validate-only), JUnit 5 + Mockito, MySQL 9 Testcontainers ITs; React 19 + TS + TanStack Query 5 + Vitest 4.

**Spec:** `docs/superpowers/specs/2026-07-12-kan48-economy-engine-design.md` — read it first; it carries the wiki quotes and locked decisions.

## Global Constraints

- Integer math only for money/time — `Math.floorDiv` for the price formula, never floats.
- Base prices/wages in `tbljob` and the `FastFoodItem`/`FoodPack`/`ClothingItem` enums are **never mutated**.
- `economyIndex`, `economyReading` (and the existing `experience`/`dependability`) **never appear on a wire DTO**.
- `wage` on the wire keeps today's shape (`JobDto.wage` is already a nullable Integer).
- Rent stays flat R80 (KAN-50); bank stays interest-free; pawn/stocks are KAN-49.
- Shell is PowerShell 5.1: no `&&`; quote `-D` args (`"-Dtest=..."`). Commits: write the message to a file with `Set-Content -Encoding ascii`, then `git commit -F <file>`; every message ends with the `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` trailer.
- After any `amiss-core` change, run `.\mvnw -B install -DskipTests` before `spring-boot:run` (the API resolves core from `~/.m2`).
- Frontend tests: `cd frontend; npm test` (script = `vitest run`).
- Full backend suite: `.\mvnw -B clean package` (unit); `.\mvnw -B clean verify` additionally runs the Testcontainers ITs (needs Docker Desktop running — launch the exe and wait ~1 min if down).
- Delivery: 3 stacked PRs into `develop`, merged in order. Branch chain: `feat/kan48-economy-core` (cut from `feat/kan48-economy-engine`, the spec/plan docs branch) → `feat/kan48-wage-snapshot` → `feat/kan48-crash-boom`.

---

# PR 1 — `feat/kan48-economy-core` (V7, EconomyService, scaled item/enroll prices, save-scoped food+clothes catalogs)

### Task 1: V7 migration + economy fields on SaveState/SaveEntity

**Files:**
- Create: `amiss-core/src/main/resources/db/migration/V7__economy_state.sql`
- Modify: `amiss-core/src/main/java/amiss/domain/model/SaveState.java`
- Modify: `amiss-api/src/main/java/amiss/api/persistence/jpa/SaveEntity.java`
- Modify: `amiss-api/src/main/java/amiss/api/persistence/jpa/JpaSaveRepository.java` (lines 42-59 `update`, 64-71 `toDomain`)
- Modify: every `new SaveState(` call site (find with the grep in Step 3; known: `amiss-core/src/test/java/amiss/application/service/save/TestSaves.java:25`, `amiss-api/src/test/java/amiss/api/web/PlayerStateAssemblerTest.java:64`)

**Interfaces:**
- Produces: `SaveState.economyIndex()` / `setEconomyIndex(int)` / `economyReading()` / `setEconomyReading(int)`; SaveState constructor gains two trailing int params `(…, boolean won, int economyIndex, int economyReading)`. Every later task relies on these exact names.

- [ ] **Step 1: Write the migration**

```sql
-- V7: hidden per-save economy state (KAN-48, PR 1).
-- Index = the economy's trend (-3..+3); Reading drives every price:
-- price = base + base*reading/60 (50%..250% of base). Both hidden stats —
-- never exposed on a wire DTO. Defaults 0/0 = exactly pre-economy prices,
-- so existing saves migrate with zero price shock.
ALTER TABLE tblsave
    ADD COLUMN economy_index   TINYINT  NOT NULL DEFAULT 0 AFTER won,
    ADD COLUMN economy_reading SMALLINT NOT NULL DEFAULT 0 AFTER economy_index;
```

- [ ] **Step 2: Add the fields to `SaveState`**

Append two constructor params after `boolean won` and set them; add accessors mirroring the existing style (e.g. after `setWon`):

```java
    private int economyIndex;
    private int economyReading;
```

```java
    public int economyIndex() {
        return economyIndex;
    }

    public void setEconomyIndex(int economyIndex) {
        this.economyIndex = economyIndex;
    }

    public int economyReading() {
        return economyReading;
    }

    public void setEconomyReading(int economyReading) {
        this.economyReading = economyReading;
    }
```

Update the class javadoc's hidden-stats sentence to name all four hidden values (`experience`, `dependability`, `economyIndex`, `economyReading`).

- [ ] **Step 3: Fix every constructor call site**

Run: `.\mvnw -B -pl amiss-core -pl amiss-api compile` — it will fail at each `new SaveState(` site. Also grep to be sure:

```
rg -n "new SaveState\(" --glob "*.java"
```

Append `, 0, 0` at every site (tests construct pre-economy saves; 0/0 is the new-game default). `TestSaves.newSave()` becomes:

```java
    static SaveState newSave() {
        return new SaveState(SAVE_ID, "tester", "Save 1", 0, 0, 4320, 1, 100, 0, 0, 1, 0, 1,
                null, 50, 10, 20, null, 0, 50, 50, 50, 50, false, 0, 0);
    }
```

- [ ] **Step 4: Map the columns in `SaveEntity` + `JpaSaveRepository`**

`SaveEntity` — add after the `won` field (plus matching getters/setters in the accessor block):

```java
    /** Hidden economy trend, -3..+3 (KAN-48). Never expose on a wire DTO. */
    @Column(name = "economy_index", nullable = false)
    private int economyIndex;

    /** Hidden economy reading, -30..+90 (KAN-48): price = base + base*reading/60. */
    @Column(name = "economy_reading", nullable = false)
    private int economyReading;
```

`JpaSaveRepository.update` — add before `saves.saveAndFlush(entity);`:

```java
            entity.setEconomyIndex(state.economyIndex());
            entity.setEconomyReading(state.economyReading());
```

`JpaSaveRepository.toDomain` — append the two new constructor args:

```java
        return new SaveState(e.getId(), e.getOwner(), e.getLabel(), e.getXpos(), e.getYpos(),
                e.getTime(), e.getRound(), e.getCash(), e.getBank(), e.getDebt(), e.getRent(),
                e.getEat(), e.getClothing(), e.getJobId(), e.getHappiness(), e.getExperience(),
                e.getDependability(), e.getCurrentCourseId(), e.getEduprog(),
                e.getGoalWealth(), e.getGoalHappiness(), e.getGoalEducation(), e.getGoalCareer(),
                e.getWon() != 0, e.getEconomyIndex(), e.getEconomyReading());
```

(`SaveEntity`'s new-save constructor needs no change — Java int fields default to 0, matching the column defaults.)

- [ ] **Step 5: Run the whole unit suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS, all existing tests green (no behaviour changed yet).

- [ ] **Step 6: Commit**

`feat: V7 economy_index/economy_reading on tblsave + SaveState (KAN-48)`

### Task 2: `EconomyService.price` — the one pricing formula (TDD)

**Files:**
- Create: `amiss-core/src/main/java/amiss/application/service/save/EconomyService.java`
- Create: `amiss-core/src/test/java/amiss/application/service/save/EconomyServiceTest.java`

**Interfaces:**
- Consumes: `SaveState.economyReading()` (Task 1).
- Produces: `EconomyService(IntUnaryOperator roll1toN)` constructor; `int price(int base, SaveState save)`; `void driftWeekly(SaveState save)` (Task 3 fills drift in). Constants `READING_MIN=-30`, `READING_MAX=90`, `INDEX_MIN=-3`, `INDEX_MAX=3` are package-visible for tests.

- [ ] **Step 1: Write the failing price tests**

```java
package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

import amiss.domain.model.SaveState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class EconomyServiceTest {

    /** Scripted rolls: pops the next queued value whatever bound is asked for. */
    private static IntUnaryOperator rolls(int... values) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int v : values) {
            queue.add(v);
        }
        return n -> queue.pop();
    }

    private static SaveState saveWithReading(int reading) {
        SaveState save = TestSaves.newSave();
        save.setEconomyReading(reading);
        return save;
    }

    @Test
    void readingZeroMeansBasePrice() {
        assertEquals(32, new EconomyService(rolls()).price(32, saveWithReading(0)));
    }

    @Test
    void readingFloorHalvesPrices() {
        // -30/60 = -50%: the wiki's cheap extreme.
        assertEquals(16, new EconomyService(rolls()).price(32, saveWithReading(-30)));
    }

    @Test
    void readingCeilingMeans250Percent() {
        // +90/60 = +150%: 20 -> 50.
        assertEquals(50, new EconomyService(rolls()).price(20, saveWithReading(90)));
    }

    @Test
    void negativeReadingsRoundDownDeterministically() {
        // floorDiv(25 * -29, 60) = floorDiv(-725, 60) = -13 -> 12 (not -12 -> 13).
        assertEquals(12, new EconomyService(rolls()).price(25, saveWithReading(-29)));
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=EconomyServiceTest"`
Expected: COMPILATION ERROR — `EconomyService` does not exist.

- [ ] **Step 3: Implement the service skeleton + price**

```java
package amiss.application.service.save;

import amiss.domain.model.SaveState;
import java.util.function.IntUnaryOperator;

/**
 * The hidden per-save economy (KAN-48), wiki-exact where the wiki documents it. Two
 * persisted values drive every price: the Index (trend, -3..+3) and the Reading
 * (-30..+90); a displayed/charged price is {@code base + base*Reading/60} — 50% to
 * 250% of base. Base prices in the catalogs are never mutated; this service is the
 * single authority both charge paths and catalog reads consult, so the price a player
 * sees is the price they pay. Rolls come through the injected {@code roll1toN}
 * (uniform 1..n) so every outcome is deterministic under test.
 */
public class EconomyService {

    static final int INDEX_MIN = -3;
    static final int INDEX_MAX = 3;
    static final int READING_MIN = -30;
    static final int READING_MAX = 90;
    /** Our drift tuning (the wiki declines to document the original formula). */
    static final int READING_STEP_PER_INDEX = 10;

    private final IntUnaryOperator roll1toN;

    public EconomyService(IntUnaryOperator roll1toN) {
        this.roll1toN = roll1toN;
    }

    /** The economy-adjusted price of {@code base} for this save. Integer, floor-rounded. */
    public int price(int base, SaveState save) {
        return base + Math.floorDiv(base * save.economyReading(), 60);
    }

    /** One week of Index/Reading drift; Task 3 wires this into the rollover. */
    public void driftWeekly(SaveState save) {
        int index = clamp(save.economyIndex() + roll1toN.applyAsInt(3) - 2, INDEX_MIN, INDEX_MAX);
        int reading = clamp(save.economyReading() + READING_STEP_PER_INDEX * index
                + roll1toN.applyAsInt(11) - 6, READING_MIN, READING_MAX);
        save.setEconomyIndex(index);
        save.setEconomyReading(reading);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=EconomyServiceTest"`
Expected: 4 tests PASS.

- [ ] **Step 5: Add the failing drift tests, run, verify they pass too**

```java
    @Test
    void driftMovesIndexThenReadingWithMomentumAndNoise() {
        // Roll 3 -> index +1 (0 -> 1); roll 6 -> noise 0. Reading 0 + 10*1 + 0 = 10.
        SaveState save = saveWithReading(0);
        new EconomyService(rolls(3, 6)).driftWeekly(save);
        assertEquals(1, save.economyIndex());
        assertEquals(10, save.economyReading());
    }

    @Test
    void driftClampsIndexAndReadingAtTheirBounds() {
        SaveState save = saveWithReading(90);
        save.setEconomyIndex(3);
        // Roll 3 -> +1 clamps at +3; noise roll 11 -> +5; reading 90 + 30 + 5 clamps at 90.
        new EconomyService(rolls(3, 11)).driftWeekly(save);
        assertEquals(3, save.economyIndex());
        assertEquals(90, save.economyReading());
    }

    @Test
    void driftClampsAtTheFloorToo() {
        SaveState save = saveWithReading(-30);
        save.setEconomyIndex(-3);
        // Roll 1 -> -1 clamps at -3; noise roll 1 -> -5; reading floor holds.
        new EconomyService(rolls(1, 1)).driftWeekly(save);
        assertEquals(-3, save.economyIndex());
        assertEquals(-30, save.economyReading());
    }
```

Run: `.\mvnw -B -pl amiss-core test "-Dtest=EconomyServiceTest"`
Expected: 7 tests PASS (implementation already exists — these lock the drift contract).

- [ ] **Step 6: Commit**

`feat: EconomyService price formula + weekly drift (KAN-48)`

### Task 3: Wire the economy into the composition root and the rollover

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/WeekRolloverService.java` (ctor + `endWeek` line 68-70 area)
- Modify: `amiss-api/src/main/java/amiss/api/config/PersistenceConfig.java:76-80`
- Modify: `amiss-core/src/test/java/amiss/application/service/save/WeekRolloverServiceTest.java:28-30`
- Modify: `amiss-api/src/test/java/amiss/api/web/PlayerStateAssemblerTest.java:58-61`

**Interfaces:**
- Consumes: `EconomyService` (Task 2).
- Produces: `SaveGameServices` constructor gains a trailing `IntUnaryOperator roll1toN` param; new getter `EconomyService economy()`; `WeekRolloverService(SaveRepository, GoalService, ActionCosts, EconomyService)`. Tasks 4-6 and PR 2/3 use `services.economy()`.

- [ ] **Step 1: Write the failing rollover-drift test** (add to `WeekRolloverServiceTest`)

The test helper changes shape — update `service()` (used by every existing test) to a neutral economy so no older expectation shifts, and add a scripted-roll variant for the new test:

```java
    /** Neutral economy: index step 0 (roll 2 on the 1..3 die), noise 0 (roll 6 on 1..11). */
    private static java.util.function.IntUnaryOperator neutralRolls() {
        return n -> n == 3 ? 2 : 6;
    }

    private WeekRolloverService service() {
        return service(neutralRolls());
    }

    private WeekRolloverService service(java.util.function.IntUnaryOperator rolls) {
        return new WeekRolloverService(saves, new GoalService(degrees), ActionCosts.defaults(),
                new EconomyService(rolls));
    }
```

(No existing rollover test asserts the economy fields, so a truly neutral drift keeps them all byte-identical in behaviour.)

New test:

```java
    @Test
    void rolloverDriftsTheEconomy() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();

        // Index roll 3 -> +1; noise roll 6 -> 0: reading 0 -> 10.
        service(rolls(3, 6)).endWeek(save);

        assertEquals(1, save.economyIndex());
        assertEquals(10, save.economyReading());
    }
```

with the same `rolls(int...)` helper as `EconomyServiceTest` (copy it in; it's 8 lines — package-private duplication beats a shared test util for two files).

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=WeekRolloverServiceTest"`
Expected: COMPILATION ERROR — `WeekRolloverService` has no 4-arg constructor.

- [ ] **Step 3: Implement**

`WeekRolloverService`: add the field + ctor param, and call the drift **after** the dependability decay, **before** the win check (the spec's ordering — PR 3's crash effects must land before goals are judged):

```java
    private final EconomyService economy;

    public WeekRolloverService(SaveRepository saves, GoalService goals, ActionCosts costs,
            EconomyService economy) {
        this.saves = saves;
        this.goals = goals;
        this.costs = costs;
        this.economy = economy;
    }
```

```java
        save.setDependability(Math.max(0, save.dependability() - WEEKLY_DEPENDABILITY_DECAY));

        economy.driftWeekly(save);

        boolean wonNow = save.won() || goals.progress(save).allMet();
```

`SaveGameServices`: ctor gains `IntUnaryOperator roll1toN` (import stays — `IntSupplier` import remains for hiring), builds the economy first and threads it:

```java
    private final EconomyService economy;
```

```java
    public SaveGameServices(SaveRepository saves, JobCatalog jobs, DegreeCatalog degreeCatalog,
            SaveDegrees saveDegrees, Turndowns turndowns, ActionCosts costs,
            IntSupplier roll1to100, IntUnaryOperator roll1toN) {
        this.saves = saves;
        this.economy = new EconomyService(roll1toN);
        this.goals = new GoalService(saveDegrees);
        this.hiring = new HiringService(saves, jobs, saveDegrees, turndowns, costs, roll1to100);
        this.shifts = new ShiftService(saves, jobs, saveDegrees, costs);
        this.courses = new CourseService(saves, degreeCatalog, saveDegrees, costs);
        this.weeks = new WeekRolloverService(saves, goals, costs, economy);
        this.travel = new TravelService(saves, costs);
        this.bank = new BankService(saves);
        this.rent = new RentService(saves, costs);
        this.shop = new ShopService(saves, costs);
    }
```

(Tasks 4-5 extend `ShopService`/`CourseService` with the economy — leave them as-is here.)

```java
    /** Production wiring: uniform rolls off one shared PRNG. */
    public static SaveGameServices withRandomRolls(SaveRepository saves, JobCatalog jobs,
            DegreeCatalog degreeCatalog, SaveDegrees saveDegrees, Turndowns turndowns,
            ActionCosts costs) {
        Random random = new Random();
        return new SaveGameServices(saves, jobs, degreeCatalog, saveDegrees, turndowns, costs,
                () -> random.nextInt(100) + 1, n -> random.nextInt(n) + 1);
    }
```

```java
    public EconomyService economy() {
        return economy;
    }
```

Add `import java.util.function.IntUnaryOperator;`.

`PersistenceConfig` is unchanged (it calls `withRandomRolls`). `PlayerStateAssemblerTest.services()` gains the new arg:

```java
        return new SaveGameServices(saves, jobCatalog, degreeCatalog, saveDegrees, turndowns,
                ActionCosts.defaults(), () -> 100, n -> 1);
```

- [ ] **Step 4: Run the full suite**

Run: `.\mvnw -B clean package`
Expected: BUILD SUCCESS — the new drift test passes, nothing else notices (no existing rollover test reads the economy fields).

- [ ] **Step 5: Commit**

`feat: wire EconomyService into SaveGameServices + weekly rollover drift (KAN-48)`

### Task 4: ShopService charges economy prices (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/ShopService.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/PurchaseOutcome.java`
- Modify: `amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java` (one line)
- Test: `amiss-core/src/test/java/amiss/application/service/save/ShopServiceTest.java`

**Interfaces:**
- Consumes: `EconomyService.price(base, save)` (Task 2).
- Produces: `ShopService(SaveRepository, ActionCosts, EconomyService)`; `PurchaseOutcome` gains a 4th component `int pricePaid` (the economy-adjusted price, populated on every status). Task 6's controller changes read `outcome.pricePaid()` / `EatOutcome.price()`.

- [ ] **Step 1: Write the failing tests** (add to the existing `ShopServiceTest`, matching its construction style — read the file first and mirror how it builds `ShopService`; every existing `new ShopService(saves, costs)` becomes `new ShopService(saves, costs, new EconomyService(n -> 1))`)

```java
    @Test
    void eatChargesTheEconomyAdjustedPrice() {
        SaveState save = TestSaves.newSave();
        save.setEconomyReading(60);   // +100%: every price doubles
        ShopService shop = new ShopService(saves, ActionCosts.defaults(),
                new EconomyService(n -> 1));

        EatOutcome outcome = shop.eat(save, FastFoodItem.BURGER);

        assertEquals(EatOutcome.Status.OK, outcome.status());
        assertEquals(64, outcome.price());          // base 32 doubled
        assertEquals(100 - 64, save.cash());
    }

    @Test
    void groceriesAndClothesChargeAndReportTheAdjustedPrice() {
        SaveState save = TestSaves.newSave();
        save.setEconomyReading(-30);  // -50%: half price
        ShopService shop = new ShopService(saves, ActionCosts.defaults(),
                new EconomyService(n -> 1));

        PurchaseOutcome groceries = shop.buyGroceries(save, FoodPack.ONE_WEEK);
        assertEquals(12, groceries.pricePaid());    // floorDiv(25*-30,60)=-13 -> 12

        PurchaseOutcome clothes = shop.buyClothes(save, ClothingItem.CASUAL);
        assertEquals(10, clothes.pricePaid());      // base 20 halved
    }
```

(Check the exact enum base prices in `FastFoodItem`/`FoodPack`/`ClothingItem` when writing — BURGER=32, ONE_WEEK=25, CASUAL=20 per the current enums; adjust the arithmetic if the file says otherwise.)

- [ ] **Step 2: Run to verify failure**

Run: `.\mvnw -B -pl amiss-core test "-Dtest=ShopServiceTest"`
Expected: COMPILATION ERROR (3-arg ctor, `pricePaid()`).

- [ ] **Step 3: Implement**

`PurchaseOutcome` — append the component and document it:

```java
public record PurchaseOutcome(Status status, int remainingMinutes, int cash, int pricePaid) {
```

(javadoc `@param pricePaid the economy-adjusted price of the item — what was, or would have been, charged`.)

`ShopService` — ctor gains `EconomyService economy` (field + param, stored); compute the adjusted price once at the top of each method and use it everywhere the base price was used:

- `eat`: `int price = economy.price(item.price(), save);` (the local already exists — change its initializer; the rest of the method flows unchanged).
- `buyGroceries`: add `int price = economy.price(pack.price(), save);` as the first line; replace `pack.price()` in the cash check and the `setCash` line with `price`; append `price` to all three `PurchaseOutcome` constructions.
- `buyClothes`: same with `int price = economy.price(item.price(), save);` replacing `item.price()`; append `price` to all four constructions.

`SaveGameServices`: `this.shop = new ShopService(saves, costs, economy);`

Fix any other `new PurchaseOutcome(` compile break the same way (grep `rg -n "new PurchaseOutcome\(" --glob "*.java"`).

- [ ] **Step 4: Run the core suite**

Run: `.\mvnw -B -pl amiss-core test`
Expected: PASS (existing shop tests still pass — reading defaults to 0, so adjusted price == base price everywhere they assert).

- [ ] **Step 5: Commit**

`feat: ShopService charges economy-adjusted prices (KAN-48)`

### Task 5: CourseService enroll fee scales (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/CourseService.java` (ctor, `EnrollResult`, `enroll` lines 95-102)
- Modify: `amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java` (one line)
- Modify: `amiss-api/src/main/java/amiss/api/web/UniversityController.java:87`
- Test: `amiss-core/src/test/java/amiss/application/service/save/CourseServiceTest.java`

**Interfaces:**
- Produces: `CourseService(SaveRepository, DegreeCatalog, SaveDegrees, ActionCosts, EconomyService)`; `EnrollResult` gains 3rd component `int feePaid`; `ENROLL_FEE` stays the public base constant.

- [ ] **Step 1: Failing test** (mirror `CourseServiceTest`'s existing construction/stubbing style; update its `new CourseService(...)` sites to pass `new EconomyService(n -> 1)`)

```java
    @Test
    void enrollChargesTheEconomyAdjustedFee() {
        SaveState save = TestSaves.newSave();   // R100 cash
        save.setEconomyReading(60);             // fee 50 -> 100
        // stub catalog.byId to an available degree exactly as the existing OK-path test does

        CourseService.EnrollResult result = service.enroll(save, DEGREE_ID);

        assertEquals(CourseService.EnrollResult.Status.OK, result.status());
        assertEquals(100, result.feePaid());
        assertEquals(0, save.cash());
    }

    @Test
    void enrollRejectsWhenCashIsBelowTheAdjustedFee() {
        SaveState save = TestSaves.newSave();   // R100 < 125
        save.setEconomyReading(90);             // fee 50 -> 125
        // same stubbing

        assertEquals(CourseService.EnrollResult.Status.INSUFFICIENT_CASH,
                service.enroll(save, DEGREE_ID).status());
    }
```

- [ ] **Step 2: Run to verify failure** — `.\mvnw -B -pl amiss-core test "-Dtest=CourseServiceTest"`, expect compile error.

- [ ] **Step 3: Implement**

```java
    public record EnrollResult(Status status, int cash, int feePaid) {
```

`enroll` — replace the fee block:

```java
        int fee = economy.price(ENROLL_FEE, save);
        if (save.cash() < fee) {
            return new EnrollResult(EnrollResult.Status.INSUFFICIENT_CASH, save.cash(), fee);
        }
        save.setCash(save.cash() - fee);
        save.setCurrentCourseId(degree.id());
        save.setEduprog(0);
        saves.update(save);
        return new EnrollResult(EnrollResult.Status.OK, save.cash(), fee);
```

Give the earlier rejection returns `fee` too — compute `int fee` before the earned/locked/enrolled checks and append it to every `EnrollResult` construction. Ctor gains `EconomyService economy`; `SaveGameServices` passes `economy`. Update the `ENROLL_FEE` javadoc: "the base fee — the charged fee scales with the economy (KAN-48)".

`UniversityController:87`: `return new EnrollResponse(outcome.feePaid(), assembler.assemble(services, save));`

- [ ] **Step 4: Run** — `.\mvnw -B clean package`, expect BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat: enroll fee scales with the economy (KAN-48)`

### Task 6: Save-scoped food/clothes catalogs with adjusted prices

**Files:**
- Modify: `amiss-api/src/main/java/amiss/api/web/FoodController.java` (catalog methods lines 57-77; response constructions 90-94, 114, 135)
- Test: whichever `amiss-api` web test covers `FoodController` (grep `rg -l "FoodController" amiss-api/src/test`) — update paths/expectations there.

**Interfaces:**
- Produces: `GET /api/saves/{saveId}/food` and `GET /api/saves/{saveId}/clothes` returning economy-adjusted prices; old `GET /api/food` / `GET /api/clothes` **deleted**. Response DTO shapes unchanged.

- [ ] **Step 1: Rewrite the two catalog endpoints**

```java
    @GetMapping("/api/saves/{saveId}/food")
    public FoodCatalogDto catalog(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<MenuItemDto> menu = new ArrayList<>(FastFoodItem.values().length);
        for (FastFoodItem item : FastFoodItem.values()) {
            menu.add(new MenuItemDto(item.name(), item.displayName(),
                    economy.price(item.price(), save)));
        }
        List<FoodPackDto> packs = new ArrayList<>(FoodPack.values().length);
        for (FoodPack pack : FoodPack.values()) {
            packs.add(new FoodPackDto(pack.name(), pack.displayName(),
                    economy.price(pack.price(), save), pack.weeks()));
        }
        return new FoodCatalogDto(menu, packs);
    }

    @GetMapping("/api/saves/{saveId}/clothes")
    public List<ClothingItemDto> clothesCatalog(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<ClothingItemDto> items = new ArrayList<>(ClothingItem.values().length);
        for (ClothingItem item : ClothingItem.values()) {
            items.add(new ClothingItemDto(item.name(), item.displayName(),
                    economy.price(item.price(), save), item.level()));
        }
        return items;
    }
```

Add `import amiss.application.service.save.EconomyService;`. Update the class javadoc ("the catalogs themselves stay unscoped" is no longer true — they are save-scoped so prices can follow the save's economy).

- [ ] **Step 2: Response prices come from the outcome, not the enum**

The action endpoints still echo base prices; switch them to what was actually charged:
- `eat` (both branches): `item.price()` → `outcome.price()`.
- `groceries`: `pack.price()` → `outcome.pricePaid()`.
- `clothes`: `item.price()` → `outcome.pricePaid()`.

(SecurityConfig needs no change — there are no per-catalog matchers; `/api/**` is already `authenticated()`, and `/api/saves/{saveId}/**` inherits the SaveScope 403 guard via `scope.require`.)

- [ ] **Step 3: Update the api web tests** for the new paths (mechanical: the mocked-MVC or test-client calls hit `/api/saves/{id}/food` etc. and stub `scope.require`).

- [ ] **Step 4: Run** — `.\mvnw -B clean package`, expect BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat: save-scoped food/clothes catalogs serve economy prices (KAN-48)`

### Task 7: Frontend — save-scoped catalogs + end-week price invalidation

**Files:**
- Modify: `frontend/src/api/food.ts:4-6`, `frontend/src/api/clothes.ts:4-6`
- Modify: `frontend/src/game/panels/MonolithBurgersPanel.tsx:12`, `frontend/src/game/panels/BlacksMarketPanel.tsx:11`, `frontend/src/game/panels/QTClothingPanel.tsx:12-16`
- Modify: `frontend/src/game/BoardScreen.tsx:73-82` (end-week `onSuccess`)
- Test: `frontend/src/game/panels/MonolithBurgersPanel.test.tsx`, `BlacksMarketPanel.test.tsx`, `QTClothingPanel.test.tsx`

**Interfaces:**
- Produces: `getFoodCatalog(saveId: number)`, `getClothesCatalog(saveId: number)`; query keys `['food', saveId]`, `['clothes', saveId]`.

- [ ] **Step 1: Update the api modules**

```ts
export function getFoodCatalog(saveId: number): Promise<FoodCatalogDto> {
  return apiFetch<FoodCatalogDto>(`/saves/${saveId}/food`);
}
```

```ts
export function getClothesCatalog(saveId: number): Promise<ClothingItemDto[]> {
  return apiFetch<ClothingItemDto[]>(`/saves/${saveId}/clothes`);
}
```

- [ ] **Step 2: Update the panel queries** (keep `staleTime: Infinity` — prices only move at rollover, and Step 3 invalidates then)

```ts
  const foodQuery = useQuery({
    queryKey: ['food', saveId],
    queryFn: () => getFoodCatalog(saveId),
    staleTime: Infinity,
  });
```

(same shape in all three panels; clothes uses `['clothes', saveId]` / `getClothesCatalog(saveId)`).

- [ ] **Step 3: Invalidate catalogs when the week rolls**

In `BoardScreen.tsx`'s `endWeekMutation.onSuccess`, after `setQueryData`:

```ts
      // Prices move at rollover (KAN-48): refetch every catalog next time it's shown.
      queryClient.invalidateQueries({ queryKey: ['food'] });
      queryClient.invalidateQueries({ queryKey: ['clothes'] });
```

- [ ] **Step 4: Update the three panel tests** — the `vi.mock`ed `getFoodCatalog`/`getClothesCatalog` now receive the saveId: add e.g. `expect(getFoodCatalog).toHaveBeenCalledWith(42)` (whatever saveId the test fixture passes) to one test per panel.

- [ ] **Step 5: Run** — `cd frontend; npm test` then `npm run lint; npm run typecheck`
Expected: all green.

- [ ] **Step 6: Commit** — `feat: SPA reads save-scoped food/clothes catalogs, refreshes prices weekly (KAN-48)`

### Task 8: V7 IT, full verify, live check, PR 1

**Files:**
- Modify: `amiss-api/src/test/java/amiss/api/persistence/jpa/SavePortsAdapterIT.java` (extend the SaveState update round-trip)

- [ ] **Step 1: Extend the adapter IT** — read the file; in the test that round-trips a `SaveState` mutation through `JpaSaveRepository.update`/`find`, additionally set and assert the new fields:

```java
        state.setEconomyIndex(2);
        state.setEconomyReading(45);
        // ... existing update + reload ...
        assertEquals(2, reloaded.economyIndex());
        assertEquals(45, reloaded.economyReading());
```

(`ddl-auto=validate` in the IT context already proves the V7 columns exist and map.)

- [ ] **Step 2: Full verify** — `.\mvnw -B clean verify` (Docker running)
Expected: BUILD SUCCESS, unit + IT suites green.

- [ ] **Step 3: Live check** — `.\mvnw -B install -DskipTests; .\mvnw -f amiss-api spring-boot:run` + `cd frontend; npm run dev`. Log in, then in MySQL: `UPDATE tblsave SET economy_reading = 60 WHERE id = <your save>;` — Monolith Burgers menu shows doubled prices, buying charges the doubled price (cash drop matches the shown price), university enroll fee doubles. Set it back to 0 afterwards. Stop the API via the port-8080 owner pid.

- [ ] **Step 4: Push + PR**

```powershell
git push -u origin feat/kan48-economy-core
gh pr create --base develop --title "feat: economy engine core - hidden Index/Reading + scaled prices (KAN-48)" --body-file <bodyfile>
```

Body: summary of V7, EconomyService, scaled item/enroll prices, save-scoped catalogs; note wages land in PR 2, crashes in PR 3. End with the Claude Code attribution line.

---

# PR 2 — `feat/kan48-wage-snapshot` (V8, hire locks the listed wage, scaled job listings)

Branch: `git checkout -b feat/kan48-wage-snapshot` off `feat/kan48-economy-core`.

### Task 9: V8 migration + `wage` on SaveState/SaveEntity

**Files:**
- Create: `amiss-core/src/main/resources/db/migration/V8__wage_snapshot.sql`
- Modify: `SaveState.java`, `SaveEntity.java`, `JpaSaveRepository.java`, every `new SaveState(` site (same drill as Task 1)

**Interfaces:**
- Produces: `SaveState.wage()` returning `Integer` (null = unemployed) / `setWage(Integer)`; constructor gains trailing `Integer wage` (after `economyReading`). All call sites append `, null`.

- [ ] **Step 1: Migration**

```sql
-- V8: wage snapshot (KAN-48, PR 2). The wage the player was hired at — the
-- Employment Office listing's economy-adjusted value, frozen until a raise
-- (KAN-49) or a crash pay-cut (PR 3). NULL = unemployed. Work pay and the
-- state DTO read this, never tbljob's base wage.
ALTER TABLE tblsave ADD COLUMN wage INT NULL AFTER job_id;

-- Backfill: pre-economy saves were all at Reading 0, so the base wage IS the
-- wage they saw when they were hired.
UPDATE tblsave s
JOIN tbljob j ON j.id = s.job_id
SET s.wage = j.wage
WHERE s.job_id IS NOT NULL;
```

- [ ] **Step 2: Field + mapping.** `SaveState`: `private Integer wage;` ctor param after `economyReading`, accessors `wage()`/`setWage(Integer)` (javadoc: hired-at wage, null while unemployed). `SaveEntity`: `@Column(name = "wage") private Integer wage;` + accessors, placed after `jobId`. `JpaSaveRepository`: `entity.setWage(state.wage());` in `update`, `e.getWage()` appended in `toDomain`. Compile, append `, null` at every broken `new SaveState(` site (`TestSaves.newSave()` ends `..., false, 0, 0, null);`).

- [ ] **Step 3: Run** — `.\mvnw -B clean package`, expect green. **Commit** — `feat: V8 wage snapshot column + SaveState.wage (KAN-48)`

### Task 10: Hire snapshots the listed wage; work pays from it (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/HiringService.java` (ctor, `hire` lines 99-106)
- Modify: `amiss-core/src/main/java/amiss/application/service/save/ShiftService.java` (lines 54-59 fired path, 67-68 pay)
- Modify: `amiss-core/src/main/java/amiss/application/service/save/SaveGameServices.java` (two lines)
- Modify: `amiss-api/src/main/java/amiss/api/web/PlayerStateAssembler.java:81-88`
- Tests: `HiringServiceTest.java`, `ShiftServiceTest.java`, `PlayerStateAssemblerTest.java`

**Interfaces:**
- Consumes: `SaveState.wage()` (Task 9), `EconomyService.price` (Task 2).
- Produces: `HiringService(SaveRepository, JobCatalog, SaveDegrees, Turndowns, ActionCosts, IntSupplier, EconomyService)`; `HireOutcome.wage()` now carries the snapshot value.

- [ ] **Step 1: Failing tests.** Update both test classes' service construction for the new ctor args (`new EconomyService(n -> 1)`); add:

```java
    // HiringServiceTest — hire in a boom locks the boosted wage
    @Test
    void hireSnapshotsTheEconomyAdjustedListingWage() {
        SaveState save = TestSaves.newSave();
        save.setEconomyReading(60);   // listings pay double
        when(jobs.byId(TestSaves.COOK.id())).thenReturn(Optional.of(TestSaves.COOK));

        HireOutcome outcome = service.apply(save, TestSaves.COOK.id());

        assertEquals(HireOutcome.Status.HIRED, outcome.status());
        assertEquals(10, outcome.wage());          // base 5 doubled
        assertEquals(10, save.wage());
    }
```

```java
    // ShiftServiceTest — pay reads the snapshot, never the catalog
    @Test
    void workPaysFromTheWageSnapshotNotTheCatalog() {
        SaveState save = TestSaves.newSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(10);                          // snapshot differs from base 5
        when(jobs.byId(TestSaves.COOK.id())).thenReturn(Optional.of(TestSaves.COOK));

        ShiftOutcome outcome = service.work(save);

        assertEquals(80, outcome.pay());           // 10 * 8 full session, not 40
    }

    @Test
    void firingForLowDependabilityClearsTheWageSnapshot() {
        SaveState save = TestSaves.newSave();
        save.setJobId(TestSaves.ASSISTANT.id());
        save.setWage(7);
        save.setDependability(0);                  // 30-required, > 5 below -> fired
        when(jobs.byId(TestSaves.ASSISTANT.id())).thenReturn(Optional.of(TestSaves.ASSISTANT));

        assertEquals(ShiftOutcome.Status.FIRED, service.work(save).status());
        assertNull(save.wage());
    }
```

(Adapt mock-field names to each test class's existing style — read them first.)

- [ ] **Step 2: Run to verify failure** — `.\mvnw -B -pl amiss-core test "-Dtest=HiringServiceTest,ShiftServiceTest"`, expect compile error.

- [ ] **Step 3: Implement.**

`HiringService`: ctor gains trailing `EconomyService economy`; `hire(...)`:

```java
    private HireOutcome hire(SaveState save, JobSpec job, int charged) {
        int listedWage = economy.price(job.wage(), save);
        save.setJobId(job.id());
        save.setWage(listedWage);
        save.setExperience(save.experience() + JOB_SWITCH_EXPERIENCE_BONUS);
        save.setDependability(Math.max(save.dependability(), HIRE_DEPENDABILITY_FLOOR));
        save.addHappiness(HIRE_HAPPINESS);
        saves.update(save);
        return HireOutcome.hired(charged, save.timeMinutes(), job.name(), listedWage);
    }
```

`ShiftService` fired path: add `save.setWage(null);` after `save.setJobId(null);`. Pay:

```java
        int wage = java.util.Objects.requireNonNull(save.wage(),
                "employed save " + save.id() + " has no wage snapshot (V8 backfill)");
        int pay = wage * FULL_SESSION_PAY_MULTIPLIER * minutes / costs.workMinutes();
```

(use a normal import; fail loud — the V8 backfill guarantees employed ⇒ wage set).

`SaveGameServices`: `this.hiring = new HiringService(saves, jobs, saveDegrees, turndowns, costs, roll1to100, economy);`

`PlayerStateAssembler.jobFor`: the job's name/location still come from the catalog; the wage is the save's:

```java
        return jobs.byId(save.jobId())
                .map(job -> new JobDto(job.name(), save.wage(), job.location()))
                .orElse(new JobDto(UNEMPLOYED, null, null));
```

Fix `PlayerStateAssemblerTest`'s employed-state fixture (its `save(...)` helper must set a wage; assert the DTO reports it).

- [ ] **Step 4: Run** — `.\mvnw -B clean package`, expect green (existing hire tests pass: reading 0 ⇒ snapshot == base wage).
- [ ] **Step 5: Commit** — `feat: hire locks the listed wage; work and the DTO read the snapshot (KAN-48)`

### Task 11: Save-scoped, economy-priced job listings

**Files:**
- Modify: `amiss-api/src/main/java/amiss/api/web/EmploymentController.java:52-58`
- Test: the api web test covering the jobs listing (grep `rg -l "api/jobs" amiss-api/src/test`)

- [ ] **Step 1: Move + scale the endpoint** (keep the `?location` filter):

```java
    @GetMapping("/api/saves/{saveId}/jobs")
    public List<JobListingDto> jobs(@PathVariable long saveId, Authentication authentication,
            @RequestParam(required = false) String location) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<JobSpec> listings = location == null ? jobCatalog.all() : jobCatalog.byLocation(location);
        return listings.stream()
                .map(job -> new JobListingDto(job.id(), job.name(), job.location(),
                        economy.price(job.wage(), save)))
                .toList();
    }
```

Add the `EconomyService` import; update the class javadoc (listings fluctuate with the save's economy; the hired wage is locked at the listed value). Update the web test paths/stubs.

- [ ] **Step 2: Run** — `.\mvnw -B clean package`, expect green. **Commit** — `feat: job listings save-scoped + economy-priced (KAN-48)`

### Task 12: Frontend jobs cutover + IT + PR 2

**Files:**
- Modify: `frontend/src/api/jobs.ts:4-7`, `frontend/src/game/panels/EmploymentOfficePanel.tsx:25`, `frontend/src/game/BoardScreen.tsx` (end-week invalidation), `frontend/src/game/panels/EmploymentOfficePanel.test.tsx`, `frontend/src/game/BoardScreen.test.tsx` (its `../api/jobs` mock)
- Modify: `amiss-api/src/test/java/amiss/api/persistence/jpa/SavePortsAdapterIT.java` (wage round-trip)

- [ ] **Step 1: Api module + panel**

```ts
export function getJobs(saveId: number, location?: string): Promise<JobListingDto[]> {
  const query = location ? `?location=${encodeURIComponent(location)}` : '';
  return apiFetch<JobListingDto[]>(`/saves/${saveId}/jobs${query}`);
}
```

```ts
  const jobsQuery = useQuery({
    queryKey: ['jobs', saveId],
    queryFn: () => getJobs(saveId),
    staleTime: Infinity,
  });
```

`BoardScreen` end-week `onSuccess`: add `queryClient.invalidateQueries({ queryKey: ['jobs'] });` beside the food/clothes lines.

- [ ] **Step 2: IT** — extend the `SavePortsAdapterIT` round-trip with `state.setWage(12);` / `assertEquals(Integer.valueOf(12), reloaded.wage());`.

- [ ] **Step 3: Run everything** — `.\mvnw -B clean verify` and `cd frontend; npm test; npm run lint; npm run typecheck`. Expected: all green.

- [ ] **Step 4: Live check** — with `economy_reading = 60` on your save: Employment Office lists doubled wages; take a job; set `economy_reading = 0`; the HUD still shows the doubled wage and a work shift pays it (snapshot survives the economy moving). Reset your save's job afterwards if you care.

- [ ] **Step 5: Push + PR** — `gh pr create --base develop --title "feat: wage snapshot + fluctuating job listings (KAN-48)"`, body noting merge order after PR 1.

---

# PR 3 — `feat/kan48-crash-boom` (week-8+ events, end-week economy report, SPA modal)

Branch: `git checkout -b feat/kan48-crash-boom` off `feat/kan48-wage-snapshot`.

### Task 13: `EconomyEvent` + `rollEvent` (TDD)

**Files:**
- Modify: `amiss-core/src/main/java/amiss/application/service/save/EconomyService.java`
- Create: `amiss-core/src/main/java/amiss/application/service/save/EconomyEvent.java`
- Test: `EconomyServiceTest.java`

**Interfaces:**
- Produces: `EconomyEvent(Type type, Severity severity, boolean fired, Integer wageCutTo, boolean bankWiped, int happinessLost)` with `Type {NONE, BOOM, CRASH}`, `Severity {MINOR, MODERATE, MAJOR}`, static `EconomyEvent.none()`; `EconomyEvent rollEvent(SaveState save)` on `EconomyService`. Task 14 threads it through the rollover.

- [ ] **Step 1: Failing tests** (scripted rolls make every branch exact; roll order is: event roll (1..31), severity (1..3), fire coin (1..2)):

```java
    private static SaveState eventEligibleSave() {
        SaveState save = TestSaves.newSave();
        save.setRound(8);
        save.setEconomyReading(85);
        return save;
    }

    @Test
    void noEventsBeforeWeekEight() {
        SaveState save = eventEligibleSave();
        save.setRound(7);
        // Would-be triggering rolls queued — they must never be consumed.
        assertEquals(EconomyEvent.Type.NONE,
                new EconomyService(rolls()).rollEvent(save).type());
    }

    @Test
    void crashNeedsReadingAtLeast80() {
        SaveState save = eventEligibleSave();
        save.setEconomyReading(79);
        // First roll = boom roll (crash ineligible): 2 -> no boom.
        assertEquals(EconomyEvent.Type.NONE,
                new EconomyService(rolls(2)).rollEvent(save).type());
    }

    @Test
    void minorCrashOnlyDropsPricesAndHappiness() {
        SaveState save = eventEligibleSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(5);
        save.setBank(200);

        // Crash roll 1 -> crash; severity roll 1 -> MINOR.
        EconomyEvent event = new EconomyService(rolls(1, 1)).rollEvent(save);

        assertEquals(EconomyEvent.Type.CRASH, event.type());
        assertEquals(EconomyEvent.Severity.MINOR, event.severity());
        assertEquals(-3, save.economyIndex());
        assertEquals(82, save.economyReading());   // 85 - 3 (the wiki's flat -5%)
        assertEquals(Integer.valueOf(5), save.wage());
        assertEquals(200, save.bank());
        assertEquals(49, save.happiness());        // 50 - 1
        assertEquals(1, event.happinessLost());
    }

    @Test
    void moderateCrashCanCutPayToEightyPercent() {
        SaveState save = eventEligibleSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(10);

        // Crash 1; severity 2 -> MODERATE; fire coin 2 -> pay cut.
        EconomyEvent event = new EconomyService(rolls(1, 2, 2)).rollEvent(save);

        assertEquals(Integer.valueOf(8), event.wageCutTo());
        assertEquals(Integer.valueOf(8), save.wage());
        assertFalse(event.fired());
        assertEquals(48, save.happiness());        // -2
    }

    @Test
    void moderateCrashCanFireInstead() {
        SaveState save = eventEligibleSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(10);

        // Crash 1; severity 2; fire coin 1 -> fired.
        EconomyEvent event = new EconomyService(rolls(1, 2, 1)).rollEvent(save);

        assertTrue(event.fired());
        assertNull(save.jobId());
        assertNull(save.wage());
    }

    @Test
    void majorCrashFiresAndWipesTheBank() {
        SaveState save = eventEligibleSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(10);
        save.setBank(500);

        // Crash 1; severity 3 -> MAJOR (no fire coin — firing is certain).
        EconomyEvent event = new EconomyService(rolls(1, 3)).rollEvent(save);

        assertTrue(event.fired());
        assertTrue(event.bankWiped());
        assertEquals(0, save.bank());
        assertNull(save.jobId());
        assertEquals(76, save.economyReading());   // 85 - 9
        assertEquals(47, save.happiness());        // -3
    }

    @Test
    void aMissedCrashRollStillAllowsABoomRoll() {
        SaveState save = eventEligibleSave();      // reading 85: crash-eligible

        // Crash roll 2 -> miss; boom roll 1 -> boom.
        EconomyEvent event = new EconomyService(rolls(2, 1)).rollEvent(save);

        assertEquals(EconomyEvent.Type.BOOM, event.type());
        assertEquals(3, save.economyIndex());
        assertEquals(90, save.economyReading());   // 85 + 6 clamped to 90
        assertEquals(50, save.happiness());        // no stocks yet: no boom bonus (wiki)
    }
```

- [ ] **Step 2: Run to verify failure** — compile error (`EconomyEvent`).

- [ ] **Step 3: Implement**

`EconomyEvent.java`:

```java
package amiss.application.service.save;

/**
 * What the economy did at one rollover (KAN-48, wiki Market Crash / Economic Boom
 * pages). {@code NONE} for the ordinary drift-only week. Crash severities are the
 * wiki's, uniform: MINOR (prices -5%), MODERATE (prices -10%, 50% fired else pay cut
 * to 80%), MAJOR (prices -15%, fired, bank wiped). Happiness loss 1/2/3 by severity.
 * A boom is a single +10% jolt. The wiki's stock effects wait for KAN-49.
 */
public record EconomyEvent(Type type, Severity severity, boolean fired,
        Integer wageCutTo, boolean bankWiped, int happinessLost) {

    public enum Type { NONE, BOOM, CRASH }

    public enum Severity { MINOR, MODERATE, MAJOR }

    public static EconomyEvent none() {
        return new EconomyEvent(Type.NONE, null, false, null, false, 0);
    }
}
```

`EconomyService` additions:

```java
    /** Events only from the wiki's week 8. */
    static final int EVENT_MIN_ROUND = 8;
    /** A crash needs a near-peak economy (wiki: Reading >= 80). */
    static final int CRASH_MIN_READING = 80;
    /** Wiki CD-ROM odds at one player: 1/(1+30*players) = 1/31, each, per week. */
    static final int EVENT_CHANCE = 31;
    /** The wiki's flat -5/-10/-15% price drop, as Reading points (price% = reading/60). */
    static final int[] CRASH_READING_DROP = {3, 6, 9};
    static final int[] CRASH_HAPPINESS_LOSS = {1, 2, 3};
    /** The wiki's flat +10% boom bump, as Reading points. */
    static final int BOOM_READING_JUMP = 6;

    /**
     * Rolls for a crash/boom after the weekly drift. Call with the round already
     * advanced. Crash is checked first (only when the Reading is at least
     * {@link #CRASH_MIN_READING}); a missed or ineligible crash still allows the
     * boom roll — the wiki's boom bound (Reading <= 120) exceeds the +90 cap, so
     * booms are always eligible.
     */
    public EconomyEvent rollEvent(SaveState save) {
        if (save.round() < EVENT_MIN_ROUND) {
            return EconomyEvent.none();
        }
        if (save.economyReading() >= CRASH_MIN_READING && roll1toN.applyAsInt(EVENT_CHANCE) == 1) {
            return crash(save);
        }
        if (roll1toN.applyAsInt(EVENT_CHANCE) == 1) {
            return boom(save);
        }
        return EconomyEvent.none();
    }

    private EconomyEvent crash(SaveState save) {
        int severityIndex = roll1toN.applyAsInt(3) - 1;
        EconomyEvent.Severity severity = EconomyEvent.Severity.values()[severityIndex];
        save.setEconomyIndex(INDEX_MIN);
        save.setEconomyReading(clamp(save.economyReading() - CRASH_READING_DROP[severityIndex],
                READING_MIN, READING_MAX));

        boolean fired = false;
        Integer wageCutTo = null;
        if (severity == EconomyEvent.Severity.MODERATE && save.employed()) {
            if (roll1toN.applyAsInt(2) == 1) {
                fired = fire(save);
            } else {
                wageCutTo = save.wage() * 4 / 5;
                save.setWage(wageCutTo);
            }
        }
        boolean bankWiped = false;
        if (severity == EconomyEvent.Severity.MAJOR) {
            if (save.employed()) {
                fired = fire(save);
            }
            bankWiped = save.bank() > 0;
            save.setBank(0);
        }
        int happinessLost = CRASH_HAPPINESS_LOSS[severityIndex];
        save.addHappiness(-happinessLost);
        return new EconomyEvent(EconomyEvent.Type.CRASH, severity, fired, wageCutTo,
                bankWiped, happinessLost);
    }

    private boolean fire(SaveState save) {
        save.setJobId(null);
        save.setWage(null);
        return true;
    }

    private EconomyEvent boom(SaveState save) {
        save.setEconomyIndex(INDEX_MAX);
        save.setEconomyReading(clamp(save.economyReading() + BOOM_READING_JUMP,
                READING_MIN, READING_MAX));
        return new EconomyEvent(EconomyEvent.Type.BOOM, null, false, null, false, 0);
    }
```

- [ ] **Step 4: Run** — `.\mvnw -B -pl amiss-core test "-Dtest=EconomyServiceTest"`, expect all PASS.
- [ ] **Step 5: Commit** — `feat: week-8+ market crashes and booms (KAN-48)`

### Task 14: Rollover integration — events before the win check (TDD)

**Files:**
- Modify: `WeekRolloverService.java` (`RolloverResult`, `endWeek`)
- Modify: `PlayerController.java:51-60`
- Create: `amiss-api/src/main/java/amiss/api/web/dto/EconomyEventDto.java`
- Modify: `amiss-api/src/main/java/amiss/api/web/dto/EndWeekResponse.java`
- Tests: `WeekRolloverServiceTest.java`, plus the api web test covering end-week

**Interfaces:**
- Produces: `RolloverResult` gains trailing `EconomyEvent economy`; `EndWeekResponse` gains `EconomyEventDto economy` before `state`; `EconomyEventDto(String event, String severity, boolean fired, Integer wageCutTo, boolean bankWiped, int happinessLost)` with static `from(EconomyEvent)`.

- [ ] **Step 1: Failing tests** (in `WeekRolloverServiceTest`):

```java
    @Test
    void crashEffectsLandBeforeTheWinCheck() {
        // The winningRequiresAllFourGoals fixture, but a MAJOR crash wipes the bank
        // first: wealth (3000+0)/100 = 30 < 50 -> no win.
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(1, 2, 3, 4, 5, 6));
        SaveState save = weekOverSave();
        save.setRound(8);
        save.setEconomyReading(85);
        save.setCash(3000);
        save.setBank(2000);
        save.setJobId(TestSaves.CLERK.id());
        save.setWage(5);
        save.setDependability(43);

        // Rolls: index 2 (step 0), noise 6 (0), crash 1, severity 3 -> MAJOR.
        WeekRolloverService.RolloverResult result = service(rolls(2, 6, 1, 3)).endWeek(save);

        assertEquals(EconomyEvent.Type.CRASH, result.economy().type());
        assertFalse(result.won());                 // bank wiped + fired before the check
        assertEquals(0, save.bank());
    }

    @Test
    void quietWeeksReportNoEconomyEvent() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        assertEquals(EconomyEvent.Type.NONE,
                service().endWeek(weekOverSave()).economy().type());
    }
```

(The neutral `service()` helper's rolls must now also cover the event rolls on rounds ≥ 8: `n -> n == 3 ? 2 : n == 31 ? 2 : 6` — roll 2 on the 1..31 event die never triggers.)

- [ ] **Step 2: Run to verify failure** — no `economy()` component on `RolloverResult`.

- [ ] **Step 3: Implement**

`RolloverResult`:

```java
    public record RolloverResult(boolean rolled, int newRound, boolean fed, int weekMinutes,
            boolean rentDue, boolean debtCharged, boolean won, EconomyEvent economy) {

        static RolloverResult weekStillRunning() {
            return new RolloverResult(false, -1, false, -1, false, false, false,
                    EconomyEvent.none());
        }
    }
```

`endWeek` — replace the drift call and final return:

```java
        save.setDependability(Math.max(0, save.dependability() - WEEKLY_DEPENDABILITY_DECAY));

        economy.driftWeekly(save);
        EconomyEvent economyEvent = economy.rollEvent(save);

        boolean wonNow = save.won() || goals.progress(save).allMet();
        save.setWon(wonNow);

        saves.update(save);
        return new RolloverResult(true, newRound, fed, save.timeMinutes(), rentDue,
                debtCharged, wonNow, economyEvent);
```

`EconomyEventDto.java`:

```java
package amiss.api.web.dto;

import amiss.application.service.save.EconomyEvent;

/**
 * The economy's move at rollover, for the end-week report (KAN-48). {@code event}
 * NONE/BOOM/CRASH; {@code severity} only for crashes. Index/Reading themselves stay
 * hidden — this reports consequences, never the numbers.
 */
public record EconomyEventDto(String event, String severity, boolean fired,
        Integer wageCutTo, boolean bankWiped, int happinessLost) {

    public static EconomyEventDto from(EconomyEvent e) {
        return new EconomyEventDto(e.type().name(),
                e.severity() == null ? null : e.severity().name(),
                e.fired(), e.wageCutTo(), e.bankWiped(), e.happinessLost());
    }
}
```

`EndWeekResponse` — add `EconomyEventDto economy` before `state` (update its javadoc). `PlayerController.endWeek`:

```java
        return new EndWeekResponse(result.newRound(), result.fed(), result.rentDue(),
                result.debtCharged(), result.won(), EconomyEventDto.from(result.economy()),
                assembler.assemble(services, save));
```

- [ ] **Step 4: Run** — `.\mvnw -B clean package`, fix any web-test fixture that constructs `EndWeekResponse`, expect green.
- [ ] **Step 5: Commit** — `feat: end-week reports the economy event (KAN-48)`

### Task 15: SPA — economy lines in the end-week modal

**Files:**
- Modify: `frontend/src/api/types.ts` (`EndWeekResponse` + new `EconomyEventDto`)
- Modify: `frontend/src/game/EndWeekModal.tsx`
- Tests: `frontend/src/game/EndWeekModal.test.tsx`, `frontend/src/game/BoardScreen.test.tsx` (end-week fixtures gain `economy`)

- [ ] **Step 1: Types**

```ts
export interface EconomyEventDto {
  event: 'NONE' | 'BOOM' | 'CRASH';
  severity: 'MINOR' | 'MODERATE' | 'MAJOR' | null;
  fired: boolean;
  wageCutTo: number | null;
  bankWiped: boolean;
  happinessLost: number;
}
```

and add `economy: EconomyEventDto;` to `EndWeekResponse`.

- [ ] **Step 2: Failing modal tests** — extend the existing fixture helper with `economy: { event: 'NONE', severity: null, fired: false, wageCutTo: null, bankWiped: false, happinessLost: 0 }` as the default, then:

```tsx
  it('reports a boom', () => {
    render(<EndWeekModal result={{ ...base, economy: { ...noEvent, event: 'BOOM' } }} onClose={vi.fn()} />);
    expect(screen.getByText('Economic boom! Prices and wages have surged.')).toBeInTheDocument();
  });

  it('reports a major crash with firing and a bank wipe', () => {
    render(
      <EndWeekModal
        result={{ ...base, economy: { event: 'CRASH', severity: 'MAJOR', fired: true, wageCutTo: null, bankWiped: true, happinessLost: 3 } }}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByText('The market crashed! Prices tumble.')).toBeInTheDocument();
    expect(screen.getByText('You were laid off in the downturn.')).toBeInTheDocument();
    expect(screen.getByText('Your bank savings were wiped out.')).toBeInTheDocument();
  });

  it('reports a pay cut', () => {
    render(
      <EndWeekModal
        result={{ ...base, economy: { event: 'CRASH', severity: 'MODERATE', fired: false, wageCutTo: 8, bankWiped: false, happinessLost: 2 } }}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByText('Your pay was cut to R8/h.')).toBeInTheDocument();
  });
```

- [ ] **Step 3: Run to verify failure** — `cd frontend; npm test`, expect the new tests red (and possibly existing fixtures breaking on the missing `economy` field — fix those fixtures now).

- [ ] **Step 4: Implement** — in `EndWeekModal`, after the `rentDue` line:

```tsx
        {result.economy.event === 'BOOM' && <p>Economic boom! Prices and wages have surged.</p>}
        {result.economy.event === 'CRASH' && <p>The market crashed! Prices tumble.</p>}
        {result.economy.fired && <p>You were laid off in the downturn.</p>}
        {result.economy.wageCutTo != null && <p>Your pay was cut to R{result.economy.wageCutTo}/h.</p>}
        {result.economy.bankWiped && <p>Your bank savings were wiped out.</p>}
```

- [ ] **Step 5: Run** — `npm test; npm run lint; npm run typecheck`, all green.
- [ ] **Step 6: Commit** — `feat: end-week modal reports crashes, booms, pay cuts (KAN-48)`

### Task 16: Full verify, live check, docs, PR 3

- [ ] **Step 1:** `.\mvnw -B clean verify` + full frontend suite — all green.
- [ ] **Step 2: Live check** — run API + SPA. Set your save to `round = 8, economy_reading = 85, time = 0` and end the week repeatedly (each week is a 1/31 crash + 1/31 boom roll; a handful of tries usually shows one — if impatient, verify NONE renders nothing new and trust the unit tests for the event branches; do confirm prices moved between weeks from the drift).
- [ ] **Step 3: Docs** — spec + plan live under `docs/superpowers/`; verify README's API row still reads correctly (it documents the auth/save-scoping rule, which now also covers the catalogs — no text change expected, but read it). Add a `tasks/todo.md` review note per the repo's task-management convention.
- [ ] **Step 4: Push + PR** — `gh pr create --base develop --title "feat: market crashes, booms + end-week economy report (KAN-48)"`, body noting merge order 1→2→3.

---

## Process notes (orchestrator, not the task executor)

- JIRA: transition KAN-48 → In Progress (id 21) when PR 1 opens; rewrite its description to the spec's scope (wiki model; item-DB scope moved to KAN-23); comment each PR link; → Done (id 31) when PR 3 merges.
- KAN-23 gets a comment that the G-500/G-501 item-catalogue scope now lives there.
- Rourke's approval gate applies after **every** PR before starting the next.

## Self-review notes

- Spec coverage: V7/V8 ✔ (Tasks 1, 9), price formula + drift ✔ (2, 3), charge paths food/clothes/enroll ✔ (4, 5), catalog reads ✔ (6, 11), wage snapshot + fired-clears-wage ✔ (10), crash/boom week-8/reading-80/severities/happiness ✔ (13), ordering before win check ✔ (14), end-week report + SPA ✔ (14, 15), invalidation of stale catalog caches ✔ (7, 12), ITs ✔ (8, 12), out-of-scope items untouched ✔.
- Deliberate scope holds: rent strings stay hardcoded (rent doesn't fluctuate); no trend UI; boom happiness bonus omitted (needs KAN-49 stocks — noted in `EconomyEvent` javadoc).
