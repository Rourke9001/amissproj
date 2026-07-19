package amiss.domain.model;

import amiss.domain.board.Location;

/**
 * A purchasable appliance/book (KAN-23) — minimal ownership plumbing only: no
 * browsing catalog, no break/repair, one canonical price and store per item (the
 * full appliances catalog, including Z-Mart's cheaper used variants, is KAN-58).
 */
public enum ApplianceItem {
    FRIDGE(876, 1, Location.SOCKET_CITY),
    FREEZER(513, 2, Location.SOCKET_CITY),
    COMPUTER(1599, 3, Location.SOCKET_CITY),
    ENCYCLOPEDIA(475, 0, Location.Z_MART),
    DICTIONARY(70, 0, Location.Z_MART),
    ATLAS(55, 0, Location.Z_MART);

    private final int basePrice;
    private final int firstOwnedHappiness;
    private final Location store;

    ApplianceItem(int basePrice, int firstOwnedHappiness, Location store) {
        this.basePrice = basePrice;
        this.firstOwnedHappiness = firstOwnedHappiness;
        this.store = store;
    }

    public int basePrice() {
        return basePrice;
    }

    /** Happiness granted the first time this save ever owns one; 0 on every later purchase. */
    public int firstOwnedHappiness() {
        return firstOwnedHappiness;
    }

    public Location store() {
        return store;
    }
}
