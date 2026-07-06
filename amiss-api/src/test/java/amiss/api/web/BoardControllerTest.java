package amiss.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.CostsConfig;
import amiss.api.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The KAN-30 board contract: 13 ring stops in clockwise order + travel cost metadata. */
@WebMvcTest(controllers = BoardController.class)
@Import({CostsConfig.class, SecurityConfig.class})
class BoardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void board_returnsTheThirteenStopsAndTravelMetadata() throws Exception {
        mvc.perform(get("/api/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops.length()").value(13))
                .andExpect(jsonPath("$.stops[0].id").value("LOW_COST_HOUSING"))
                .andExpect(jsonPath("$.stops[0].ringIndex").value(0))
                .andExpect(jsonPath("$.stops[0].row").value(0))
                .andExpect(jsonPath("$.stops[0].col").value(2))
                .andExpect(jsonPath("$.stops[6].id").value("HI_TECH_U"))
                .andExpect(jsonPath("$.stops[12].id").value("RENT_OFFICE"))
                .andExpect(jsonPath("$.travel.minutesPerStep").value(40))
                .andExpect(jsonPath("$.travel.enterBuildingMinutes").value(120));
    }
}
