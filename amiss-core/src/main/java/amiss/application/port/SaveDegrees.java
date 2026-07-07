package amiss.application.port;

import java.util.Set;

/** Port over {@code tblsave_degrees} — the degrees a save has earned (KAN-53). */
public interface SaveDegrees {

    Set<Integer> earned(long saveId);

    /** Records a graduation; degrees can never be lost, so there is no removal. */
    void award(long saveId, int degreeId);
}
