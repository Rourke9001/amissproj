package amiss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.repository.UserRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link FoodService} with the user repository mocked.
 */
@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;

    private FoodService newService() {
        return new FoodService(users, USER);
    }

    // ---- setFood: the "buy one when you have none" special case ------------

    @Test
    void setFood_buyingOneWhenEmptyStoresExactlyOne() throws SQLException {
        when(users.getEat(USER)).thenReturn(0);
        newService().setFood(1);
        // (eat==0 || eat==1) && count==1 -> clamps to 1 rather than adding.
        verify(users).updateEat(USER, 1);
    }

    @Test
    void setFood_buyingOneWhenHoldingOneStaysAtOne() throws SQLException {
        when(users.getEat(USER)).thenReturn(1);
        newService().setFood(1);
        verify(users).updateEat(USER, 1);
    }

    @Test
    void setFood_buyingMoreThanOneAddsToTheStore() throws SQLException {
        when(users.getEat(USER)).thenReturn(5);
        newService().setFood(2);
        verify(users).updateEat(USER, 7);
    }

    // ---- getFood -----------------------------------------------------------

    @Test
    void getFood_returnsTheStoredAmount() throws SQLException {
        when(users.getEat(USER)).thenReturn(8);
        assertEquals(8, newService().getFood());
    }

    @Test
    void getFood_returnsZeroOnSqlException() throws SQLException {
        when(users.getEat(USER)).thenThrow(new SQLException("boom"));
        assertEquals(0, newService().getFood());
    }

    // ---- getEat: did the player eat last round? ----------------------------

    @Test
    void getEat_isFalseAndConsumesNothingWhenStoreIsZero() throws SQLException {
        when(users.getEat(USER)).thenReturn(0);
        assertFalse(newService().getEat());
        verify(users, never()).updateEat(anyString(), anyInt());
    }

    @Test
    void getEat_isTrueAndConsumesOneWhenStoreIsPositive() throws SQLException {
        when(users.getEat(USER)).thenReturn(3);
        assertTrue(newService().getEat());
        // "ate" -> setFood(-1) decrements the store: 3 + (-1) = 2.
        verify(users).updateEat(USER, 2);
    }

    @Test
    void getEat_isFalseWhenNoRowExists() throws SQLException {
        when(users.getEat(USER)).thenReturn(-1);
        assertFalse(newService().getEat());
        verify(users, never()).updateEat(anyString(), anyInt());
    }
}
