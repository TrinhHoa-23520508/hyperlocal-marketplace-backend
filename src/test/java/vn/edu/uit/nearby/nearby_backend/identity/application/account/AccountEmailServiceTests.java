package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenStore;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenSubject;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

@ExtendWith(MockitoExtension.class)
class AccountEmailServiceTests {

	private static final EmailAddress EMAIL = EmailAddress.of(UserFixtures.EMAIL);

	private static final String TOKEN = "verify-token";

	@Mock
	UserRepository users;

	@Mock
	OneTimeTokenStore tokens;

	@Mock
	AccountEmailDispatcher emails;

	private AccountEmailService service() {
		return new AccountEmailService(users, tokens, emails, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void newAddressIsSavedLowercaseAndAVerificationLinkIsSent() {
		User user = UserFixtures.activeUser();
		when(users.getRequired(user.getId())).thenReturn(user);

		service().changeEmail(user.getId(), " An.Nguyen@Example.com ");

		assertThat(user.emailAddress()).contains(EMAIL);
		assertThat(user.hasVerifiedEmail()).isFalse();
		verify(emails).sendEmailVerification(user, EMAIL);
	}

	@Test
	void savingTheVerifiedAddressAgainSendsNothing() {
		User user = UserFixtures.userWithVerifiedEmail();
		when(users.getRequired(user.getId())).thenReturn(user);

		service().changeEmail(user.getId(), UserFixtures.EMAIL);

		assertThat(user.hasVerifiedEmail()).isTrue();
		verifyNoInteractions(emails);
	}

	@Test
	void addressOfAnotherAccountIsRefusedWithAGenericError() {
		User user = UserFixtures.activeUser();
		when(users.existsByEmailAndIdNot(UserFixtures.EMAIL, user.getId())).thenReturn(true);

		assertThatThrownBy(() -> service().changeEmail(user.getId(), UserFixtures.EMAIL))
			.isInstanceOf(ConflictException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("EMAIL_UNAVAILABLE");
		verifyNoInteractions(emails);
	}

	@Test
	void resendGoesToTheUnverifiedAddress() {
		User user = UserFixtures.userWithUnverifiedEmail();
		when(users.getRequired(user.getId())).thenReturn(user);

		service().resendVerification(user.getId());

		verify(emails).sendEmailVerification(user, EMAIL);
	}

	@Test
	void resendForAVerifiedAddressSendsNothing() {
		User user = UserFixtures.userWithVerifiedEmail();
		when(users.getRequired(user.getId())).thenReturn(user);

		service().resendVerification(user.getId());

		verifyNoInteractions(emails);
	}

	@Test
	void resendWithoutAnAddressIsRejected() {
		User user = UserFixtures.activeUser();
		when(users.getRequired(user.getId())).thenReturn(user);

		assertThatThrownBy(() -> service().resendVerification(user.getId())).isInstanceOf(InvalidRequestException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("EMAIL_NOT_SET");
	}

	@Test
	void validLinkVerifiesTheAddress() {
		User user = UserFixtures.userWithUnverifiedEmail();
		givenToken(new OneTimeTokenSubject(user.getId(), UserFixtures.EMAIL), user);

		service().verifyEmail(TOKEN);

		assertThat(user.hasVerifiedEmail()).isTrue();
	}

	@Test
	void linkForAnAddressNoLongerOnTheAccountIsRejected() {
		User user = UserFixtures.userWithUnverifiedEmail();
		givenToken(new OneTimeTokenSubject(user.getId(), "old@example.com"), user);

		assertThatThrownBy(() -> service().verifyEmail(TOKEN)).isInstanceOf(UnprocessableRequestException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("INVALID_VERIFICATION_TOKEN");
		assertThat(user.hasVerifiedEmail()).isFalse();
	}

	@Test
	void unknownOrUsedLinkIsRejected() {
		when(tokens.consume(OneTimeTokenPurpose.EMAIL_VERIFICATION, TOKEN)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().verifyEmail(TOKEN)).isInstanceOf(UnprocessableRequestException.class);
	}

	private void givenToken(OneTimeTokenSubject subject, User user) {
		when(tokens.consume(OneTimeTokenPurpose.EMAIL_VERIFICATION, TOKEN)).thenReturn(Optional.of(subject));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));
	}

}
