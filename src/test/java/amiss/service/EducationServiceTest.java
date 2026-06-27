package amiss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.repository.UserStatsRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EducationService} with the stats repository mocked.
 */
@ExtendWith(MockitoExtension.class)
class EducationServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserStatsRepository stats;

    private EducationService newService() {
        return new EducationService(stats, USER);
    }

    @Test
    void getEducation_returnsTheStoredLevel() throws SQLException {
        when(stats.getEducation(USER)).thenReturn(3);
        assertEquals(3, newService().getEducation());
    }

    @Test
    void getEducation_returnsMinusOneOnSqlException() throws SQLException {
        when(stats.getEducation(USER)).thenThrow(new SQLException("boom"));
        assertEquals(-1, newService().getEducation());
    }

    @Test
    void setEducation_advancesTheStoredLevelByOne() throws SQLException {
        when(stats.getEducation(USER)).thenReturn(2);
        newService().setEducation();
        verify(stats).updateEducation(USER, 3);
    }

    @Test
    void hasEnrolled_isFalseWhenProgressIsZero() throws SQLException {
        when(stats.getEduprog(USER)).thenReturn(0);
        assertFalse(newService().hasEnrolled());
    }

    @Test
    void hasEnrolled_isTrueWhenProgressIsOne() throws SQLException {
        when(stats.getEduprog(USER)).thenReturn(1);
        assertTrue(newService().hasEnrolled());
    }

    @Test
    void hasEnrolled_isFalseForAnyOtherProgressValue() throws SQLException {
        when(stats.getEduprog(USER)).thenReturn(2);
        assertFalse(newService().hasEnrolled());
    }

    @Test
    void getProg_returnsMinusOneOnSqlException() throws SQLException {
        when(stats.getEduprog(USER)).thenThrow(new SQLException("boom"));
        assertEquals(-1, newService().getProg());
    }

    @Test
    void setProg_persistsTheGivenProgress() throws SQLException {
        newService().setProg(1);
        verify(stats).updateEduprog(USER, 1);
    }
}
