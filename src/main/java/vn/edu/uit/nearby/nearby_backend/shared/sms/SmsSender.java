package vn.edu.uit.nearby.nearby_backend.shared.sms;

/**
 * Gateway to the SMS provider (ADR-18). Changing provider means adding an implementation,
 * not changing callers.
 */
public interface SmsSender {

	/**
	 * Sends synchronously, so the caller knows whether the message left.
	 * @param phoneE164 recipient in E.164 format, for example {@code +84912345678}
	 * @throws SmsDeliveryException when the provider rejects the message or cannot be
	 * reached
	 */
	void send(String phoneE164, String content);

}
