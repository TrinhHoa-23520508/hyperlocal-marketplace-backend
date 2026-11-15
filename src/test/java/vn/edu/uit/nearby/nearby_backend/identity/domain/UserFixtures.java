package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.time.Instant;

public final class UserFixtures {

	public static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");

	public static final String PHONE = "+84912345678";

	public static final String EMAIL = "an.nguyen@example.com";

	public static final String PASSWORD_HASH = "{noop}Secret123";

	public static final String DISPLAY_NAME = "An Nguyen";

	private UserFixtures() {
	}

	public static User activeUser() {
		return activeUser(PHONE);
	}

	public static User activeUser(String phone) {
		return User.register(PhoneNumber.of(phone), PASSWORD_HASH, DISPLAY_NAME, NOW);
	}

	public static User userWithVerifiedEmail() {
		User user = activeUser();
		EmailAddress email = EmailAddress.of(EMAIL);
		user.changeEmail(email, NOW);
		user.verifyEmail(email, NOW);
		return user;
	}

	public static User userWithUnverifiedEmail() {
		User user = activeUser();
		user.changeEmail(EmailAddress.of(EMAIL), NOW);
		return user;
	}

	public static User lockedUser() {
		User user = activeUser();
		user.lock(NOW);
		return user;
	}

	public static User admin() {
		return User.administrator(EmailAddress.of("admin@example.com"), PASSWORD_HASH, "Admin", NOW);
	}

}
