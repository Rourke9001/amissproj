package amiss.api.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.security.SecurityConfig;
import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserRepository;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The KAN-28 high-score board: ranked rows, highest round first. */
@WebMvcTest(HighscoresController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class HighscoresControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserRepository users;

    @Test
    void highscores_returnsRowsRankedByRound() throws Exception {
        when(users.highScores()).thenReturn(List.of(
                new String[]{"alice", "5"},
                new String[]{"bob", "3"}));

        mvc.perform(get("/api/highscores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].round").value(5))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[1].username").value("bob"))
                .andExpect(jsonPath("$[1].round").value(3));
    }

    @Test
    void highscores_sqlExceptionIsA500Problem() throws Exception {
        when(users.highScores()).thenThrow(new PersistenceFailureException(new SQLException("db down")));

        mvc.perform(get("/api/highscores"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:persistence-failure"));
    }
}
