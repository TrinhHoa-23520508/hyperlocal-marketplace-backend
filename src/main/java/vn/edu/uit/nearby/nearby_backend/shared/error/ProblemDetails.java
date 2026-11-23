package vn.edu.uit.nearby.nearby_backend.shared.error;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;

/**
 * Builds the RFC 9457 body of a business error. REST answers it with the matching HTTP
 * status; the STOMP error queue carries the same body (UC-CH-02 E1..E4), so the app
 * parses one shape on both channels.
 */
public final class ProblemDetails {

	private ProblemDetails() {
	}

	public static HttpStatus statusOf(BusinessException ex) {
		return switch (ex) {
			case ResourceNotFoundException e -> HttpStatus.NOT_FOUND;
			case ConflictException e -> HttpStatus.CONFLICT;
			case InvalidRequestException e -> HttpStatus.BAD_REQUEST;
			case OperationNotAllowedException e -> HttpStatus.FORBIDDEN;
			case RateLimitExceededException e -> HttpStatus.TOO_MANY_REQUESTS;
			case AuthenticationFailedException e -> HttpStatus.UNAUTHORIZED;
			case UnprocessableRequestException e -> HttpStatus.UNPROCESSABLE_CONTENT;
			case ServiceUnavailableException e -> HttpStatus.SERVICE_UNAVAILABLE;
		};
	}

	public static ProblemDetail of(BusinessException ex) {
		ProblemDetail problem = of(statusOf(ex), ex.getCode(), ex.getMessage());
		ex.getProperties().forEach(problem::setProperty);
		return problem;
	}

	public static ProblemDetail of(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create(ErrorCodes.PROBLEM_TYPE_BASE + code.toLowerCase().replace('_', '-')));
		problem.setProperty("code", code);
		return problem;
	}

}
