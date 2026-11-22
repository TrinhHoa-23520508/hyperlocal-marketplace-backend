package vn.edu.uit.nearby.nearby_backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

class ValueObjectTests {

	@Test
	void emailIsTrimmedAndLowercased() {
		EmailAddress email = EmailAddress.of("  Binh.Tran@Example.COM ");

		assertThat(email.value()).isEqualTo("binh.tran@example.com");
		assertThat(email.localPart()).isEqualTo("binh.tran");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "no-at-sign", "two@@example.com", "spaces in@example.com", "missing@tld" })
	void malformedEmailIsRejected(String value) {
		assertThatThrownBy(() -> EmailAddress.of(value)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void emailLongerThanTheColumnIsRejected() {
		String tooLong = "a".repeat(243) + "@example.com";

		assertThatThrownBy(() -> EmailAddress.of(tooLong)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void emailAtTheColumnLimitIsAccepted() {
		String longest = "a".repeat(242) + "@example.com";

		assertThat(EmailAddress.of(longest).value()).hasSize(254);
	}

}
