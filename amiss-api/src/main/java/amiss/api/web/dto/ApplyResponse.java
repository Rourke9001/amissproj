package amiss.api.web.dto;

import java.util.List;

/**
 * The outcome of a job application (KAN-54). A charged rejection (never hired, but
 * {@code minutesCharged} still reflects the interview time) is a normal 200, not an error —
 * {@code reasons} is the only channel through which a player ever learns why (education,
 * experience, work-history, or no openings; possibly several at once).
 */
public record ApplyResponse(boolean hired, List<String> reasons, int minutesCharged, String job,
        Integer wage, SaveStateDto state) {
}
