package amiss.domain.board;

/**
 * The game board: a 5-wide by 4-tall grid whose perimeter forms a loop of 13 stops, with the
 * bottom-middle cell {@code (3,2)} reserved for the turn timer and the inner cells left blank
 * (they hold the board art and the notification area). Laid out like a clock face: the starting
 * apartment, Low-Cost Housing, sits at top-middle {@code (0,2)} (12 o'clock) and the timer sits
 * directly opposite at bottom-middle {@code (3,2)} (6 o'clock). The remaining stops are ordered
 * clockwise from there.
 *
 * <p>This replaces the old {@code TwoDGrid} 4x4 model and the hard-coded distance maths in
 * {@code TimeService.getMulti}. Movement cost is a clean <em>ring distance</em> — the fewer of
 * the clockwise / anticlockwise steps around the loop — so the model scales to any number of
 * stops and has no special-case wrap-around branches.
 *
 * <p>Coordinates are {@code (row, col)} with {@code row} 0..3 (top to bottom) and {@code col}
 * 0..4 (left to right), matching how the player's position is stored ({@code xpos=row},
 * {@code ypos=col}). Pure domain: no Swing, no persistence.
 */
public final class Board {

    /** Grid dimensions. */
    public static final int ROWS = 4;
    public static final int COLS = 5;

    /** The timer cell (bottom-middle): not a stop. */
    public static final int TIMER_ROW = 3;
    public static final int TIMER_COL = 2;

    /**
     * The 13 stops in clockwise ring order; index i sits at {@code CELLS[i] = {row, col}}.
     * Note the loop steps straight from Hi-Tech U {@code (3,3)} to Employment Office
     * {@code (3,1)} — the timer cell {@code (3,2)} between them is not walkable.
     */
    private static final int[][] CELLS = {
        {0, 2}, // 0  Low-Cost Housing (start) — 12 o'clock
        {0, 3}, // 1  Pawn Shop
        {0, 4}, // 2  Z-Mart
        {1, 4}, // 3  Monolith Burgers
        {2, 4}, // 4  QT Clothing
        {3, 4}, // 5  Socket City
        {3, 3}, // 6  Hi-Tech U
        {3, 1}, // 7  Employment Office
        {3, 0}, // 8  Factory
        {2, 0}, // 9  Bank
        {1, 0}, // 10 Black's Market
        {0, 0}, // 11 Le Security Apartments
        {0, 1}, // 12 Rent Office
    };

    private static final Location[] STOPS = {
        Location.LOW_COST_HOUSING,
        Location.PAWN_SHOP,
        Location.Z_MART,
        Location.MONOLITH_BURGERS,
        Location.QT_CLOTHING,
        Location.SOCKET_CITY,
        Location.HI_TECH_U,
        Location.EMPLOYMENT_OFFICE,
        Location.FACTORY,
        Location.BANK,
        Location.BLACKS_MARKET,
        Location.LE_SECURITY_APARTMENTS,
        Location.RENT_OFFICE,
    };

    /** Number of stops on the loop (13). */
    public int size() {
        return STOPS.length;
    }

    /** True if {@code (row,col)} is one of the 13 stops. */
    public boolean isStop(int row, int col) {
        return ringIndex(row, col) >= 0;
    }

    /** True if {@code (row,col)} is the reserved timer cell. */
    public boolean isTimerCell(int row, int col) {
        return row == TIMER_ROW && col == TIMER_COL;
    }

    /** The stop at {@code (row,col)}, or {@code null} if that cell is not a stop. */
    public Location locationAt(int row, int col) {
        int i = ringIndex(row, col);
        return i < 0 ? null : STOPS[i];
    }

    /** The ring index (0..12) of {@code stop}; every {@link Location} is on the ring. */
    public int ringIndexOf(Location stop) {
        for (int i = 0; i < STOPS.length; i++) {
            if (STOPS[i] == stop) {
                return i;
            }
        }
        throw new IllegalArgumentException("Location not on the ring: " + stop);
    }

    /** The ring index (0..12) of {@code (row,col)}, or -1 if it is not a stop. */
    public int ringIndex(int row, int col) {
        for (int i = 0; i < CELLS.length; i++) {
            if (CELLS[i][0] == row && CELLS[i][1] == col) {
                return i;
            }
        }
        return -1;
    }

    /** The {@code {row, col}} cell of ring index {@code i}. */
    public int[] cellOf(int i) {
        return new int[]{CELLS[i][0], CELLS[i][1]};
    }

    /**
     * Walking distance in ring steps between two stops: the smaller of going clockwise or
     * anticlockwise around the loop.
     */
    public int ringDistance(int fromIndex, int toIndex) {
        int direct = Math.abs(fromIndex - toIndex);
        return Math.min(direct, size() - direct);
    }

    /**
     * Walking distance in ring steps between two cells. Returns 0 if either cell is not a stop
     * (a defensive fallback; callers move only between stops).
     */
    public int ringDistanceBetween(int fromRow, int fromCol, int toRow, int toCol) {
        int from = ringIndex(fromRow, fromCol);
        int to = ringIndex(toRow, toCol);
        if (from < 0 || to < 0) {
            return 0;
        }
        return ringDistance(from, to);
    }
}
