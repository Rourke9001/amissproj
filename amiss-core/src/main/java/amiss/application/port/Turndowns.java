package amiss.application.port;

/**
 * Port over {@code tblsave_turndowns} — jobs refused with "No Openings" (KAN-53).
 * A block only counts while the stored round equals the save's current round.
 */
public interface Turndowns {

    boolean isTurnedDown(long saveId, int jobId, int round);

    /** Records (or refreshes) a turndown for the given round. */
    void record(long saveId, int jobId, int round);
}
