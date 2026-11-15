package vn.edu.uit.nearby.nearby_backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

class UserTests {

	private static final Instant LATER = NOW.plus(Duration.ofHours(1));

	private static final EmailAddress EMAIL = EmailAddress.of(UserFixtures.EMAIL);

	private static final EmailAddress OTHER_EMAIL = EmailAddress.of("binh@example.com");

	@Test
	void registeredUserIsActiveWithAVerifiedPhoneAndNoEmail() {
		User user = User.register(PhoneNumber.of("0912 345 678"), "hash", "An", NOW);

		assertThat(user.getId()).isNotNull();
		assertThat(user.phoneNumber()).contains(PhoneNumber.of(UserFixtures.PHONE));
		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertThat(user.hasVerifiedPhone()).isTrue();
		assertThat(user.getPhoneVerifiedAt()).isEqualTo(NOW);
		assertThat(user.emailAddress()).isEmpty();
		assertThat(user.hasVerifiedEmail()).isFalse();
		assertThat(user.isAdmin()).isFalse();
	}

	@Test
	void creationTimeIsTruncatedToMillisecondsForCursorPagination() {
		User user = User.register(PhoneNumber.of(UserFixtures.PHONE), "hash", "An",
				Instant.parse("2026-10-18T03:00:00.123456789Z"));

		assertThat(user.getCreatedAt()).isEqualTo(Instant.parse("2026-10-18T03:00:00.123Z"));
	}

	@Test
	void administratorHasAVerifiedEmailAndNoPhone() {
		User admin = UserFixtures.admin();

		assertThat(admin.isAdmin()).isTrue();
		assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(admin.hasVerifiedEmail()).isTrue();
		assertThat(admin.phoneNumber()).isEmpty();
		assertThat(admin.hasVerifiedPhone()).isFalse();
	}

	@Test
	void newEmailIsStoredUnverified() {
		User user = UserFixtures.activeUser();

		user.changeEmail(EMAIL, LATER);

		assertThat(user.emailAddress()).contains(EMAIL);
		assertThat(user.hasVerifiedEmail()).isFalse();
		assertThat(user.verifiedEmailAddress()).isEmpty();
		assertThat(user.getUpdatedAt()).isEqualTo(LATER);
	}

	@Test
	void verifyingTheAddressOnFileKeepsTheFirstVerificationTime() {
		User user = UserFixtures.userWithUnverifiedEmail();

		assertThat(user.verifyEmail(EMAIL, NOW)).isTrue();
		assertThat(user.verifyEmail(EMAIL, LATER)).isTrue();

		assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
		assertThat(user.verifiedEmailAddress()).contains(EMAIL);
	}

	@Test
	void linkSentToAPreviousAddressDoesNotVerifyTheNewOne() {
		User user = UserFixtures.userWithUnverifiedEmail();
		user.changeEmail(OTHER_EMAIL, NOW);

		assertThat(user.verifyEmail(EMAIL, LATER)).isFalse();

		assertThat(user.hasVerifiedEmail()).isFalse();
	}

	@Test
	void changingToAnotherAddressDropsTheVerification() {
		User user = UserFixtures.userWithVerifiedEmail();

		user.changeEmail(OTHER_EMAIL, LATER);

		assertThat(user.hasVerifiedEmail()).isFalse();
		assertThat(user.emailAddress()).contains(OTHER_EMAIL);
	}

	@Test
	void savingTheSameVerifiedAddressKeepsItVerified() {
		User user = UserFixtures.userWithVerifiedEmail();

		user.changeEmail(EMAIL, LATER);

		assertThat(user.hasVerifiedEmail()).isTrue();
	}

	@Test
	void activeUserCanLogIn() {
		assertThatCode(UserFixtures.activeUser()::ensureCanLogIn).doesNotThrowAnyException();
	}

	@Test
	void lockedUserCannotLogIn() {
		assertThatThrownBy(UserFixtures.lockedUser()::ensureCanLogIn).isInstanceOf(OperationNotAllowedException.class);
	}

	@Test
	void lockAndUnlockRoundTripReturnsToActive() {
		User user = UserFixtures.activeUser();

		user.lock(NOW);
		assertThat(user.isLocked()).isTrue();
		user.unlock(LATER);

		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(user.getUpdatedAt()).isEqualTo(LATER);
	}

	@Test
	void lockingTwiceIsAConflict() {
		User user = UserFixtures.lockedUser();

		assertThatThrownBy(() -> user.lock(LATER)).isInstanceOf(ConflictException.class);
	}

	@Test
	void changePasswordReplacesTheHashAndRecordsWhen() {
		User user = UserFixtures.activeUser();

		user.changePassword("new-hash", LATER);

		assertThat(user.getPasswordHash()).isEqualTo("new-hash");
		assertThat(user.getPasswordChangedAt()).isEqualTo(LATER);
		assertThat(user.getUpdatedAt()).isEqualTo(LATER);
	}

	@Test
	void updateProfileStoresDisplayNameAndHomeArea() {
		User user = UserFixtures.activeUser();
		HomeArea homeArea = HomeArea.roundedFrom(new GeoPoint(10.8701, 106.8031));

		user.updateProfile("Binh", homeArea, LATER);

		assertThat(user.getDisplayName()).isEqualTo("Binh");
		assertThat(user.homeArea()).contains(homeArea);
	}

	@Test
	void updateProfileWithoutHomeAreaClearsIt() {
		User user = UserFixtures.activeUser();
		user.updateProfile("An", HomeArea.roundedFrom(new GeoPoint(10.87, 106.8)), NOW);

		user.updateProfile("An", null, LATER);

		assertThat(user.homeArea()).isEmpty();
	}

	@Test
	void replacingAnAvatarReturnsThePreviousKeyForCleanup() {
		User user = UserFixtures.activeUser();

		assertThat(user.replaceAvatar("avatars/a.jpg", NOW)).isEmpty();
		assertThat(user.replaceAvatar("avatars/b.jpg", LATER)).contains("avatars/a.jpg");
		assertThat(user.getAvatarKey()).isEqualTo("avatars/b.jpg");
	}

	@Test
	void replacingAnAvatarWithTheSameKeyDoesNotDiscardIt() {
		User user = UserFixtures.activeUser();
		user.replaceAvatar("avatars/a.jpg", NOW);

		assertThat(user.replaceAvatar("avatars/a.jpg", LATER)).isEmpty();
	}

	@Test
	void removingTheAvatarReturnsItsKey() {
		User user = UserFixtures.activeUser();
		user.replaceAvatar("avatars/a.jpg", NOW);

		assertThat(user.removeAvatar(LATER)).contains("avatars/a.jpg");
		assertThat(user.getAvatarKey()).isNull();
		assertThat(user.removeAvatar(LATER)).isEmpty();
	}

}
