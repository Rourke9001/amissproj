package amiss.domain.model;

/**
 * QT Clothing's stock (KAN-23, wiki-exact prices/durability). Three independent
 * categories — Casual, Dress, Business — each with its own weeks-remaining counter on
 * {@code SaveState}; buying adds to the category rather than replacing it. Z-Mart's
 * cheaper, shorter-lived line and its lack of a happiness bonus are KAN-58.
 */
public enum ClothingItem {
    CASUAL("Casual Clothes", 73, 1, 11, 0),
    DRESS("Dress Clothes", 125, 2, 13, 1),
    BUSINESS("Business Suit", 295, 3, 13, 2);

    private final String displayName;
    private final int price;
    private final int level;
    private final int weeks;
    private final int happinessPerPurchase;

    ClothingItem(String displayName, int price, int level, int weeks, int happinessPerPurchase) {
        this.displayName = displayName;
        this.price = price;
        this.level = level;
        this.weeks = weeks;
        this.happinessPerPurchase = happinessPerPurchase;
    }

    public String displayName() {
        return displayName;
    }

    public int price() {
        return price;
    }

    /** Ordinal matching {@code JobSpec.reqClothing}: 1 = Casual, 2 = Dress, 3 = Business. */
    public int level() {
        return level;
    }

    public int weeks() {
        return weeks;
    }

    /** Happiness on every purchase (wiki: QT only; Casual grants none). */
    public int happinessPerPurchase() {
        return happinessPerPurchase;
    }
}
