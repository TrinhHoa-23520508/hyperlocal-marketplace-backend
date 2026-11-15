package vn.edu.uit.nearby.nearby_backend.shared.sms;

import java.io.Serial;

public class SmsDeliveryException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public SmsDeliveryException(String message) {
		super(message);
	}

	public SmsDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}

}
