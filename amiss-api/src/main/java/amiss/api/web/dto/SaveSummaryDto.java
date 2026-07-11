package amiss.api.web.dto;

import java.time.Instant;

/** One row of {@code GET /api/saves}: enough to list and choose a save, nothing more. */
public record SaveSummaryDto(long id, String label, int round, int cash, boolean won, Instant updatedAt) {
}
