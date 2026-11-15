package vn.edu.uit.nearby.nearby_backend.shared.error;

import java.io.Serial;

/**
 * An external dependency such as the SMS gateway failed (HTTP 503); the client may retry
 * later.
 */
public non-sealed class ServiceUnavailableException extends BusinessException {

	@Serial
	private static final long serialVersionUID = 1L;

	public ServiceUnavailableException(String code, String message) {
		super(code, message);
	}

}
