package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.DegreeCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.domain.model.DegreeSpec;
import amiss.domain.model.SaveState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Hi-Tech U over the 11-degree tree (KAN-53). Enrolling targets a specific AVAILABLE
 * course (prerequisite earned, not already earned, not already enrolled elsewhere) and
 * charges the R50 fee; ten study sessions graduate it. Graduation awards the degree
 * (never lost), +5 dependability — deliberately allowed over the cap; the weekly −3
 * decay erodes it — and unlocks the courses it gates.
 */
public class CourseService {

    /** The base fee — the charged fee scales with the economy (KAN-48). */
    public static final int ENROLL_FEE = 50;
    public static final int STUDIES_PER_DEGREE = 10;
    private static final int GRADUATION_DEPENDABILITY_BONUS = 5;

    public enum CourseStatus {
        EARNED, AVAILABLE, LOCKED
    }

    /** One row of the course board: the degree, its gate, and this save's standing. */
    public record CourseView(DegreeSpec degree, CourseStatus status, boolean enrolled, int studiesDone) {
    }

    public record EnrollResult(Status status, int cash, int feePaid) {
        public enum Status {
            OK, UNKNOWN_DEGREE, ALREADY_EARNED, LOCKED, ALREADY_ENROLLED, INSUFFICIENT_CASH
        }
    }

    public record StudyResult(Status status, int studiesDone, int remainingMinutes, String degreeCompleted) {
        public enum Status {
            OK, GRADUATED, NOT_ENROLLED, WEEK_OVER
        }
    }

    private final SaveRepository saves;
    private final DegreeCatalog catalog;
    private final SaveDegrees degrees;
    private final ActionCosts costs;
    private final EconomyService economy;

    public CourseService(SaveRepository saves, DegreeCatalog catalog, SaveDegrees degrees,
            ActionCosts costs, EconomyService economy) {
        this.saves = saves;
        this.catalog = catalog;
        this.degrees = degrees;
        this.costs = costs;
        this.economy = economy;
    }

    /** The whole course board for this save, in catalog order. */
    public List<CourseView> courses(SaveState save) {
        Set<Integer> earned = degrees.earned(save.id());
        List<CourseView> board = new ArrayList<>();
        for (DegreeSpec degree : catalog.all()) {
            CourseStatus status;
            if (earned.contains(degree.id())) {
                status = CourseStatus.EARNED;
            } else if (degree.prereqDegreeId() == null || earned.contains(degree.prereqDegreeId())) {
                status = CourseStatus.AVAILABLE;
            } else {
                status = CourseStatus.LOCKED;
            }
            boolean enrolled = save.currentCourseId() != null && save.currentCourseId() == degree.id();
            board.add(new CourseView(degree, status, enrolled, enrolled ? save.eduprog() : 0));
        }
        return board;
    }

    public EnrollResult enroll(SaveState save, int degreeId) {
        Optional<DegreeSpec> found = catalog.byId(degreeId);
        int fee = economy.price(ENROLL_FEE, save);
        if (found.isEmpty()) {
            return new EnrollResult(EnrollResult.Status.UNKNOWN_DEGREE, save.cash(), fee);
        }
        DegreeSpec degree = found.get();
        Set<Integer> earned = degrees.earned(save.id());
        if (earned.contains(degree.id())) {
            return new EnrollResult(EnrollResult.Status.ALREADY_EARNED, save.cash(), fee);
        }
        if (degree.prereqDegreeId() != null && !earned.contains(degree.prereqDegreeId())) {
            return new EnrollResult(EnrollResult.Status.LOCKED, save.cash(), fee);
        }
        if (save.currentCourseId() != null) {
            return new EnrollResult(EnrollResult.Status.ALREADY_ENROLLED, save.cash(), fee);
        }
        if (save.cash() < fee) {
            return new EnrollResult(EnrollResult.Status.INSUFFICIENT_CASH, save.cash(), fee);
        }
        save.setCash(save.cash() - fee);
        save.setCurrentCourseId(degree.id());
        save.setEduprog(0);
        saves.update(save);
        return new EnrollResult(EnrollResult.Status.OK, save.cash(), fee);
    }

    public StudyResult study(SaveState save) {
        if (save.currentCourseId() == null) {
            return new StudyResult(StudyResult.Status.NOT_ENROLLED, 0, save.timeMinutes(), null);
        }
        if (save.weekOver()) {
            return new StudyResult(StudyResult.Status.WEEK_OVER, save.eduprog(), 0, null);
        }
        save.spendUpTo(costs.studyMinutes());
        int done = save.eduprog() + 1;
        if (done >= STUDIES_PER_DEGREE) {
            int degreeId = save.currentCourseId();
            String name = catalog.byId(degreeId).map(DegreeSpec::name).orElse("degree");
            degrees.award(save.id(), degreeId);
            save.setCurrentCourseId(null);
            save.setEduprog(0);
            save.setDependability(save.dependability() + GRADUATION_DEPENDABILITY_BONUS);
            saves.update(save);
            return new StudyResult(StudyResult.Status.GRADUATED, STUDIES_PER_DEGREE,
                    save.timeMinutes(), name);
        }
        save.setEduprog(done);
        saves.update(save);
        return new StudyResult(StudyResult.Status.OK, done, save.timeMinutes(), null);
    }
}
