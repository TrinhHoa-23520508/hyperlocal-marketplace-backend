package vn.edu.uit.nearby.nearby_backend.shared.security;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class SecureRandomCodeGenerator implements OneTimeCodeGenerator {

	private static final int DECIMAL_RADIX = 10;

	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public String numericCode(int digits) {
		if (digits <= 0) {
			throw new IllegalArgumentException("A one-time code needs at least one digit");
		}
		StringBuilder code = new StringBuilder(digits);
		for (int i = 0; i < digits; i++) {
			code.append(secureRandom.nextInt(DECIMAL_RADIX));
		}
		return code.toString();
	}

}
