package amiss.api.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.error.PlayerNotFoundException;
import amiss.api.security.SecurityConfig;
import amiss.application.port.PersistenceFailureException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The KAN-27 error-envelope contract: every API error leaves as an RFC 7807
 * problem document with a stable machine-readable {@code type}. These test-only routes live
 * outside {@code /api/**}, so KAN-37's {@code anyRequest().authenticated()} catch-all still
 * requires a bearer token here — {@code .with(jwt())} proves the error envelope, not the auth
 * rule itself.
 */
@WebMvcTest(controllers = ProblemDetailContractTest.ThrowingController.class)
@Import({GlobalExceptionHandler.class, ProblemDetailContractTest.ThrowingController.class, SecurityConfig.class})
class ProblemDetailContractTest {

    @Autowired
    private MockMvc mvc;

    /** Test-only endpoints that throw the exceptions under contract. */
    @RestController
    static class ThrowingController {

        @GetMapping("/test/missing-player")
        String missingPlayer() {
            throw new PlayerNotFoundException("bob");
        }

        @GetMapping("/test/db-failure")
        String dbFailure() {
            throw new PersistenceFailureException(new SQLException("db down"));
        }
    }

    @Test
    void unknownPlayerBecomesA404Problem() throws Exception {
        mvc.perform(get("/test/missing-player").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:player-not-found"))
                .andExpect(jsonPath("$.title").value("Player not found"))
                .andExpect(jsonPath("$.detail").value("No player named 'bob'"));
    }

    @Test
    void databaseFailureBecomesA500ProblemWithoutLeakingInternals() throws Exception {
        mvc.perform(get("/test/db-failure").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:persistence-failure"))
                .andExpect(jsonPath("$.title").value("Database error"))
                .andExpect(jsonPath("$.detail").value(
                        "A database error prevented the request from completing"));
    }
}
