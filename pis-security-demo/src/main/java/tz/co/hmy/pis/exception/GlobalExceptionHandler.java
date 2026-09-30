package tz.co.hmy.pis.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One place where exceptions become HTTP responses.
 *
 * RFC 9457 problem detail, so every error has the same machine-readable shape.
 * Without this, each controller invents its own error format and the client
 * ends up parsing four of them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail onNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "not-found");
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail onDuplicate(DuplicateResourceException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate resource", ex.getMessage(), "duplicate");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail onBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated",
                ex.getMessage(), "business-rule");
    }

    /** Field-level validation failures, returned as a map the client can attach to inputs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));

        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", "validation");
        pd.setProperty("errors", errors);
        return pd;
    }

    /**
     * A failed login at POST /api/v1/auth/login. Unlike a 401 from the security
     * filters, this one is thrown inside a controller, so it arrives here.
     * Without this handler it would fall through to Exception and become a 500.
     *
     * One message for wrong password, unknown user and disabled account alike.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail onLoginFailed(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Login failed",
                "Invalid username or password", "login-failed");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail onInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid refresh token",
                "The refresh token is invalid, expired or revoked. Log in again.", "invalid-refresh-token");
    }

    /**
     * A @PreAuthorize rule said no. That happens INSIDE the service, so unlike a
     * URL rule the exception reaches this class, and without this handler the
     * catch-all below would turn it into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied",
                "You may not perform this operation on this record", "forbidden");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        // Deliberately does not leak ex.getMessage() to the client.
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "An unexpected error occurred. Quote the timestamp when reporting it.",
                "internal");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://hmy.co.tz/problems/" + type));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
