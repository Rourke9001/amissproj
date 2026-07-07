package amiss.domain.model;

/**
 * One row of the {@code tbljob} catalog (KAN-53). {@code reqDependability} is the
 * <em>listed</em> value; a listed 10 truly requires 0 (wiki anti-frustration rule) —
 * apply {@link #effectiveReqDependability()} when gating.
 */
public record JobSpec(int id, String name, String location, int wage,
        int reqExperience, int reqDependability, int reqClothing) {

    /** The 10-means-0 wiki rule, in one place. */
    public int effectiveReqDependability() {
        return reqDependability == 10 ? 0 : reqDependability;
    }

    /** Anyone can get the Cook job at Monolith Burgers, whatever their stats. */
    public boolean alwaysHired() {
        return "Cook".equals(name) && "Monolith Burgers".equals(location);
    }
}
