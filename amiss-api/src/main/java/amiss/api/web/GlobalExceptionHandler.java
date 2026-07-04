package amiss.api.web;

import amiss.api.error.PersistenceFailureException;
import amiss.api.error.PlayerNotFoundException;
import amiss.api.error.WeekNotOverException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps API exceptions to RFC 7807 Problem Details, so every error leaves as
 * {@code application/problem+json} with a stable machine-readable {@code type}.
 * Framework exceptions (unknown route, bad JSON, validation…) get the same
 * treatment via {@link ResponseEntityExceptionHandler} +
 * {@code spring.mvc.problemdetails.enabled}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PlayerNotFoundException.class)
    ProblemDetail playerNotFound(PlayerNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Player not found");
        problem.setType(URI.create("urn:amiss:player-not-found"));
        return problem;
    }

    @ExceptionHandler(WeekNotOverException.class)
    ProblemDetail weekNotOver(WeekNotOverException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Week not over");
        problem.setType(URI.create("urn:amiss:week-not-over"));
        return problem;
    }

    @ExceptionHandler(PersistenceFailureException.class)
    ProblemDetail persistenceFailure(PersistenceFailureException ex) {
        log.error("Database access failed", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "A database error prevented the request from completing");
        problem.setTitle("Database error");
        problem.setType(URI.create("urn:amiss:persistence-failure"));
        return problem;
    }
}
