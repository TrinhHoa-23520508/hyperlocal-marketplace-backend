package vn.edu.uit.nearby.nearby_backend.shared.error;

import java.io.Serial;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;

@Getter
public abstract sealed class BusinessException extends RuntimeException permits ResourceNotFoundException,
		ConflictException, InvalidRequestException, OperationNotAllowedException, RateLimitExceededException,
		AuthenticationFailedException, UnprocessableRequestException, ServiceUnavailableException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String code;

	@Getter(AccessLevel.NONE)
	private final Map<String, Object> properties;

	protected BusinessException(String code, String message) {
		this(code, message, Map.of());
	}

	protected BusinessException(String code, String message, Map<String, Object> properties) {
		super(message);
		this.code = code;
		this.properties = Map.copyOf(properties);
	}

	/**
	 * Extra members added to the problem detail, for example the list of violated rules.
	 */
	public Map<String, Object> getProperties() {
		return properties;
	}

}
