package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.config.ActionCosts;
import amiss.application.port.DegreeCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.domain.model.DegreeSpec;
import amiss.domain.model.SaveState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final DegreeSpec JUNIOR_COLLEGE = new DegreeSpec(1, "Junior College", null);
    private static final DegreeSpec TRADE_SCHOOL = new DegreeSpec(2, "Trade School", null);
    private static final DegreeSpec BUSINESS_ADMIN = new DegreeSpec(3, "Business Administration", 1);
    private static final int DEGREE_ID = 1;

    @Mock
    private SaveRepository saves;
    @Mock
    private DegreeCatalog catalog;
    @Mock
    private SaveDegrees degrees;

    private CourseService service() {
        return new CourseService(saves, catalog, degrees, ActionCosts.defaults(), new EconomyService(n -> 1));
    }

    @Test
    void theCourseBoardShowsEarnedAvailableAndLocked() {
        when(catalog.all()).thenReturn(List.of(JUNIOR_COLLEGE, TRADE_SCHOOL, BUSINESS_ADMIN));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(2));
        SaveState save = TestSaves.newSave();

        List<CourseService.CourseView> board = service().courses(save);

        assertEquals(CourseService.CourseStatus.AVAILABLE, board.get(0).status());
        assertEquals(CourseService.CourseStatus.EARNED, board.get(1).status());
        assertEquals(CourseService.CourseStatus.LOCKED, board.get(2).status());   // needs Junior College
    }

    @Test
    void enrollingChargesTheFeeAndTargetsTheCourse() {
        when(catalog.byId(1)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = TestSaves.newSave();

        CourseService.EnrollResult result = service().enroll(save, 1);

        assertEquals(CourseService.EnrollResult.Status.OK, result.status());
        assertEquals(50, save.cash());
        assertEquals(1, save.currentCourseId());
        assertEquals(0, save.eduprog());
        verify(saves).update(save);
    }

    @Test
    void enrollGuardsLockedEarnedBusyAndBroke() {
        SaveState save = TestSaves.newSave();

        when(catalog.byId(3)).thenReturn(Optional.of(BUSINESS_ADMIN));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        assertEquals(CourseService.EnrollResult.Status.LOCKED, service().enroll(save, 3).status());

        when(catalog.byId(1)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(1));
        assertEquals(CourseService.EnrollResult.Status.ALREADY_EARNED, service().enroll(save, 1).status());

        when(catalog.byId(2)).thenReturn(Optional.of(TRADE_SCHOOL));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        save.setCurrentCourseId(1);
        assertEquals(CourseService.EnrollResult.Status.ALREADY_ENROLLED, service().enroll(save, 2).status());

        save.setCurrentCourseId(null);
        save.setCash(49);
        assertEquals(CourseService.EnrollResult.Status.INSUFFICIENT_CASH, service().enroll(save, 2).status());

        when(catalog.byId(99)).thenReturn(Optional.empty());
        assertEquals(CourseService.EnrollResult.Status.UNKNOWN_DEGREE, service().enroll(save, 99).status());
    }

    @Test
    void tenStudySessionsGraduateWithTheDependabilityBonusOverCap() {
        when(catalog.byId(1)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        SaveState save = TestSaves.newSave();
        save.setCurrentCourseId(1);
        save.setEduprog(9);
        save.setDependability(40);   // any cap is irrelevant: the +5 is cap-exempt

        CourseService.StudyResult result = service().study(save);

        assertEquals(CourseService.StudyResult.Status.GRADUATED, result.status());
        assertEquals("Junior College", result.degreeCompleted());
        assertNull(save.currentCourseId());
        assertEquals(0, save.eduprog());
        assertEquals(45, save.dependability());
        verify(degrees).award(TestSaves.SAVE_ID, 1);
        verify(saves).update(save);
    }

    @Test
    void studyProgressesAndChargesTime() {
        SaveState save = TestSaves.newSave();
        save.setCurrentCourseId(1);

        CourseService.StudyResult result = service().study(save);

        assertEquals(CourseService.StudyResult.Status.OK, result.status());
        assertEquals(1, result.studiesDone());
        assertEquals(3960, save.timeMinutes());
    }

    @Test
    void studyNeedsEnrollmentAndTime() {
        assertEquals(CourseService.StudyResult.Status.NOT_ENROLLED,
                service().study(TestSaves.newSave()).status());

        SaveState save = TestSaves.newSave();
        save.setCurrentCourseId(1);
        save.setTimeMinutes(0);
        assertEquals(CourseService.StudyResult.Status.WEEK_OVER, service().study(save).status());
    }

    @Test
    void enrollChargesTheEconomyAdjustedFee() {
        SaveState save = TestSaves.newSave();   // R100 cash
        save.setEconomyReading((short) 60);     // fee 50 -> 100
        when(catalog.byId(DEGREE_ID)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());

        CourseService.EnrollResult result = service().enroll(save, DEGREE_ID);

        assertEquals(CourseService.EnrollResult.Status.OK, result.status());
        assertEquals(100, result.feePaid());
        assertEquals(0, save.cash());
    }

    @Test
    void enrollRejectsWhenCashIsBelowTheAdjustedFee() {
        SaveState save = TestSaves.newSave();   // R100 < 125
        save.setEconomyReading((short) 90);     // fee 50 -> 125
        when(catalog.byId(DEGREE_ID)).thenReturn(Optional.of(JUNIOR_COLLEGE));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());

        assertEquals(CourseService.EnrollResult.Status.INSUFFICIENT_CASH,
                service().enroll(save, DEGREE_ID).status());
    }
}
