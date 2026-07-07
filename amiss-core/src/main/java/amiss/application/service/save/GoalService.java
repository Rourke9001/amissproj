package amiss.application.service.save;

import amiss.application.port.SaveDegrees;
import amiss.domain.model.SaveState;

/**
 * Win-goal progress per the wiki Goals rules (KAN-53): Wealth = (cash+bank)/100,
 * Happiness = the happiness stat, Education = 1 + 9×degrees, Career = 1.25 ×
 * dependability (0 while unemployed). Targets are per-save, chosen at creation.
 */
public class GoalService {

    /** One goal's standing. */
    public record GoalProgress(int current, int target) {
        public boolean met() {
            return current >= target;
        }
    }

    /** All four; the win condition is all four met at a week rollover. */
    public record GoalsProgress(GoalProgress wealth, GoalProgress happiness,
            GoalProgress education, GoalProgress career) {
        public boolean allMet() {
            return wealth.met() && happiness.met() && education.met() && career.met();
        }
    }

    private final SaveDegrees degrees;

    public GoalService(SaveDegrees degrees) {
        this.degrees = degrees;
    }

    public GoalsProgress progress(SaveState save) {
        int degreeCount = degrees.earned(save.id()).size();
        return new GoalsProgress(
                new GoalProgress(StatFormulas.wealthStat(save.cash(), save.bank()), save.goalWealth()),
                new GoalProgress(save.happiness(), save.goalHappiness()),
                new GoalProgress(StatFormulas.educationStat(degreeCount), save.goalEducation()),
                new GoalProgress(StatFormulas.careerStat(save.employed(), save.dependability()),
                        save.goalCareer()));
    }
}
