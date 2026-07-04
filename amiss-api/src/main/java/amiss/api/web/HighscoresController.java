package amiss.api.web;

import amiss.api.error.PersistenceFailureException;
import amiss.api.web.dto.HighscoreEntryDto;
import amiss.application.port.UserRepository;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The KAN-28 high-score board: every player's best round reached, ranked highest first.
 * Reads {@link UserRepository} directly (no per-player {@code GameServices} needed).
 */
@RestController
public class HighscoresController {

    private final UserRepository users;

    public HighscoresController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/highscores")
    public List<HighscoreEntryDto> highscores() {
        try {
            List<String[]> rows = users.highScores();
            List<HighscoreEntryDto> entries = new ArrayList<>(rows.size());
            int rank = 1;
            for (String[] row : rows) {
                entries.add(new HighscoreEntryDto(rank++, row[0], Integer.parseInt(row[1])));
            }
            return entries;
        } catch (SQLException e) {
            throw new PersistenceFailureException(e);
        }
    }
}
