package amiss.domain.model;

/** The Monolith Burgers menu (mirrors {@code FastFoodGUI}'s buttons; prices in Rand). */
public enum FastFoodItem {
    BURGER("Burger", 32),
    CHEESE_BURGER("Cheese Burger", 36),
    DOUBLE_PATTY_CHEESE("Double Patty Cheese", 40),
    MILKSHAKE("Milkshake", 22),
    FRIES("Fries", 20),
    FAMILY_MEAL("Family Meal", 50);

    private final String displayName;
    private final int price;

    FastFoodItem(String displayName, int price) {
        this.displayName = displayName;
        this.price = price;
    }

    public String displayName() {
        return displayName;
    }

    public int price() {
        return price;
    }
}
