package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.edu.uit.nearby.nearby_backend.identity.AccountStanding;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
class AccountStandingServiceIntegrationTests {

	@Autowired
	AccountStanding accountStanding;

	@Autowired
	UserRepository users;

	@Test
	void anActiveUserWithAVerifiedPhoneIsInGoodStanding() {
		User user = users.save(phoneUser());

		assertThat(accountStanding.isActiveWithVerifiedPhone(user.getId())).isTrue();
		assertThatCode(() -> accountStanding.requireActiveWithVerifiedPhone(user.getId())).doesNotThrowAnyException();
	}

	@Test
	void aSuspendedUserIsNot() {
		User user = phoneUser();
		user.suspend(Instant.now().plus(Duration.ofDays(1)), Instant.now());
		users.save(user);

		assertThat(accountStanding.isActiveWithVerifiedPhone(user.getId())).isFalse();
	}

	@Test
	void anAccountWithoutAPhoneIsNot() {
		User admin = users.save(User.administrator(EmailAddress.of("standing-" + UUID.randomUUID() + "@nearby.test"),
				"hash", "Admin", Instant.now()));

		assertThatThrownBy(() -> accountStanding.requireActiveWithVerifiedPhone(admin.getId()))
			.isInstanceOf(OperationNotAllowedException.class)
			.hasFieldOrPropertyWithValue("code", "ACCESS_DENIED");
	}

	@Test
	void anUnknownUserIsNot() {
		assertThat(accountStanding.isActiveWithVerifiedPhone(UUID.randomUUID())).isFalse();
	}

	private static User phoneUser() {
		String phone = "+847" + "%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
		return User.register(PhoneNumber.of(phone), "hash", "Standing Tester", Instant.now());
	}

}
