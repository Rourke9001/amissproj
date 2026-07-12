package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.DegreeCatalog;
import amiss.application.port.JobCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.application.port.Turndowns;
import amiss.domain.model.SaveState;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntSupplier;

/**
 * Composition root of the save-scoped rules layer (KAN-53) — the successor of the
 * per-username {@code GameServices}. Built once over the ports plus an injectable
 * 1–100 roll so the hiring luck is deterministic under test; production callers use
 * {@link #withRandomRolls}.
 */
public class SaveGameServices {

    private final SaveRepository saves;
    private final HiringService hiring;
    private final ShiftService shifts;
    private final CourseService courses;
    private final GoalService goals;
    private final WeekRolloverService weeks;
    private final TravelService travel;
    private final BankService bank;
    private final RentService rent;
    private final ShopService shop;

    public SaveGameServices(SaveRepository saves, JobCatalog jobs, DegreeCatalog degreeCatalog,
            SaveDegrees saveDegrees, Turndowns turndowns, ActionCosts costs, IntSupplier roll1to100) {
        this.saves = saves;
        this.goals = new GoalService(saveDegrees);
        this.hiring = new HiringService(saves, jobs, saveDegrees, turndowns, costs, roll1to100);
        this.shifts = new ShiftService(saves, jobs, saveDegrees, costs);
        this.courses = new CourseService(saves, degreeCatalog, saveDegrees, costs);
        this.weeks = new WeekRolloverService(saves, goals, costs);
        this.travel = new TravelService(saves, costs);
        this.bank = new BankService(saves);
        this.rent = new RentService(saves, costs);
        this.shop = new ShopService(saves, costs);
    }

    /** Production wiring: a uniform 1–100 roll. */
    public static SaveGameServices withRandomRolls(SaveRepository saves, JobCatalog jobs,
            DegreeCatalog degreeCatalog, SaveDegrees saveDegrees, Turndowns turndowns,
            ActionCosts costs) {
        Random random = new Random();
        return new SaveGameServices(saves, jobs, degreeCatalog, saveDegrees, turndowns, costs,
                () -> random.nextInt(100) + 1);
    }

    public Optional<SaveState> load(long saveId) {
        return saves.find(saveId);
    }

    public HiringService hiring() {
        return hiring;
    }

    public ShiftService shifts() {
        return shifts;
    }

    public CourseService courses() {
        return courses;
    }

    public GoalService goals() {
        return goals;
    }

    public WeekRolloverService weeks() {
        return weeks;
    }

    public TravelService travel() {
        return travel;
    }

    public BankService bank() {
        return bank;
    }

    public RentService rent() {
        return rent;
    }

    public ShopService shop() {
        return shop;
    }
}
