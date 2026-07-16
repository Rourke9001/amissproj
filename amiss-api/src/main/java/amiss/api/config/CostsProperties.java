package amiss.api.config;

import amiss.application.config.ActionCosts;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring binding for the action cost table (KAN-29): any {@code amiss.costs.*} property —
 * e.g. {@code amiss.costs.work-minutes=300} — overrides the matching {@link ActionCosts}
 * default, so game balance is tunable per deployment without a rebuild. Unset values fall
 * back to {@link ActionCosts#defaults()}.
 */
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

    /** The bound overrides merged over the built-in defaults. */
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
