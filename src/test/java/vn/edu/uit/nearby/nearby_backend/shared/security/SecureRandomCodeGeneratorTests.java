package vn.edu.uit.nearby.nearby_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecureRandomCodeGeneratorTests {

	private final SecureRandomCodeGenerator generator = new SecureRandomCodeGenerator();

	@Test
	void codeHasExactlyTheRequestedNumberOfDigits() {
		assertThat(generator.numericCode(6)).matches("\\d{6}");
	}

	@Test
	void singleDigitCodeIsAllowed() {
		assertThat(generator.numericCode(1)).matches("\\d");
	}

	@Test
	void codesVary() {
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			codes.add(generator.numericCode(6));
		}

		assertThat(codes).hasSizeGreaterThan(40);
	}

	@Test
	void zeroDigitsIsRejected() {
		assertThatThrownBy(() -> generator.numericCode(0)).isInstanceOf(IllegalArgumentException.class);
	}

}
