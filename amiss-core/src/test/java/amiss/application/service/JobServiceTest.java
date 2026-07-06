package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link JobService}. The two repositories and the collaborating
 * {@link EducationService} are mocked, so the eligibility / earnings / dress-code rules
 * are exercised in isolation. {@code time} is a real {@link TimeService} over the same
 * mocked {@link UserRepository} (mirrors {@link StatsServiceOrchestrationTest}'s pattern),
 * since {@link JobService#apply(String)} needs a genuine clock.
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
        return new JobService(jobs, users, education, new TimeService(users, USER), ActionCosts.defaults(), USER);
    }

    // ---- applyForJob -------------------------------------------------------

    @Test
    void applyForJob_rejectsAndDoesNotPersistWhenEducationIsTooLow() {
        when(jobs.getRequiredEducation("Doctor")).thenReturn(5);
        when(education.getEducation()).thenReturn(2);

        assertEquals("not enough education", newService().applyForJob("Doctor"));
        verify(users, never()).updateJob(anyString(), anyString());
    }

    @Test
    void applyForJob_hiresAndPersistsWhenEducationIsSufficient() {
        when(jobs.getRequiredEducation("Janitor")).thenReturn(0);
        when(education.getEducation()).thenReturn(1);
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobs.getSalary("Janitor")).thenReturn(20);

        assertEquals(
                "Well Done! You Got The Job, You will earn R20 for every hour you Work!",
                newService().applyForJob("Janitor"));
        verify(users).updateJob(USER, "Janitor");
    }

    // ---- apply ---------------------------------------------------------------

    @Test
    void apply_unknownJobIsRejectedBeforeAnyCharge() {
        when(jobs.getRequiredEducation("Bogus")).thenReturn(-1);

        ApplyOutcome outcome = newService().apply("Bogus");

        assertEquals(new ApplyOutcome(ApplyOutcome.Status.UNKNOWN_JOB, -1, "Bogus", -1), outcome);
        verify(users, never()).updateTime(anyString(), anyInt());
        verify(users, never()).updateJob(anyString(), anyString());
    }

    @Test
    void apply_weekOverRejectsAndChargesNothing() {
        when(jobs.getRequiredEducation("Janitor")).thenReturn(0);
        when(users.getTime(USER)).thenReturn(0);

        ApplyOutcome outcome = newService().apply("Janitor");

        assertEquals(new ApplyOutcome(ApplyOutcome.Status.WEEK_OVER, 0, "Janitor", -1), outcome);
        verify(users, never()).updateJob(anyString(), anyString());
    }

    @Test
    void apply_insufficientTimeRejectsAndChargesNothing() {
        when(jobs.getRequiredEducation("Janitor")).thenReturn(0);
        when(users.getTime(USER)).thenReturn(100); // 100 - 240 < 0

        ApplyOutcome outcome = newService().apply("Janitor");

        assertEquals(new ApplyOutcome(ApplyOutcome.Status.INSUFFICIENT_TIME, 100, "Janitor", -1), outcome);
        verify(users, never()).updateJob(anyString(), anyString());
    }

    @Test
    void apply_chargesTheFourHoursEvenWhenEducationIsTooLow() {
        when(jobs.getRequiredEducation("Doctor")).thenReturn(5);
        when(users.getTime(USER)).thenReturn(4320);
        when(education.getEducation()).thenReturn(2);

        ApplyOutcome outcome = newService().apply("Doctor");

        assertEquals(new ApplyOutcome(ApplyOutcome.Status.INSUFFICIENT_EDUCATION, 4080, "Doctor", -1), outcome);
        verify(users).updateTime(USER, 4080); // the 4h is charged even on rejection (game rule)
        verify(users, never()).updateJob(anyString(), anyString());
    }

    @Test
    void apply_hiresAndPersistsWhenEducationIsSufficient() {
        when(jobs.getRequiredEducation("Janitor")).thenReturn(0);
        when(users.getTime(USER)).thenReturn(4320);
        when(education.getEducation()).thenReturn(1);
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobs.getSalary("Janitor")).thenReturn(20);

        ApplyOutcome outcome = newService().apply("Janitor");

        assertEquals(new ApplyOutcome(ApplyOutcome.Status.HIRED, 4080, "Janitor", 20), outcome);
        verify(users).updateJob(USER, "Janitor");
    }

    @Test
    void apply_returnsFailedOnSqlExceptionCheckingEducation() {
        when(jobs.getRequiredEducation("Janitor")).thenThrow(new PersistenceFailureException(new SQLException("boom")));

        ApplyOutcome outcome = newService().apply("Janitor");

        assertEquals(new ApplyOutcome(ApplyOutcome.Status.FAILED, -1, "Janitor", -1), outcome);
        verify(users, never()).updateJob(anyString(), anyString());
    }

    // ---- getEarnings / getLocation -----------------------------------------

    @Test
    void getEarnings_returnsTheSalaryForTheCurrentJob() {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobs.getSalary("Pilot")).thenReturn(100);
        assertEquals(100, newService().getEarnings());
    }

    @Test
    void getEarnings_returnsMinusOneOnSqlException() {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobs.getSalary("Pilot")).thenThrow(new PersistenceFailureException(new SQLException("boom")));
        assertEquals(-1, newService().getEarnings());
    }

    @Test
    void getLocation_returnsTheBuildingForTheCurrentJob() {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobs.getLocation("Pilot")).thenReturn("Airport");
        assertEquals("Airport", newService().getLocation());
    }

    // ---- getJobClothes: dress-code check -----------------------------------

    @Test
    void getJobClothes_warnsWhenUnderdressed() {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobs.getRequiredClothing("Pilot")).thenReturn("3");
        when(users.getUserClothing(USER)).thenReturn("1");
        assertEquals("You are not properly dressed for work", newService().getJobClothes());
    }

    @Test
    void getJobClothes_returnsNullWhenProperlyDressed() {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobs.getRequiredClothing("Janitor")).thenReturn("1");
        when(users.getUserClothing(USER)).thenReturn("2");
        assertNull(newService().getJobClothes());
    }

    @Test
    void getJobClothes_returnsNullWhenTheJobHasNoClothingRequirement() {
        when(users.getJob(USER)).thenReturn("Unemployed");
        when(jobs.getRequiredClothing("Unemployed")).thenReturn(null);
        assertNull(newService().getJobClothes());
    }

    // ---- setClothes / toString ---------------------------------------------

    @Test
    void setClothes_persistsTheClothingLevel() {
        newService().setClothes(3);
        verify(users).updateClothing(USER, 3);
    }

    @Test
    void toString_describesJobAndEarnings() {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobs.getSalary("Janitor")).thenReturn(20);
        assertEquals("You work as a Janitor and Earn R20", newService().toString());
    }
}
