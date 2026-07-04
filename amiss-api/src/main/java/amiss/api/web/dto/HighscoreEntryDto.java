package amiss.api.web.dto;

/** One row of the high-score board, ranked by round reached (highest first). */
public record HighscoreEntryDto(int rank, String username, int round) {
}
