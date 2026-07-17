package amiss.application.service.save;

import amiss.domain.model.JobSpec;
import amiss.domain.model.SaveState;

/** Shared fixtures: a fresh save with new-game defaults, and catalog rows used across tests. */
final class TestSaves {

    static final long SAVE_ID = 7L;

    /** Cook @ Monolith Burgers — the always-hired job (catalog id 4). */
    static final JobSpec COOK = new JobSpec(4, "Cook", "Monolith Burgers", 5, 0, 10, 1);
    /** Z-Mart Clerk — listed req dep 10 (= effective 0), req exp 10, no degrees. */
    static final JobSpec CLERK = new JobSpec(1, "Clerk", "Z-Mart", 5, 10, 10, 1);
    /** Monolith Assistant Manager — req exp 20 / dep 30, no degrees. */
    static final JobSpec ASSISTANT = new JobSpec(6, "Assistant Manager", "Monolith Burgers", 7, 20, 30, 1);
    /** Bank Broker — req exp 70 / dep 70 / Business Admin (3) + Academic (4) / Business dress. */
    static final JobSpec BROKER = new JobSpec(32, "Broker", "Bank", 22, 70, 70, 3);

    private TestSaves() {
    }

    /**
     * A save at the top of a fresh week: 3600 min (the flat 60h week — KAN-23), round 1,
     * R100, exp 10 / dep 20, happiness 50, no job.
     */
    static SaveState newSave() {
        return new SaveState(SAVE_ID, "tester", "Save 1", 0, 0, 3600, 1, 100, 0, 0, 1, 0, 1,
                null, 50, 10, 20, null, 0, 50, 50, 50, 50, false, (byte) 0, (short) 0, null);
    }
}
