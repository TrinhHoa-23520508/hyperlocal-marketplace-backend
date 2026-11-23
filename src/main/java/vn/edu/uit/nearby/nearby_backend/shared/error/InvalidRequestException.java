package vn.edu.uit.nearby.nearby_backend.shared.error;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;

public non-sealed class InvalidRequestException extends BusinessException {

	@Serial
	private static final long serialVersionUID = 1L;

	public InvalidRequestException(String code, String message) {
		super(code, message);
	}

	public InvalidRequestException(String code, String message, Map<String, Object> properties) {
		super(code, message, properties);
	}

	/**
	 * {@code 400 VALIDATION_FAILED} for one field, in the same {@code errors} shape as
	 * Bean Validation failures, for rules the domain checks after the request was bound.
	 */
	public static InvalidRequestException invalidField(String field, String message) {
		return new InvalidRequestException(ErrorCodes.VALIDATION_FAILED, "Request validation failed.",
				Map.of("errors", List.of(Map.of("field", field, "message", message))));
	}

}
