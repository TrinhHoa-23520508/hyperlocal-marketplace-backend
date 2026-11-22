package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.application.profile.AvatarDiscarded;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountAnonymisationJobTests {

	private static final Instant RUN_AT = NOW.plus(Duration.ofDays(7));

	@Mock
	UserRepository users;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	ApplicationEventPublisher events;

	@Test
	void accountsPastTheGracePeriodAreAnonymisedAndTheirAvatarDiscarded() {
		User user = UserFixtures.activeUser();
		user.replaceAvatar("avatars/a.jpg", NOW);
		user.requestDeletion(NOW);
		when(users.findByStatusAndDeletionRequestedAtLessThanEqual(AccountStatus.PENDING_DELETION, NOW))
			.thenReturn(List.of(user));
		when(passwordEncoder.encode(anyString())).thenReturn("unusable");

		job().anonymiseDueAccounts();

		assertThat(user.getStatus()).isEqualTo(AccountStatus.DELETED);
		assertThat(user.phoneNumber()).isEmpty();
		assertThat(user.getPasswordHash()).isEqualTo("unusable");
		verify(events).publishEvent(new AvatarDiscarded("avatars/a.jpg"));
		verify(events).publishEvent(new AccountDeleted(user.getId(), RUN_AT));
	}

	@Test
	void nothingHappensWhenNoAccountIsDue() {
		when(users.findByStatusAndDeletionRequestedAtLessThanEqual(AccountStatus.PENDING_DELETION, NOW))
			.thenReturn(List.of());

		job().anonymiseDueAccounts();

		verifyNoInteractions(events, passwordEncoder);
	}

	private AccountAnonymisationJob job() {
		return new AccountAnonymisationJob(users, passwordEncoder, IdentityParametersFixture.seeded(), events,
				Clock.fixed(RUN_AT, ZoneOffset.UTC));
	}

}
