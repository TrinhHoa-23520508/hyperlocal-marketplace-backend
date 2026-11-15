package vn.edu.uit.nearby.nearby_backend.identity.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
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

import vn.edu.uit.nearby.nearby_backend.identity.SanctionLifted;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;

@ExtendWith(MockitoExtension.class)
class SuspensionExpiryJobTests {

	private static final Instant LATER = NOW.plus(Duration.ofDays(1));

	@Mock
	UserRepository users;

	@Mock
	ApplicationEventPublisher events;

	@Test
	void expiredSuspensionsBecomeActiveAndAreAnnounced() {
		User user = UserFixtures.suspendedUntil(LATER);
		when(users.findByStatusAndSuspendedUntilLessThanEqual(AccountStatus.SUSPENDED, LATER))
			.thenReturn(List.of(user));

		job().liftExpiredSuspensions();

		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		verify(events).publishEvent(new SanctionLifted(user.getId(), LATER));
	}

	@Test
	void nothingIsAnnouncedWhenNoSuspensionExpired() {
		when(users.findByStatusAndSuspendedUntilLessThanEqual(AccountStatus.SUSPENDED, LATER)).thenReturn(List.of());

		job().liftExpiredSuspensions();

		verifyNoInteractions(events);
	}

	private SuspensionExpiryJob job() {
		return new SuspensionExpiryJob(users, events, Clock.fixed(LATER, ZoneOffset.UTC));
	}

}
