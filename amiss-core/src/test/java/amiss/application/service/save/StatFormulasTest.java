package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pins the wiki formulas to the wiki's own worked examples. */
class StatFormulasTest {

    @Test
    void luckMatchesTheThreeWikiDataPoints() {
        // "Your luck starts at 43": dep 20, exp 10, 0 degrees.
        assertEquals(43, StatFormulas.luck(20, 10, 0));
        // "Taking the free Cook job immediately increases luck to 44": +2 exp on hire.
        assertEquals(44, StatFormulas.luck(20, 12, 0));
        // "All degrees guarantee at least 66%" at worst dep (0) and starting exp (10).
        assertEquals(66, StatFormulas.luck(0, 10, 11));
    }

    @Test
    void capsFollowTheWikiExamples() {
        // "Job requiring only 10 Experience + all 11 Degrees: max = 10 + 10 + 55 = 75".
        assertEquals(75, StatFormulas.maxExperience(TestSaves.CLERK, 11));
        // "Job requiring only 10 Dependibility + all 11 Degrees: max = 20 + 10 + 55 = 85".
        assertEquals(85, StatFormulas.maxDependability(TestSaves.CLERK, 11));
        // No degrees: exceed the requirement by exactly 10 / 20.
        assertEquals(30, StatFormulas.maxExperience(TestSaves.ASSISTANT, 0));
        assertEquals(50, StatFormulas.maxDependability(TestSaves.ASSISTANT, 0));
    }

    @Test
    void educationStatIsOnePlusNinePerDegree() {
        assertEquals(1, StatFormulas.educationStat(0));
        assertEquals(46, StatFormulas.educationStat(5));
        assertEquals(100, StatFormulas.educationStat(11));
    }

    @Test
    void careerStatIsOneAndAQuarterDependabilityWhileEmployed() {
        assertEquals(100, StatFormulas.careerStat(true, 80));   // wiki: 80 dep => 100 career
        assertEquals(25, StatFormulas.careerStat(true, 20));
        assertEquals(26, StatFormulas.careerStat(true, 21));    // floor(26.25)
        assertEquals(0, StatFormulas.careerStat(false, 80));    // unemployed = 0, always
    }

    @Test
    void wealthStatIsLiquidAssetsPerHundred() {
        assertEquals(0, StatFormulas.wealthStat(99, 0));
        assertEquals(10, StatFormulas.wealthStat(400, 650));
    }
}
