package vn.edu.uit.nearby.nearby_backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

class PhoneNumberTests {

	@ParameterizedTest
	@ValueSource(strings = { "0912345678", "84912345678", "+84912345678", "091 234 5678", "091.234.5678",
			"091-234-5678", "(+84) 912 345 678" })
	void acceptedFormsAreNormalisedToE164(String raw) {
		assertThat(PhoneNumber.of(raw).value()).isEqualTo("+84912345678");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0312345678", "0512345678", "0712345678", "0812345678", "0912345678" })
	void everyVietnameseMobilePrefixIsAccepted(String raw) {
		assertThat(PhoneNumber.of(raw).value()).matches("^\\+84[0-9]{9}$");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(
			strings = { "091234567", "09123456789", "0212345678", "0412345678", "+14155550100", "09123x5678", "abc" })
	void anythingElseIsRejectedAsInvalidPhone(String raw) {
		assertThatThrownBy(() -> PhoneNumber.of(raw)).isInstanceOf(InvalidRequestException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("INVALID_PHONE");
	}

	@ParameterizedTest
	@ValueSource(strings = { "An@Example.com", " an@example.com " })
	void identifierWithAnAtSignIsAnEmail(String raw) {
		assertThat(AccountIdentifier.parse(raw)).isEqualTo(EmailAddress.of("an@example.com"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0912345678", "+84912345678" })
	void identifierWithoutAnAtSignIsAPhoneNumber(String raw) {
		assertThat(AccountIdentifier.parse(raw)).isEqualTo(PhoneNumber.of("+84912345678"));
	}

}
