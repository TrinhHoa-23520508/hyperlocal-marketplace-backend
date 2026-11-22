package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;

/**
 * Accounts for area tests: phone-verified users, with or without PRECISE_LOCATION
 * consent, and an administrator who has no phone.
 */
class AreaTestAccounts {

	private static final String PASSWORD_HASH = "{noop}not-used";

	private final UserRepository users;

	private final LocationConsentService locationConsents;

	private final AppParameters appParameters;

	AreaTestAccounts(UserRepository users, LocationConsentService locationConsents, AppParameters appParameters) {
		this.users = users;
		this.locationConsents = locationConsents;
		this.appParameters = appParameters;
	}

	UUID eligibleUser() {
		UUID userId = userWithoutConsent();
		locationConsents.grantConsent(userId, appParameters.get("consent.location_terms_version"));
		return userId;
	}

	UUID userWithoutConsent() {
		return users.save(User.register(PhoneNumber.of(randomPhone()), PASSWORD_HASH, "Area Tester", Instant.now()))
			.getId();
	}

	UUID admin() {
		return users
			.save(User.administrator(EmailAddress.of("admin-" + UUID.randomUUID() + "@nearby.test"), PASSWORD_HASH,
					"Area Admin", Instant.now()))
			.getId();
	}

	private static String randomPhone() {
		return "+843" + "%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
	}

}
