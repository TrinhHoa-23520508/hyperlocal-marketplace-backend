package vn.edu.uit.nearby.nearby_backend.identity.application.account;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenPurpose;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenStore;
import vn.edu.uit.nearby.nearby_backend.identity.application.port.OneTimeTokenSubject;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

/**
 * Optional email address and its verification by a signed link (FR-ID-09). Only a
 * verified address can be used to log in or reset the password.
 */
@Service
public class AccountEmailService {

	private final UserRepository users;

	private final OneTimeTokenStore tokens;

	private final AccountEmailDispatcher emails;

	private final Clock clock;

	public AccountEmailService(UserRepository users, OneTimeTokenStore tokens, AccountEmailDispatcher emails,
			Clock clock) {
		this.users = users;
		this.tokens = tokens;
		this.emails = emails;
		this.clock = clock;
	}

	/**
	 * Saves the address unverified and emails a verification link. Saving the address
	 * already verified on the account does nothing.
	 * @throws ConflictException {@code EMAIL_UNAVAILABLE} when another account uses the
	 * address; the message does not say so
	 */
	@Transactional
	public void changeEmail(UUID userId, String email) {
		EmailAddress address = EmailAddress.of(email);
		if (users.existsByEmailAndIdNot(address.value(), userId)) {
			throw new ConflictException(ErrorCodes.EMAIL_UNAVAILABLE, "This email address cannot be used.");
		}
		User user = users.getRequired(userId);
		user.changeEmail(address, clock.instant());
		if (!user.hasVerifiedEmail()) {
			emails.sendEmailVerification(user, address);
		}
	}

	/**
	 * @throws InvalidRequestException {@code EMAIL_NOT_SET} when the account has no email
	 * address; does nothing when the address is already verified
	 */
	@Transactional(readOnly = true)
	public void resendVerification(UUID userId) {
		User user = users.getRequired(userId);
		EmailAddress address = user.emailAddress()
			.orElseThrow(() -> new InvalidRequestException(ErrorCodes.EMAIL_NOT_SET,
					"Add an email address to your account first."));
		if (!user.hasVerifiedEmail()) {
			emails.sendEmailVerification(user, address);
		}
	}

	/**
	 * @throws UnprocessableRequestException {@code INVALID_VERIFICATION_TOKEN} for an
	 * unknown, used or expired link, or one sent to an address the account no longer uses
	 */
	@Transactional
	public void verifyEmail(String token) {
		OneTimeTokenSubject subject = tokens.consume(OneTimeTokenPurpose.EMAIL_VERIFICATION, token)
			.orElseThrow(AccountEmailService::invalidLink);
		boolean isVerified = users.findById(subject.userId())
			.map(user -> user.verifyEmail(EmailAddress.of(subject.boundTo()), clock.instant()))
			.orElse(false);
		if (!isVerified) {
			throw invalidLink();
		}
	}

	private static UnprocessableRequestException invalidLink() {
		return new UnprocessableRequestException(ErrorCodes.INVALID_VERIFICATION_TOKEN,
				"The verification link is invalid or has expired; request a new one.");
	}

}
