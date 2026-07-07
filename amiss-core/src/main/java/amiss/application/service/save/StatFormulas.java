package amiss.application.service.save;

import amiss.domain.model.JobSpec;

/**
 * The wiki-exact stat formulas (KAN-53), each verified against the wiki's worked
 * examples (see the milestone spec). Kept in one static place so hiring, shifts,
 * graduation and goals can never disagree.
 */
public final class StatFormulas {

    private StatFormulas() {
    }

    /** Experience cap while holding {@code job}; +5 permanently per degree. */
    public static int maxExperience(JobSpec job, int degrees) {
        return 10 + job.reqExperience() + 5 * degrees;
    }

    /** Dependability cap while holding {@code job}; +5 permanently per degree. */
    public static int maxDependability(JobSpec job, int degrees) {
        return 20 + job.reqDependability() + 5 * degrees;
    }

    /**
     * The Employment Office luck score a 1–100 roll must not exceed. Grouping was
     * reconstructed from the wiki's three data points: start luck 43 (dep 20, exp 10,
     * 0 degrees), 44 after taking Cook (exp 12), and the guaranteed 66 floor with all
     * 11 degrees at worst dep/exp (0/10).
     */
    public static int luck(int dependability, int experience, int degrees) {
        return 30 + (10 + dependability + experience + 8 * degrees) / 3;
    }

    /** Education stat: 1 + 9 per degree (100 at all 11). */
    public static int educationStat(int degrees) {
        return 1 + 9 * degrees;
    }

    /** Career stat: 1.25 × dependability while employed, 0 while unemployed. */
    public static int careerStat(boolean employed, int dependability) {
        return employed ? (dependability * 5) / 4 : 0;
    }

    /** Wealth stat: liquid assets (cash + bank) per R100. */
    public static int wealthStat(int cash, int bank) {
        return (cash + bank) / 100;
    }
}
