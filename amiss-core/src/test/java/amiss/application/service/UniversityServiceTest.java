package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link UniversityService}, extracted verbatim from
 * {@code UniversityGUI.btnEnrollActionPerformed} / {@code btnStudyActionPerformed}. Wired
 * like {@link StatsServiceOrchestrationTest}: real collaborator services over mocked
 * repositories, mirroring the {@link GameServices} composition. {@code ALREADY_ENROLLED} /
 * {@code EDUCATION_COMPLETE} / {@code NOT_ENROLLED} are defensive guards Swing's button
 * visibility never reaches, but are still pinned here for the future API.
 */
@ExtendWith(MockitoExtension.class)
class UniversityServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;
    @Mock
    private UserStatsRepository userStats;
    @Mock
    private JobRepository jobRepo;

    private UniversityService service;

    @BeforeEach
    void wireRealCollaboratorsOverMockedRepositories() {
        EducationService education = new EducationService(userStats, USER);
        TimeService time = new TimeService(users, USER);
        JobService jobs = new JobService(jobRepo, users, education, time, ActionCosts.defaults(), USER);
        FoodService food = new FoodService(users, USER);
        StatsService stats = new StatsService(users, userStats, jobs, time, food, USER);
        service = new UniversityService(education, stats, time, ActionCosts.defaults());
    }

    // ---- enroll ----------------------------------------------------------------

    @Test
    void enroll_chargesTheFeeAndStartsProgress() {
        when(userStats.getEducation(USER)).thenReturn(0);
        when(userStats.getEduprog(USER)).thenReturn(0);
        when(users.getCash(USER)).thenReturn(100);

        EnrollOutcome outcome = service.enroll();

        assertEquals(new EnrollOutcome(EnrollOutcome.Status.OK, 50), outcome);
        verify(users).updateCash(USER, 50);
        verify(userStats).updateEduprog(USER, 1);
    }

    @Test
    void enroll_insufficientCashRejectsAndChargesNothing() {
        when(userStats.getEducation(USER)).thenReturn(0);
        when(userStats.getEduprog(USER)).thenReturn(0);
        when(users.getCash(USER)).thenReturn(10); // 10 < fee 50

        EnrollOutcome outcome = service.enroll();

        assertEquals(new EnrollOutcome(EnrollOutcome.Status.INSUFFICIENT_CASH, 10), outcome);
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(userStats, never()).updateEduprog(anyString(), anyInt());
    }

    @Test
    void enroll_alreadyEnrolledIsRejectedBeforeAnyCharge() {
        when(userStats.getEducation(USER)).thenReturn(2);
        when(userStats.getEduprog(USER)).thenReturn(5); // mid-study

        EnrollOutcome outcome = service.enroll();

        assertEquals(EnrollOutcome.Status.ALREADY_ENROLLED, outcome.status());
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(userStats, never()).updateEduprog(anyString(), anyInt());
    }

    @Test
    void enroll_educationCompleteIsRejectedBeforeAnyCharge() {
        when(userStats.getEducation(USER)).thenReturn(8);

        EnrollOutcome outcome = service.enroll();

        assertEquals(EnrollOutcome.Status.EDUCATION_COMPLETE, outcome.status());
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(userStats, never()).updateEduprog(anyString(), anyInt());
    }

    // ---- study -----------------------------------------------------------------

    @Test
    void study_advancesProgressWithoutCompletingTheDegree() {
        when(userStats.getEducation(USER)).thenReturn(0);
        when(userStats.getEduprog(USER)).thenReturn(1);
        when(users.getTime(USER)).thenReturn(4320);

        StudyOutcome outcome = service.study();

        assertEquals(new StudyOutcome(StudyOutcome.Status.OK, 3960, 2, 0, null), outcome);
        verify(userStats).updateEduprog(USER, 2);
        verify(userStats, never()).updateEducation(anyString(), anyInt());
    }

    @Test
    void study_completesTheDegreeOnTheTenthStudy() {
        when(userStats.getEducation(USER)).thenReturn(0);
        when(userStats.getEduprog(USER)).thenReturn(10); // the 10th study: prog becomes 11
        when(users.getTime(USER)).thenReturn(4320);

        StudyOutcome outcome = service.study();

        assertEquals(new StudyOutcome(StudyOutcome.Status.DEGREE_COMPLETED, 3960, 0, 1, "Junior College"), outcome);
        verify(userStats).updateEduprog(USER, 0);
        verify(userStats).updateEducation(USER, 1);
    }

    @Test
    void study_completesTheFinalDegreeAtEducationLevelEight() {
        when(userStats.getEducation(USER)).thenReturn(7); // "Publishing", the last degree
        when(userStats.getEduprog(USER)).thenReturn(10);
        when(users.getTime(USER)).thenReturn(4320);

        StudyOutcome outcome = service.study();

        assertEquals(new StudyOutcome(StudyOutcome.Status.DEGREE_COMPLETED, 3960, 0, 8, "Publishing"), outcome);
        verify(userStats).updateEducation(USER, 8);
    }

    @Test
    void study_insufficientTimeRejectsAndChangesNothing() {
        when(userStats.getEducation(USER)).thenReturn(0);
        when(userStats.getEduprog(USER)).thenReturn(1);
        when(users.getTime(USER)).thenReturn(300); // 300 - 360 < 0

        StudyOutcome outcome = service.study();

        assertEquals(StudyOutcome.Status.INSUFFICIENT_TIME, outcome.status());
        verify(userStats, never()).updateEduprog(anyString(), anyInt());
        verify(userStats, never()).updateEducation(anyString(), anyInt());
    }

    @Test
    void study_notEnrolledIsRejectedBeforeAnyCharge() {
        when(userStats.getEducation(USER)).thenReturn(0);
        when(userStats.getEduprog(USER)).thenReturn(0);

        StudyOutcome outcome = service.study();

        assertEquals(StudyOutcome.Status.NOT_ENROLLED, outcome.status());
        verify(users, never()).updateTime(anyString(), anyInt());
        verify(userStats, never()).updateEduprog(anyString(), anyInt());
    }

    @Test
    void study_educationCompleteIsRejectedBeforeAnyCharge() {
        when(userStats.getEducation(USER)).thenReturn(8);

        StudyOutcome outcome = service.study();

        assertEquals(StudyOutcome.Status.EDUCATION_COMPLETE, outcome.status());
        verify(users, never()).updateTime(anyString(), anyInt());
    }
}
