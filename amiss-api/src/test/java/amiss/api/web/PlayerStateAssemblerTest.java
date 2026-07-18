package amiss.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import amiss.api.web.dto.SaveStateDto;
import amiss.application.config.ActionCosts;
import amiss.application.port.DegreeCatalog;
import amiss.application.port.JobCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.application.port.Turndowns;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.model.DegreeSpec;
import amiss.domain.model.JobSpec;
import amiss.domain.model.SaveState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link PlayerStateAssembler} (KAN-54: rewritten over the save-scoped facade).
 * Wired with a <em>real</em> {@link SaveGameServices} over mocked ports, since the assembler
 * drives goals/courses through it exactly as the controllers do.
 */
@ExtendWith(MockitoExtension.class)
class PlayerStateAssemblerTest {

    private static final long SAVE_ID = 7L;

    @Mock
    private SaveRepository saves;
    @Mock
    private JobCatalog jobCatalog;
    @Mock
    private DegreeCatalog degreeCatalog;
    @Mock
    private SaveDegrees saveDegrees;
    @Mock
    private Turndowns turndowns;

    private PlayerStateAssembler assembler;

    @BeforeEach
    void setUp() {
        // Built here, not as a field initializer: @Mock fields are injected by MockitoExtension
        // after JUnit constructs the test instance, so a field initializer would capture a
        // still-null jobCatalog.
        assembler = new PlayerStateAssembler(jobCatalog);
    }

    private SaveGameServices services() {
        return new SaveGameServices(saves, jobCatalog, degreeCatalog, saveDegrees, turndowns,
                ActionCosts.defaults(), () -> 100, n -> 1);
    }

    private static SaveState save(Integer jobId, Integer currentCourseId, int eduprog) {
        return new SaveState(SAVE_ID, "bob", "My Save", 0, 2, 3960, 3, 120, 50, 0, 0, 1,
                1, 0, 0,
                jobId, 60, 30, 40, currentCourseId, eduprog, 200, 100, 30, 50, false, (byte) 0, (short) 0,
                jobId != null ? 6 : null,  // wage snapshot when employed
                false, Set.of());
    }

    private static SaveState save(int timeMinutes) {
        return new SaveState(SAVE_ID, "bob", "My Save", 0, 2, timeMinutes, 3, 120, 50, 0, 0, 1,
                1, 0, 0,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null,
                false, Set.of());
    }

    @Test
    void assemble_unemployedSaveHasUnemployedJobWageAndLocation() {
        when(degreeCatalog.all()).thenReturn(List.of());
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of());

        SaveStateDto dto = assembler.assemble(services(), save(null, null, 0));

