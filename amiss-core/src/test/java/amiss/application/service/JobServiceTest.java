package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link JobService}. The two repositories and the collaborating
 * {@link EducationService} are mocked, so the eligibility / earnings / dress-code rules
 * are exercised in isolation.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final String USER = "bob";

    @Mock
    private JobRepository jobs;
    @Mock
    private UserRepository users;
    @Mock
    private EducationService education;

    private JobService newService() {
        return new JobService(jobs, users, education, USER);
    }

    // ---- applyForJob -------------------------------------------------------

    @Test
    void applyForJob_rejectsAndDoesNotPersistWhenEducationIsTooLow() throws SQLException {
        when(jobs.getRequiredEducation("Doctor")).thenReturn(5);
        when(education.getEducation()).thenReturn(2);

        assertEquals("not enough education", newService().applyForJob("Doctor"));
        verify(users, never()).updateJob(anyString(), anyString());
    }

    @Test
    void applyForJob_hiresAndPersistsWhenEducationIsSufficient() throws SQLException {
        when(jobs.getRequiredEducation("Janitor")).thenReturn(0);
        when(education.getEducation()).thenReturn(1);
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobs.getSalary("Janitor")).thenReturn(20);

        assertEquals(
                "Well Done! You Got The Job, You will earn R20 for every hour you Work!",
                newService().applyForJob("Janitor"));
        verify(users).updateJob(USER, "Janitor");
    }

    // ---- getEarnings / getLocation -----------------------------------------

    @Test
    void getEarnings_returnsTheSalaryForTheCurrentJob() throws SQLException {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobs.getSalary("Pilot")).thenReturn(100);
        assertEquals(100, newService().getEarnings());
    }

    @Test
    void getEarnings_returnsMinusOneOnSqlException() throws SQLException {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobs.getSalary("Pilot")).thenThrow(new SQLException("boom"));
        assertEquals(-1, newService().getEarnings());
    }

    @Test
    void getLocation_returnsTheBuildingForTheCurrentJob() throws SQLException {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobs.getLocation("Pilot")).thenReturn("Airport");
        assertEquals("Airport", newService().getLocation());
    }

    // ---- getJobClothes: dress-code check -----------------------------------

    @Test
    void getJobClothes_warnsWhenUnderdressed() throws SQLException {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobs.getRequiredClothing("Pilot")).thenReturn("3");
        when(users.getUserClothing(USER)).thenReturn("1");
        assertEquals("You are not properly dressed for work", newService().getJobClothes());
    }

    @Test
    void getJobClothes_returnsNullWhenProperlyDressed() throws SQLException {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobs.getRequiredClothing("Janitor")).thenReturn("1");
        when(users.getUserClothing(USER)).thenReturn("2");
        assertNull(newService().getJobClothes());
    }

    @Test
    void getJobClothes_returnsNullWhenTheJobHasNoClothingRequirement() throws SQLException {
        when(users.getJob(USER)).thenReturn("Unemployed");
        when(jobs.getRequiredClothing("Unemployed")).thenReturn(null);
        assertNull(newService().getJobClothes());
    }

    // ---- setClothes / toString ---------------------------------------------

    @Test
    void setClothes_persistsTheClothingLevel() throws SQLException {
        newService().setClothes(3);
        verify(users).updateClothing(USER, 3);
    }

    @Test
    void toString_describesJobAndEarnings() throws SQLException {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobs.getSalary("Janitor")).thenReturn(20);
        assertEquals("You work as a Janitor and Earn R20", newService().toString());
    }
}
