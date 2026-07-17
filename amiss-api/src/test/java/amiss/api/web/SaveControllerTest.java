package amiss.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.CostsConfig;
import amiss.api.persistence.jpa.SaveEntity;
import amiss.api.persistence.jpa.SaveJpaRepository;
import amiss.api.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The KAN-54 saves CRUD: list/create/delete, every route owner-scoped. */
@WebMvcTest(SaveController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, CostsConfig.class})
class SaveControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveJpaRepository saves;

    private static SaveEntity entity(long id, String owner, String label, int round, int cash, boolean won) {
        SaveEntity entity = mock(SaveEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getOwner()).thenReturn(owner);
        when(entity.getLabel()).thenReturn(label);
        when(entity.getRound()).thenReturn(round);
        when(entity.getCash()).thenReturn(cash);
        when(entity.getWon()).thenReturn(won ? 1 : 0);
        when(entity.getUpdatedAt()).thenReturn(Instant.parse("2026-07-10T00:00:00Z"));
        return entity;
    }

    @Test
    void list_returnsOnlyTheCallersOwnSaves() throws Exception {
        // entity(...) does its own when(...).thenReturn(...) stubbing internally, so it must be
        // fully built before the outer when(...) below opens its own stub, not passed inline as
        // an argument (Mockito's stubbing state is not reentrant).
        SaveEntity run1 = entity(1, "alice", "Run 1", 3, 250, false);
        when(saves.findByOwnerOrderByUpdatedAtDesc("alice")).thenReturn(List.of(run1));

        mvc.perform(get("/api/saves").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].label").value("Run 1"))
                .andExpect(jsonPath("$[0].round").value(3))
                .andExpect(jsonPath("$[0].cash").value(250))
                .andExpect(jsonPath("$[0].won").value(false));
    }

    @Test
    void create_explicitGoalsInRangeReturns201() throws Exception {
        SaveEntity saved = entity(5, "alice", "New Game", 1, 100, false);
        when(saves.save(any(SaveEntity.class))).thenReturn(saved);
        when(saves.findById(5L)).thenReturn(Optional.of(saved));

        mvc.perform(post("/api/saves")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"New Game\",\"goals\":"
                                + "{\"wealth\":50,\"happiness\":50,\"education\":50,\"career\":50}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.label").value("New Game"));
    }

    @Test
    void create_seedsRoundOneWithAFlatSixtyHourWeek() throws Exception {
        SaveEntity saved = entity(5, "alice", "New Game", 1, 100, false);
        when(saves.save(any(SaveEntity.class))).thenReturn(saved);
        when(saves.findById(5L)).thenReturn(Optional.of(saved));

        mvc.perform(post("/api/saves")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"New Game\",\"goals\":"
                                + "{\"wealth\":50,\"happiness\":50,\"education\":50,\"career\":50}}"))
                .andExpect(status().isCreated());

        // Round 1 is budgeted from the same ActionCosts.baseWeekMinutes() every later week is
        // set from at rollover — not a second hard-coded copy of "a week is 60h" (KAN-23).
        ArgumentCaptor<SaveEntity> created = ArgumentCaptor.forClass(SaveEntity.class);
        verify(saves).save(created.capture());
        assertThat(created.getValue().getTime()).isEqualTo(3600);
    }

    @Test
    void create_goalOutOfRangeIsA400Problem() throws Exception {
        mvc.perform(post("/api/saves")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"New Game\",\"goals\":"
                                + "{\"wealth\":5,\"happiness\":50,\"education\":50,\"career\":50}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:invalid-goal"));
    }

    @Test
    void create_missingGoalsWithoutRandomIsA400Problem() throws Exception {
        mvc.perform(post("/api/saves")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"New Game\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:invalid-goal"));
    }

    @Test
    void create_randomTrueRollsEveryGoalAndReturns201() throws Exception {
        SaveEntity saved = entity(6, "alice", "Random Game", 1, 100, false);
        when(saves.save(any(SaveEntity.class))).thenReturn(saved);
        when(saves.findById(6L)).thenReturn(Optional.of(saved));

        mvc.perform(post("/api/saves")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Random Game\",\"random\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6));
    }

    @Test
    void delete_ownSaveIsNoContent() throws Exception {
        SaveEntity mine = entity(3, "alice", "Mine", 1, 100, false);
        when(saves.findById(3L)).thenReturn(Optional.of(mine));

        mvc.perform(delete("/api/saves/3").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isNoContent());

        verify(saves).delete(mine);
    }

    @Test
    void delete_unknownIdIsA404Problem() throws Exception {
        when(saves.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/saves/99").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:save-not-found"));
    }

    @Test
    void delete_anotherPlayersSaveIsForbidden() throws Exception {
        SaveEntity bobs = entity(4, "bob", "Bob's", 1, 100, false);
        when(saves.findById(4L)).thenReturn(Optional.of(bobs));

        mvc.perform(delete("/api/saves/4").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));
    }
}
