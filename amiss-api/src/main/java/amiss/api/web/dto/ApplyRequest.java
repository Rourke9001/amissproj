package amiss.api.web.dto;

/** Body of {@code POST .../jobs/apply}: the {@code tbljob} catalog id to apply for. */
public record ApplyRequest(Integer jobId) {
}
