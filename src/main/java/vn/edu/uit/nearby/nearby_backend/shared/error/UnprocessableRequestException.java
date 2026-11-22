package vn.edu.uit.nearby.nearby_backend.shared.error;

import java.io.Serial;
import java.util.Map;

/**
 * The request is well formed but breaks a business rule (HTTP 422), for example a wrong
 * or expired one-time code.
 */
public non-sealed class UnprocessableRequestException extends BusinessException {

	@Serial
	private static final long serialVersionUID = 1L;

	public UnprocessableRequestException(String code, String message) {
		super(code, message);
	}

	public UnprocessableRequestException(String code, String message, Map<String, Object> properties) {
		super(code, message, properties);
	}

}
