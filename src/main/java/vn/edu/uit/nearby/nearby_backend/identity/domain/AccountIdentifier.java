package vn.edu.uit.nearby.nearby_backend.identity.domain;

/**
 * What a user types to identify the account: the phone number, or an email address that
 * was verified (FR-ID-03, FR-ID-07).
 */
public sealed interface AccountIdentifier permits PhoneNumber, EmailAddress {

	String EMAIL_MARKER = "@";

	String value();

	static AccountIdentifier parse(String raw) {
		if (raw != null && raw.contains(EMAIL_MARKER)) {
			return EmailAddress.of(raw);
		}
		return PhoneNumber.of(raw);
	}

}
