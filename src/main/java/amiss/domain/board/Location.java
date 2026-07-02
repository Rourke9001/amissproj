package amiss.domain.board;

/**
 * The 13 stops of the game board, in the original <em>Jones in the Fast Lane</em> loop
 * order (clockwise from the starting apartment). Each carries the display name shown to the
 * player. This is pure domain data — it knows nothing about Swing screens or the grid; the
 * {@link Board} maps each stop to a render cell and {@code presentation.ui.OpenLocation} maps
 * it to a screen.
 */
public enum Location {

    LOW_COST_HOUSING("Low-Cost Housing"),
    PAWN_SHOP("Pawn Shop"),
    Z_MART("Z-Mart"),
    MONOLITH_BURGERS("Monolith Burgers"),
    QT_CLOTHING("QT Clothing"),
    SOCKET_CITY("Socket City"),
    HI_TECH_U("Hi-Tech U"),
    EMPLOYMENT_OFFICE("Employment Office"),
    FACTORY("Factory"),
    BANK("Bank"),
    BLACKS_MARKET("Black's Market"),
    LE_SECURITY_APARTMENTS("Le Security Apartments"),
    RENT_OFFICE("Rent Office");

    private final String displayName;

    Location(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable name, e.g. {@code "Monolith Burgers"}. */
    public String displayName() {
        return displayName;
    }
}
