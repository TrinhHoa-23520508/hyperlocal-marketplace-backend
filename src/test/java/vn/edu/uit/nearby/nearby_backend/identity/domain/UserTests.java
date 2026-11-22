package vn.edu.uit.nearby.nearby_backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

class UserTests {

	private static final Instant LATER = NOW.plus(Duration.ofHours(1));

	private static final Duration RENAME_INTERVAL = Duration.ofDays(30);

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
	void suspensionKeepsTheEndAndIsOverFromThatInstant() {
		User user = UserFixtures.activeUser();
		Instant until = NOW.plus(Duration.ofDays(1));

		user.suspend(until, NOW);

		assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
		assertThat(user.getSuspendedUntil()).isEqualTo(until);
		assertThat(user.isSanctioned()).isTrue();
		assertThat(user.isSuspensionOverAt(until.minusMillis(1))).isFalse();
		assertThat(user.isSuspensionOverAt(until)).isTrue();
	}

	@Test
	void banningASuspendedAccountDropsTheSuspensionEnd() {
		User user = UserFixtures.suspendedUntil(LATER);

		user.ban(NOW);

		assertThat(user.getStatus()).isEqualTo(AccountStatus.BANNED);
		assertThat(user.getSuspendedUntil()).isNull();
		assertThat(user.isSuspensionOverAt(LATER)).isFalse();
	}

	@Test
	void liftingASanctionReturnsToActive() {
		User user = UserFixtures.bannedUser();

		user.liftSanction(LATER);

		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(user.isSanctioned()).isFalse();
		assertThat(user.getUpdatedAt()).isEqualTo(LATER);
	}

	@Test
	void banningTwiceIsAConflict() {
		User user = UserFixtures.bannedUser();

		assertThatThrownBy(() -> user.ban(LATER)).isInstanceOf(ConflictException.class);
	}

	@Test
	void loginEndsAnExpiredSuspension() {
		User user = UserFixtures.suspendedUntil(LATER);

		assertThat(user.reactivateOnLogin(LATER)).contains(LoginReactivation.SUSPENSION_ENDED);
		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(user.getSuspendedUntil()).isNull();
	}

	@Test
	void loginDuringASuspensionChangesNothing() {
		User user = UserFixtures.suspendedUntil(LATER);

		assertThat(user.reactivateOnLogin(NOW)).isEmpty();
		assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
	}

	@Test
	void loginCancelsAPendingDeletion() {
		User user = UserFixtures.activeUser();
		user.requestDeletion(NOW);
		assertThat(user.getDeletionRequestedAt()).isEqualTo(NOW);

		assertThat(user.reactivateOnLogin(LATER)).contains(LoginReactivation.DELETION_CANCELLED);
		assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(user.getDeletionRequestedAt()).isNull();
	}

	@Test
	void loginOfAnActiveAccountChangesNothing() {
		assertThat(UserFixtures.activeUser().reactivateOnLogin(LATER)).isEmpty();
	}

	@Test
	void anonymisationRemovesEveryPersonalField() {
		User user = UserFixtures.userWithVerifiedEmail();
		user.replaceAvatar("avatars/a.jpg", NOW);
		user.requestDeletion(NOW);

		assertThat(user.anonymise("unusable", LATER)).contains("avatars/a.jpg");

		assertThat(user.getStatus()).isEqualTo(AccountStatus.DELETED);
		assertThat(user.isDeleted()).isTrue();
		assertThat(user.phoneNumber()).isEmpty();
		assertThat(user.hasVerifiedPhone()).isFalse();
		assertThat(user.emailAddress()).isEmpty();
		assertThat(user.hasVerifiedEmail()).isFalse();
		assertThat(user.getPasswordHash()).isEqualTo("unusable");
		assertThat(user.getDisplayName()).isEqualTo(User.DELETED_DISPLAY_NAME);
		assertThat(user.getAvatarKey()).isNull();
		assertThat(user.getAnonymisedAt()).isEqualTo(LATER);
	}

	@Test
	void onlyAPendingDeletionCanBeAnonymised() {
		User user = UserFixtures.activeUser();

		assertThatThrownBy(() -> user.anonymise("unusable", LATER)).isInstanceOf(ConflictException.class);
		assertThat(user.phoneNumber()).isPresent();
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
	void firstRenameIsAllowedAndStartsTheWaitingPeriod() {
		User user = UserFixtures.activeUser();

		user.rename("Binh", RENAME_INTERVAL, LATER);

		assertThat(user.getDisplayName()).isEqualTo("Binh");
		assertThat(user.getDisplayNameChangedAt()).isEqualTo(LATER);
		assertThat(user.nextRenameAllowedAt(RENAME_INTERVAL)).contains(LATER.plus(RENAME_INTERVAL));
	}

	@Test
	void renamingWithinTheIntervalIsRejectedWithTheNextAllowedTime() {
		User user = UserFixtures.activeUser();
		user.rename("Binh", RENAME_INTERVAL, NOW);
		Instant nextAllowedAt = NOW.plus(RENAME_INTERVAL);

		assertThatThrownBy(() -> user.rename("Chi", RENAME_INTERVAL, nextAllowedAt.minusMillis(1)))
			.isInstanceOf(UnprocessableRequestException.class)
			.satisfies(ex -> {
				assertThat(((BusinessException) ex).getCode()).isEqualTo("NAME_CHANGE_TOO_SOON");
				assertThat(((BusinessException) ex).getProperties()).containsEntry("nextAllowedAt", nextAllowedAt);
			});
		assertThat(user.getDisplayName()).isEqualTo("Binh");
	}

	@Test
	void renamingIsAllowedAgainOnceTheIntervalIsOver() {
		User user = UserFixtures.activeUser();
		user.rename("Binh", RENAME_INTERVAL, NOW);

		user.rename("Chi", RENAME_INTERVAL, NOW.plus(RENAME_INTERVAL));

		assertThat(user.getDisplayName()).isEqualTo("Chi");
	}

	@Test
	void sendingTheCurrentNameIsNotARename() {
		User user = UserFixtures.activeUser();
		user.rename("Binh", RENAME_INTERVAL, NOW);

		user.rename("Binh", RENAME_INTERVAL, LATER);

		assertThat(user.getDisplayNameChangedAt()).isEqualTo(NOW);
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
