package amiss.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the shop catalog enums (mirrors {@link amiss.application.config.ActionCostsTest}'s
 * simplicity): the design values Swing's menus and the future API both read prices/levels
 * from.
 */
class CatalogsTest {

    @Test
    void fastFoodItem_matchesMonolithBurgersMenu() {
        assertEquals("Burger", FastFoodItem.BURGER.displayName());
        assertEquals(32, FastFoodItem.BURGER.price());
        assertEquals("Cheese Burger", FastFoodItem.CHEESE_BURGER.displayName());
        assertEquals(36, FastFoodItem.CHEESE_BURGER.price());
        assertEquals("Double Patty Cheese", FastFoodItem.DOUBLE_PATTY_CHEESE.displayName());
        assertEquals(40, FastFoodItem.DOUBLE_PATTY_CHEESE.price());
        assertEquals("Milkshake", FastFoodItem.MILKSHAKE.displayName());
        assertEquals(22, FastFoodItem.MILKSHAKE.price());
        assertEquals("Fries", FastFoodItem.FRIES.displayName());
        assertEquals(20, FastFoodItem.FRIES.price());
        assertEquals("Family Meal", FastFoodItem.FAMILY_MEAL.displayName());
        assertEquals(50, FastFoodItem.FAMILY_MEAL.price());
    }

    @Test
    void foodPack_matchesBlacksMarketPacks() {
        assertEquals(25, FoodPack.ONE_WEEK.price());
        assertEquals(1, FoodPack.ONE_WEEK.weeks());
        assertEquals(48, FoodPack.TWO_WEEKS.price());
        assertEquals(2, FoodPack.TWO_WEEKS.weeks());
        assertEquals(90, FoodPack.FOUR_WEEKS.price());
        assertEquals(4, FoodPack.FOUR_WEEKS.weeks());
        assertEquals(140, FoodPack.EIGHT_WEEKS.price());
        assertEquals(8, FoodPack.EIGHT_WEEKS.weeks());
        assertEquals("1 Weeks of Food", FoodPack.ONE_WEEK.displayName());
    }

    @Test
    void clothingItem_matchesQtClothingStock() {
        assertEquals("Casual Clothes", ClothingItem.CASUAL.displayName());
        assertEquals(73, ClothingItem.CASUAL.price());
        assertEquals(1, ClothingItem.CASUAL.level());
        assertEquals(11, ClothingItem.CASUAL.weeks());
        assertEquals(0, ClothingItem.CASUAL.happinessPerPurchase());
        assertEquals("Dress Clothes", ClothingItem.DRESS.displayName());
        assertEquals(125, ClothingItem.DRESS.price());
        assertEquals(2, ClothingItem.DRESS.level());
        assertEquals(13, ClothingItem.DRESS.weeks());
        assertEquals(1, ClothingItem.DRESS.happinessPerPurchase());
        assertEquals("Business Suit", ClothingItem.BUSINESS.displayName());
        assertEquals(295, ClothingItem.BUSINESS.price());
        assertEquals(3, ClothingItem.BUSINESS.level());
        assertEquals(13, ClothingItem.BUSINESS.weeks());
        assertEquals(2, ClothingItem.BUSINESS.happinessPerPurchase());
    }
}
