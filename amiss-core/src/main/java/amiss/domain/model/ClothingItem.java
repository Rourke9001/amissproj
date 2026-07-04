package amiss.domain.model;

/** QT Clothing's stock (mirrors {@code ClothesStoreGUI}'s buttons; prices in Rand). */
public enum ClothingItem {
    CASUAL("Casual Clothes", 20, 1),
    FORMAL("Formal Clothes", 35, 2),
    SUIT("Suit", 55, 3);

    private final String displayName;
    private final int price;
    private final int level;

    ClothingItem(String displayName, int price, int level) {
        this.displayName = displayName;
        this.price = price;
        this.level = level;
    }

    public String displayName() {
        return displayName;
    }

    public int price() {
        return price;
    }

    public int level() {
        return level;
    }
}
