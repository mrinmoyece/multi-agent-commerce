package com.agentforge.commerce.platform.api.error;

import com.agentforge.commerce.platform.api.web.CorrelationIdFilter;
import com.agentforge.commerce.platform.service.exception.ApprovalNotFoundException;
import com.agentforge.commerce.platform.service.exception.IdempotencyConflictException;
import com.agentforge.commerce.platform.service.exception.InvalidStateTransitionException;
import com.agentforge.commerce.platform.service.exception.RateLimitExceededException;
import com.agentforge.commerce.platform.service.exception.RunNotFoundException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralised RFC 9457 error rendering. Maps domain exceptions to
 * {@code application/problem+json} responses that carry the request correlation
 * id and never leak internal details or stack traces — the 500 path returns a
 * deliberately generic, health-safe body. Extending
 * {@link ResponseEntityExceptionHandler} means Spring MVC exceptions (validation,
 * unreadable body, unsupported method, …) keep their correct status codes and are
 * still enriched with the correlation id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "urn:problem-type:";

    @ExceptionHandler({RunNotFoundException.class, ApprovalNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "not-found");
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidStateTransitionException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid state transition", ex.getMessage(),
                "invalid-state-transition");
    }

    @ExceptionHandler({IdempotencyConflictException.class, DataIntegrityViolationException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        String detail = ex instanceof IdempotencyConflictException
                ? ex.getMessage()
                : "The request conflicts with the current state of the resource.";
        return problem(HttpStatus.CONFLICT, "Conflict", detail, "conflict");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The resource was modified concurrently; retry the request.", "concurrent-modification");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimit(RateLimitExceededException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", ex.getMessage(),
                "rate-limited");
    }

    /**
     * Let authorization failures propagate to Spring Security's
     * {@code AccessDeniedHandler} (which renders a 403 problem detail) rather than
     * being swallowed by the generic handler below and turned into a 500.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public void handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception ({})", ex.getClass().getName(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.", "internal-error");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            addCorrelationId(problem);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + type));
        addCorrelationId(problem);
        return problem;
    }

    private static void addCorrelationId(ProblemDetail problem) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty(CorrelationIdFilter.MDC_KEY, correlationId);
        }
    }
}
