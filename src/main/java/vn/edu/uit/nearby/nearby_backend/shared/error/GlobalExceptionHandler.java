package vn.edu.uit.nearby.nearby_backend.shared.error;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
		ProblemDetail problem = ProblemDetails.of(ex);
		ResponseEntity.BodyBuilder response = ResponseEntity.status(ProblemDetails.statusOf(ex));
		if (ex instanceof RateLimitExceededException rateLimited) {
			rateLimited.getRetryAfter()
				.ifPresent(retryAfter -> response.header(HttpHeaders.RETRY_AFTER, retryAfterSeconds(retryAfter)));
		}
		return response.body(problem);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.header(HttpHeaders.WWW_AUTHENTICATE, SecurityConstants.BEARER_SCHEME)
			.body(problem(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "A valid access token is required."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		log.warn("Access denied for user {} on {} {}", CurrentUser.id().map(UUID::toString).orElse("anonymous"),
				request.getMethod(), request.getRequestURI());
		return problem(HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, "You are not allowed to perform this action.");
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, ErrorCodes.CONCURRENT_MODIFICATION,
				"The resource was modified by another request; reload and retry.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.debug("Data integrity violation", ex);
		return problem(HttpStatus.CONFLICT, ErrorCodes.DATA_CONFLICT, "The request conflicts with existing data.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred.");
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
				"Request validation failed.");
		problem.setProperty("errors", fieldErrorsOf(ex));
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	/**
	 * A query or path value of the wrong type, such as an unknown enum name.
	 */
	@Override
	protected @Nullable ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String field = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
				: String.valueOf(ex.getPropertyName());
		return validationFailed(ex, List.of(Map.of("field", field, "message", "Invalid value.")), headers, request);
	}

	/**
	 * A required query parameter that was not sent.
	 */
	@Override
	protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		return validationFailed(ex,
				List.of(Map.of("field", ex.getParameterName(), "message", "This parameter is required.")), headers,
				request);
	}

	/**
	 * A constraint on a {@code @RequestParam} or {@code @PathVariable}.
	 */
	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(error -> Map.of("field", String.valueOf(result.getMethodParameter().getParameterName()), "message",
						String.valueOf(error.getDefaultMessage()))))
			.toList();
		return validationFailed(ex, errors, headers, request);
	}

	private @Nullable ResponseEntity<Object> validationFailed(Exception ex, List<Map<String, String>> errors,
			HttpHeaders headers, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
				"Request validation failed.");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	private static String retryAfterSeconds(Duration retryAfter) {
		return String.valueOf(Math.max(1, retryAfter.toSeconds()));
	}

	private static List<Map<String, String>> fieldErrorsOf(MethodArgumentNotValidException ex) {
		return ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
			.toList();
	}

	private static ProblemDetail problem(HttpStatus status, String code, String detail) {
		return ProblemDetails.of(status, code, detail);
	}

}
