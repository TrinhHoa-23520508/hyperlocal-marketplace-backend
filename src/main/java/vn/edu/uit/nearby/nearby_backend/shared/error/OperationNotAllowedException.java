package vn.edu.uit.nearby.nearby_backend.shared.error;

import java.io.Serial;
import java.util.Map;

public non-sealed class OperationNotAllowedException extends BusinessException {

	@Serial
	private static final long serialVersionUID = 1L;

	public OperationNotAllowedException(String code, String message) {
		super(code, message);
	}

	public OperationNotAllowedException(String code, String message, Map<String, Object> properties) {
		super(code, message, properties);
	}

}