        assertEquals("Unemployed", dto.job().name());
        assertNull(dto.job().hourlyWage());
        assertNull(dto.job().location());
    }

    @Test
    void assemble_employedSaveReportsWageAndLocation() {
        when(jobCatalog.byId(4)).thenReturn(Optional.of(new JobSpec(4, "Cook", "Monolith Burgers", 5, 0, 0, 0)));
        when(degreeCatalog.all()).thenReturn(List.of());
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of());

        SaveStateDto dto = assembler.assemble(services(), save(4, null, 0));

        assertEquals("Cook", dto.job().name());
        assertEquals(6, dto.job().hourlyWage());  // snapshot wage, not catalog wage (5)
        assertEquals("Monolith Burgers", dto.job().location());
    }

    @Test
    void assemble_populatesGoalsFoodClothingAndEarnedDegrees() {
        when(degreeCatalog.all()).thenReturn(List.of(new DegreeSpec(1, "Junior College", null)));
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of(1));

        SaveStateDto dto = assembler.assemble(services(), save(null, null, 0));

        assertEquals(1, dto.foodWeeks());
        assertEquals(false, dto.ateFastFoodLastTurn());
        assertEquals(1, dto.clothingCasualWeeks());
        assertEquals(0, dto.clothingDressWeeks());
        assertEquals(0, dto.clothingBusinessWeeks());
        assertEquals(50, dto.bank());
        assertEquals(List.of("Junior College"), dto.degreesEarned());
        assertNull(dto.currentCourse());

        assertEquals(1, dto.goals().wealth().current());
        assertEquals(200, dto.goals().wealth().target());
        assertEquals(60, dto.goals().happiness().current());
        assertEquals(100, dto.goals().happiness().target());
        assertEquals(10, dto.goals().education().current());
        assertEquals(30, dto.goals().education().target());
        assertEquals(0, dto.goals().career().current());
        assertEquals(50, dto.goals().career().target());
    }

    @Test
    void assemble_ateFastFoodLastTurnIsThreadedThroughFromSaveState() {
        when(degreeCatalog.all()).thenReturn(List.of());
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of());
        SaveState save = new SaveState(SAVE_ID, "bob", "My Save", 0, 2, 3960, 3, 120, 50, 0, 0, 1,
                1, 0, 0,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null,
                true, Set.of());

        SaveStateDto dto = assembler.assemble(services(), save);

        assertEquals(true, dto.ateFastFoodLastTurn());
    }

    @Test
    void assemble_currentCourseReportsProgress() {
        when(degreeCatalog.all()).thenReturn(List.of(
                new DegreeSpec(1, "Junior College", null),
                new DegreeSpec(2, "Business", 1)));
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of(1));

        SaveStateDto dto = assembler.assemble(services(), save(null, 2, 4));

        assertEquals(List.of("Junior College"), dto.degreesEarned());
        assertEquals(2, dto.currentCourse().id());
        assertEquals("Business", dto.currentCourse().name());
        assertEquals(4, dto.currentCourse().studiesDone());
    }

    @Test
    void assemble_staleSavedPositionClampsToHome() {
        when(degreeCatalog.all()).thenReturn(List.of());
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of());
        SaveState stale = new SaveState(SAVE_ID, "bob", "My Save", 9, 9, 3960, 3, 120, 50, 0, 0, 1,
                1, 0, 0,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null,
                false, Set.of());

        SaveStateDto dto = assembler.assemble(services(), stale);

        assertEquals("LOW_COST_HOUSING", dto.location().id());
        assertEquals(0, dto.location().row());
        assertEquals(2, dto.location().col());
        assertEquals(0, dto.location().ringIndex());
    }

    // ---- timeDisplay: wire contract, mirrored by frontend/src/game/formatMinutes.ts --------
    // Carried over from the retired TimeServiceTest's format_* pins (KAN-54) now that
    // PlayerStateAssembler owns the formatting.

    @Test
    void assemble_timeDisplayRendersHoursAndMinutes() {
        when(degreeCatalog.all()).thenReturn(List.of());
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of());

        SaveStateDto dto = assembler.assemble(services(), save(2310));

        assertEquals("38h 30m", dto.timeDisplay());
    }

    @Test
    void assemble_timeDisplayOmitsTheMinutesPartWhenZero() {
        when(degreeCatalog.all()).thenReturn(List.of());
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of());

        SaveStateDto dto = assembler.assemble(services(), save(4320));

        assertEquals("72h", dto.timeDisplay());
    }

    @Test
    void assemble_timeDisplayRendersUnderAnHourAsMinutesOnly() {
        when(degreeCatalog.all()).thenReturn(List.of());
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of());

        SaveStateDto dto = assembler.assemble(services(), save(45));

        assertEquals("45m", dto.timeDisplay());
    }

    @Test
    void assemble_timeDisplayRendersAnEmptyClockAsZeroHours() {
        when(degreeCatalog.all()).thenReturn(List.of());
        when(saveDegrees.earned(SAVE_ID)).thenReturn(Set.of());

        SaveStateDto dto = assembler.assemble(services(), save(0));

        assertEquals("0h", dto.timeDisplay());
    }
}
