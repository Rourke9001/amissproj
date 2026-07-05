package amiss.api.web.dto;

/** Body of {@code POST .../jobs/apply}: the job name to apply for. */
public record ApplyRequest(String job) {
}
