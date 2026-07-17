package amiss.api.web;

import amiss.api.error.AlreadyEnrolledException;
import amiss.api.error.ApplianceAlreadyOwnedException;
import amiss.api.error.DegreeAlreadyEarnedException;
import amiss.api.error.DegreeLockedException;
import amiss.api.error.EducationCompleteException;
import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.InvalidAmountException;
import amiss.api.error.InvalidCredentialsException;
import amiss.api.error.InvalidGoalException;
import amiss.api.error.InvalidRegistrationException;
import amiss.api.error.NoJobException;
import amiss.api.error.NotEnrolledException;
import amiss.api.error.PlayerNotFoundException;
import amiss.api.error.RentNotDueException;
import amiss.api.error.SaveNotFoundException;
import amiss.api.error.UnderdressedException;
import amiss.api.error.UnknownDegreeException;
import amiss.api.error.UnknownItemException;
import amiss.api.error.UnknownJobException;
import amiss.api.error.UnknownLocationException;
import amiss.api.error.UsernameTakenException;
import amiss.api.error.WeekNotOverException;
import amiss.api.error.WeekOverException;
import amiss.api.error.WrongLocationException;
import amiss.application.port.PersistenceFailureException;
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

    @ExceptionHandler(SaveNotFoundException.class)
    ProblemDetail saveNotFound(SaveNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Save not found");
        problem.setType(URI.create("urn:amiss:save-not-found"));
        return problem;
    }

    @ExceptionHandler(WeekNotOverException.class)
    ProblemDetail weekNotOver(WeekNotOverException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Week not over");
        problem.setType(URI.create("urn:amiss:week-not-over"));
        return problem;
    }

    @ExceptionHandler(WeekOverException.class)
    ProblemDetail weekOver(WeekOverException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Week over");
        problem.setType(URI.create("urn:amiss:week-over"));
        return problem;
    }

    @ExceptionHandler(InsufficientTimeException.class)
    ProblemDetail insufficientTime(InsufficientTimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Insufficient time");
        problem.setType(URI.create("urn:amiss:insufficient-time"));
        return problem;
    }

    @ExceptionHandler(UnknownLocationException.class)
    ProblemDetail unknownLocation(UnknownLocationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Unknown location");
        problem.setType(URI.create("urn:amiss:unknown-location"));
        return problem;
    }

    @ExceptionHandler(InvalidAmountException.class)
    ProblemDetail invalidAmount(InvalidAmountException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid amount");
        problem.setType(URI.create("urn:amiss:invalid-amount"));
        return problem;
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Insufficient funds");
        problem.setType(URI.create("urn:amiss:insufficient-funds"));
        return problem;
    }

    @ExceptionHandler(RentNotDueException.class)
    ProblemDetail rentNotDue(RentNotDueException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Rent not due");
        problem.setType(URI.create("urn:amiss:rent-not-due"));
        return problem;
    }

    @ExceptionHandler(WrongLocationException.class)
    ProblemDetail wrongLocation(WrongLocationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Wrong location");
        problem.setType(URI.create("urn:amiss:wrong-location"));
        return problem;
    }

    @ExceptionHandler(UnknownJobException.class)
    ProblemDetail unknownJob(UnknownJobException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Unknown job");
        problem.setType(URI.create("urn:amiss:unknown-job"));
        return problem;
    }

    @ExceptionHandler(ApplianceAlreadyOwnedException.class)
    ProblemDetail applianceAlreadyOwned(ApplianceAlreadyOwnedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Appliance already owned");
        problem.setType(URI.create("urn:amiss:appliance-already-owned"));
        return problem;
    }

    @ExceptionHandler(UnknownItemException.class)
    ProblemDetail unknownItem(UnknownItemException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Unknown item");
        problem.setType(URI.create("urn:amiss:unknown-item"));
        return problem;
    }

    @ExceptionHandler(NoJobException.class)
    ProblemDetail noJob(NoJobException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("No job");
        problem.setType(URI.create("urn:amiss:no-job"));
        return problem;
    }

    @ExceptionHandler(UnderdressedException.class)
    ProblemDetail underdressed(UnderdressedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Underdressed");
        problem.setType(URI.create("urn:amiss:underdressed"));
        return problem;
    }

    @ExceptionHandler(NotEnrolledException.class)
    ProblemDetail notEnrolled(NotEnrolledException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Not enrolled");
        problem.setType(URI.create("urn:amiss:not-enrolled"));
        return problem;
    }

    @ExceptionHandler(AlreadyEnrolledException.class)
    ProblemDetail alreadyEnrolled(AlreadyEnrolledException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Already enrolled");
        problem.setType(URI.create("urn:amiss:already-enrolled"));
        return problem;
    }

    @ExceptionHandler(EducationCompleteException.class)
    ProblemDetail educationComplete(EducationCompleteException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Education complete");
        problem.setType(URI.create("urn:amiss:education-complete"));
        return problem;
    }

    @ExceptionHandler(UnknownDegreeException.class)
    ProblemDetail unknownDegree(UnknownDegreeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Unknown degree");
        problem.setType(URI.create("urn:amiss:unknown-degree"));
        return problem;
    }

    @ExceptionHandler(DegreeLockedException.class)
    ProblemDetail degreeLocked(DegreeLockedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Degree locked");
        problem.setType(URI.create("urn:amiss:degree-locked"));
        return problem;
    }

    @ExceptionHandler(DegreeAlreadyEarnedException.class)
    ProblemDetail degreeAlreadyEarned(DegreeAlreadyEarnedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Degree already earned");
        problem.setType(URI.create("urn:amiss:degree-already-earned"));
        return problem;
    }

    @ExceptionHandler(InvalidGoalException.class)
    ProblemDetail invalidGoal(InvalidGoalException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid goal");
        problem.setType(URI.create("urn:amiss:invalid-goal"));
        return problem;
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    ProblemDetail invalidRegistration(InvalidRegistrationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid registration");
        problem.setType(URI.create("urn:amiss:invalid-registration"));
        return problem;
    }

    @ExceptionHandler(UsernameTakenException.class)
    ProblemDetail usernameTaken(UsernameTakenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Username taken");
        problem.setType(URI.create("urn:amiss:username-taken"));
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Invalid credentials");
        problem.setType(URI.create("urn:amiss:invalid-credentials"));
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
