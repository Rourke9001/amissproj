package amiss.domain.model;

/** Grocery packs at Black's Market (mirrors {@code MarketGUI}'s buttons; prices in Rand). */
public enum FoodPack {
    ONE_WEEK(25, 1),
    TWO_WEEKS(48, 2),
    FOUR_WEEKS(90, 4),
    EIGHT_WEEKS(140, 8);

    private final int price;
    private final int weeks;

    FoodPack(int price, int weeks) {
        this.price = price;
        this.weeks = weeks;
    }

    /** Matches {@code MarketGUI}'s label text verbatim (e.g. {@code "1 Weeks of Food"}). */
    public String displayName() {
        return weeks + " Weeks of Food";
    }

    public int price() {
        return price;
    }

    public int weeks() {
        return weeks;
    }
}
