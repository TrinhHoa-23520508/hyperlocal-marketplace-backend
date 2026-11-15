package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import vn.edu.uit.nearby.nearby_backend.identity.AccountDeletionBlocker;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeletionRequested;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.UserSessions;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.RevokeReason;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTests {

	private static final String PASSWORD = "Secret123";

	private static final Instant DELETE_AFTER = NOW.plus(Duration.ofDays(7));

	@Mock
	UserRepository users;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	UserSessions sessions;

	@Mock
	ApplicationEventPublisher events;

	@Test
	void deletionWaitsForTheGracePeriodAndEndsEverySession() {
		User user = givenUserWithPassword(UserFixtures.activeUser(), true);

		Instant deleteAfter = service(List.of()).requestDeletion(user.getId(), PASSWORD);

		assertThat(deleteAfter).isEqualTo(DELETE_AFTER);
		assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_DELETION);
		assertThat(user.getDeletionRequestedAt()).isEqualTo(NOW);
		verify(sessions).endAll(user.getId(), RevokeReason.ACCOUNT_DELETION);
		verify(events).publishEvent(new AccountDeletionRequested(user.getId(), DELETE_AFTER));
	}

	@Test
	void wrongPasswordChangesNothing() {
		User user = givenUserWithPassword(UserFixtures.activeUser(), false);

		assertThatThrownBy(() -> service(List.of()).requestDeletion(user.getId(), PASSWORD))
			.isInstanceOf(InvalidRequestException.class)
			.extracting(AccountDeletionServiceTests::codeOf)
			.isEqualTo("CURRENT_PASSWORD_INVALID");
		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		verifyNoInteractions(sessions, events);
	}

	@Test
	void openObligationsOfOtherModulesBlockTheDeletionAndAreListed() {
		User user = givenUserWithPassword(UserFixtures.activeUser(), true);
		AccountDeletionBlocker transactions = userId -> List.of("OPEN_TRANSACTION");
		AccountDeletionBlocker payments = userId -> List.of("PAYMENT_HELD", "PAYOUT_PENDING");

		assertThatThrownBy(() -> service(List.of(transactions, payments)).requestDeletion(user.getId(), PASSWORD))
			.isInstanceOf(ConflictException.class)
			.satisfies(ex -> {
				assertThat(codeOf(ex)).isEqualTo("ACCOUNT_DELETION_BLOCKED");
				assertThat(((BusinessException) ex).getProperties()).containsEntry("blockers",
						List.of("OPEN_TRANSACTION", "PAYMENT_HELD", "PAYOUT_PENDING"));
			});
		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void blockerWithNothingOpenLetsTheDeletionGoOn() {
		User user = givenUserWithPassword(UserFixtures.activeUser(), true);

		service(List.of(userId -> List.of())).requestDeletion(user.getId(), PASSWORD);

		assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_DELETION);
	}

	@Test
	void administratorAccountCannotBeDeleted() {
		User admin = UserFixtures.admin();
		when(users.getRequired(admin.getId())).thenReturn(admin);

		assertThatThrownBy(() -> service(List.of()).requestDeletion(admin.getId(), PASSWORD))
			.isInstanceOf(OperationNotAllowedException.class)
			.extracting(AccountDeletionServiceTests::codeOf)
			.isEqualTo("CANNOT_DELETE_ADMIN");
	}

	@Test
	void sanctionedAccountCannotRequestDeletion() {
		User user = givenUserWithPassword(UserFixtures.bannedUser(), true);

		assertThatThrownBy(() -> service(List.of()).requestDeletion(user.getId(), PASSWORD))
			.extracting(AccountDeletionServiceTests::codeOf)
			.isEqualTo("ACCOUNT_STATUS_CONFLICT");
		verifyNoInteractions(sessions, events);
	}

	private User givenUserWithPassword(User user, boolean isPasswordCorrect) {
		when(users.getRequired(user.getId())).thenReturn(user);
		when(passwordEncoder.matches(PASSWORD, UserFixtures.PASSWORD_HASH)).thenReturn(isPasswordCorrect);
		return user;
	}

	private AccountDeletionService service(List<AccountDeletionBlocker> blockers) {
		return new AccountDeletionService(users, passwordEncoder, blockers, sessions,
				IdentityParametersFixture.seeded(), events, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static String codeOf(Throwable ex) {
		return ((BusinessException) ex).getCode();
	}

}
