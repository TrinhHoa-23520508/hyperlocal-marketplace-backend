package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.edu.uit.nearby.nearby_backend.identity.application.account.PasswordChangeService.ChangePasswordCommand;
import vn.edu.uit.nearby.nearby_backend.identity.application.auth.UserSessions;
import vn.edu.uit.nearby.nearby_backend.identity.domain.RevokeReason;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.domain.password.WeakPasswordException;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTests {

	private static final UUID SESSION_ID = UUID.randomUUID();

	private final User user = UserFixtures.activeUser();

	@Mock
	UserRepository users;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	UserSessions sessions;

	@BeforeEach
	void givenTheUser() {
		when(users.getRequired(user.getId())).thenReturn(user);
	}

	private PasswordChangeService service() {
		return new PasswordChangeService(users, PasswordPolicyFixture.standard(), passwordEncoder, sessions,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void newPasswordIsSavedAndOnlyOtherSessionsAreEnded() {
		givenCurrentPasswordIs("Secret123");
		when(passwordEncoder.encode("NewSecret456")).thenReturn("new-hash");

		service().changePassword(new ChangePasswordCommand(user.getId(), SESSION_ID, "Secret123", "NewSecret456"));

		assertThat(user.getPasswordHash()).isEqualTo("new-hash");
		assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
		verify(sessions).endAllExcept(user.getId(), SESSION_ID, RevokeReason.PASSWORD_CHANGED);
	}

	@Test
	void withoutAKnownSessionEverySessionIsEnded() {
		givenCurrentPasswordIs("Secret123");
		when(passwordEncoder.encode("NewSecret456")).thenReturn("new-hash");

		service().changePassword(new ChangePasswordCommand(user.getId(), null, "Secret123", "NewSecret456"));

		verify(sessions).endAll(user.getId(), RevokeReason.PASSWORD_CHANGED);
	}

	@Test
	void wrongCurrentPasswordIsRejected() {
		when(passwordEncoder.matches("Wrong123", UserFixtures.PASSWORD_HASH)).thenReturn(false);

		assertThatThrownBy(() -> service()
			.changePassword(new ChangePasswordCommand(user.getId(), SESSION_ID, "Wrong123", "NewSecret456")))
			.isInstanceOf(InvalidRequestException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("CURRENT_PASSWORD_INVALID");
		verifyNoInteractions(sessions);
	}

	@Test
	void reusingTheCurrentPasswordIsRejected() {
		givenCurrentPasswordIs("Secret123");

		assertThatThrownBy(() -> service()
			.changePassword(new ChangePasswordCommand(user.getId(), SESSION_ID, "Secret123", "Secret123")))
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("PASSWORD_REUSED");
	}

	@Test
	void weakNewPasswordIsRejected() {
		givenCurrentPasswordIs("Secret123");

		assertThatThrownBy(() -> service()
			.changePassword(new ChangePasswordCommand(user.getId(), SESSION_ID, "Secret123", "short")))
			.isInstanceOf(WeakPasswordException.class);
		verifyNoInteractions(sessions);
	}

	private void givenCurrentPasswordIs(String password) {
		when(passwordEncoder.matches(password, UserFixtures.PASSWORD_HASH)).thenReturn(true);
	}

}
