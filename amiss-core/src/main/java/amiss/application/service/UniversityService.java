package amiss.application.service;

import amiss.application.config.ActionCosts;

/**
 * University enrollment and study progress (mirrors {@code UniversityGUI}'s handlers), so
 * Swing and the REST API share one rule source. Swing-free and constructor-injected.
 */
public class UniversityService {

    /** Enrollment fee charged once per degree (matches {@code UniversityGUI}'s "Fee: R50"). */
    public static final int ENROLL_FEE = 50;
    /** Study actions needed to complete one degree. */
    public static final int STUDIES_PER_DEGREE = 10;
    /** Education level at which every degree is complete. */
    public static final int MAX_EDUCATION = 8;
    /** The 8 degrees studied at Hi-Tech U, in level order (index = education level). */
    public static final String[] DEGREES = {
            "Junior College", "Academic", "Year 3", "Year 4",
            "Graduate School", "Post Doctoral", "Research", "Publishing"
    };

    private final EducationService education;
    private final StatsService stats;
    private final TimeService time;
    private final ActionCosts costs;

    public UniversityService(EducationService education, StatsService stats, TimeService time, ActionCosts costs) {
        this.education = education;
        this.stats = stats;
        this.time = time;
        this.costs = costs;
    }

    /**
     * Enrolls the player in their next degree, charging {@link #ENROLL_FEE}. Mirrors
     * {@code UniversityGUI.btnEnrollActionPerformed} exactly; {@link EnrollOutcome.Status#ALREADY_ENROLLED}
     * and {@link EnrollOutcome.Status#EDUCATION_COMPLETE} are defensive guards the API needs
     * but Swing never reaches (its Enroll button is hidden in both states).
     */
    public EnrollOutcome enroll() {
        if (education.getEducation() >= MAX_EDUCATION) {
            return new EnrollOutcome(EnrollOutcome.Status.EDUCATION_COMPLETE, stats.getCash());
        }
        if (education.getProg() != 0) {
            return new EnrollOutcome(EnrollOutcome.Status.ALREADY_ENROLLED, stats.getCash());
        }
        int cashBefore = stats.getCash();
        if (cashBefore < ENROLL_FEE) {
            return new EnrollOutcome(EnrollOutcome.Status.INSUFFICIENT_CASH, cashBefore);
        }
        stats.buy(Integer.toString(ENROLL_FEE));
        education.setProg(1);
        return new EnrollOutcome(EnrollOutcome.Status.OK, cashBefore - ENROLL_FEE);
    }

    /**
     * Studies toward the current degree, charging {@link ActionCosts#studyMinutes()}. Mirrors
     * {@code UniversityGUI.btnStudyActionPerformed} exactly; {@link StudyOutcome.Status#NOT_ENROLLED}
     * and {@link StudyOutcome.Status#EDUCATION_COMPLETE} are defensive guards the API needs
     * but Swing never reaches (its Study button is hidden in both states).
     */
    public StudyOutcome study() {
        int educationLevel = education.getEducation();
        if (educationLevel >= MAX_EDUCATION) {
            return new StudyOutcome(StudyOutcome.Status.EDUCATION_COMPLETE, -1, education.getProg(), educationLevel, null);
        }
        int prog = education.getProg();
        if (prog == 0) {
            return new StudyOutcome(StudyOutcome.Status.NOT_ENROLLED, -1, 0, educationLevel, null);
        }
        TimeSpend spend = time.spendMinutes(costs.studyMinutes());
        if (spend.rejected()) {
            return new StudyOutcome(StudyOutcome.Status.INSUFFICIENT_TIME, spend.remainingMinutes(), prog, educationLevel, null);
        }
        prog += 1;
        education.setProg(prog);
        if (prog == STUDIES_PER_DEGREE + 1) {
            String degreeCompleted = DEGREES[educationLevel];
            education.setEducation();
            education.setProg(0);
            return new StudyOutcome(StudyOutcome.Status.DEGREE_COMPLETED, spend.remainingMinutes(), 0,
                    educationLevel + 1, degreeCompleted);
        }
        return new StudyOutcome(StudyOutcome.Status.OK, spend.remainingMinutes(), prog, educationLevel, null);
    }
}
