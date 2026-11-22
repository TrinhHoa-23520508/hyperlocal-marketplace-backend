package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

/**
 * Account identified by a verified Vietnamese mobile number (ADR-18); the email address
 * is an optional second channel that counts only once verified (FR-ID-09).
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	public static final String DELETED_DISPLAY_NAME = "Người dùng đã xoá";

	@Id
	private UUID id;

	@Getter(AccessLevel.NONE)
	@Column(name = "phone_e164")
	private @Nullable String phoneE164;

	@Column(name = "phone_verified_at")
	private @Nullable Instant phoneVerifiedAt;

	@Getter(AccessLevel.NONE)
	@Column
	private @Nullable String email;

	@Column(name = "email_verified_at")
	private @Nullable Instant emailVerifiedAt;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "password_changed_at")
	private @Nullable Instant passwordChangedAt;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "display_name_changed_at")
	private @Nullable Instant displayNameChangedAt;

	@Column(name = "avatar_key")
	private @Nullable String avatarKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Column(name = "suspended_until")
	private @Nullable Instant suspendedUntil;

	@Column(name = "deletion_requested_at")
	private @Nullable Instant deletionRequestedAt;

	@Column(name = "anonymised_at")
	private @Nullable Instant anonymisedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	private User(String passwordHash, String displayName, UserRole role, Instant now) {
		this.id = UUID.randomUUID();
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.role = role;
		this.status = AccountStatus.ACTIVE;
		this.createdAt = truncated(now);
		this.updatedAt = this.createdAt;
	}

	/**
	 * Creates the account after the phone number was proven by a one-time code
	 * (FR-ID-01).
	 */
	public static User register(PhoneNumber verifiedPhone, String passwordHash, String displayName, Instant now) {
		User user = new User(passwordHash, displayName, UserRole.USER, now);
		user.phoneE164 = verifiedPhone.value();
		user.phoneVerifiedAt = now;
		return user;
	}

	public static User administrator(EmailAddress email, String passwordHash, String displayName, Instant now) {
		User admin = new User(passwordHash, displayName, UserRole.ADMIN, now);
		admin.email = email.value();
		admin.emailVerifiedAt = now;
		return admin;
	}

	public Optional<PhoneNumber> phoneNumber() {
		return Optional.ofNullable(phoneE164).map(PhoneNumber::of);
	}

	public Optional<EmailAddress> emailAddress() {
		return Optional.ofNullable(email).map(EmailAddress::of);
	}

	public Optional<EmailAddress> verifiedEmailAddress() {
		return hasVerifiedEmail() ? emailAddress() : Optional.empty();
	}

	public boolean isAdmin() {
		return role == UserRole.ADMIN;
	}

	public boolean hasVerifiedPhone() {
		return phoneVerifiedAt != null;
	}

	public boolean hasVerifiedEmail() {
		return email != null && emailVerifiedAt != null;
	}

	public boolean isSanctioned() {
		return status.isSanctioned();
	}

	public boolean isDeleted() {
		return status == AccountStatus.DELETED;
	}

	/**
	 * Sets a new, unverified email address. Setting the address already on file changes
	 * nothing, so a verified address stays verified.
	 */
	public void changeEmail(EmailAddress newEmail, Instant now) {
		if (newEmail.value().equals(email)) {
			return;
		}
		email = newEmail.value();
		emailVerifiedAt = null;
		touch(now);
	}

	/**
	 * @return true when {@code address} is the address on file and is now verified; false
	 * when the address changed after the verification link was sent
	 */
	public boolean verifyEmail(EmailAddress address, Instant now) {
		if (!address.value().equals(email)) {
			return false;
		}
		if (emailVerifiedAt == null) {
			emailVerifiedAt = now;
			touch(now);
		}
		return true;
	}

	public void changePassword(String newPasswordHash, Instant now) {
		passwordHash = newPasswordHash;
		passwordChangedAt = now;
		touch(now);
	}

	/**
	 * Renames the account at most once per {@code minimumInterval} (FR-ID-11). Sending
	 * the current name again is not a change.
	 * @throws UnprocessableRequestException {@code NAME_CHANGE_TOO_SOON} with
	 * {@code nextAllowedAt}
	 */
	public void rename(String newDisplayName, Duration minimumInterval, Instant now) {
		if (newDisplayName.equals(displayName)) {
			return;
		}
		Optional<Instant> nextAllowedAt = nextRenameAllowedAt(minimumInterval).filter(now::isBefore);
		if (nextAllowedAt.isPresent()) {
			throw new UnprocessableRequestException(ErrorCodes.NAME_CHANGE_TOO_SOON,
					"The display name can be changed again later.", Map.of("nextAllowedAt", nextAllowedAt.get()));
		}
		displayName = newDisplayName;
		displayNameChangedAt = now;
		touch(now);
	}

	public Optional<Instant> nextRenameAllowedAt(Duration minimumInterval) {
		return Optional.ofNullable(displayNameChangedAt).map(changedAt -> changedAt.plus(minimumInterval));
	}

	/**
	 * @return the key of the avatar that was replaced, so the caller can delete it
	 */
	public Optional<String> replaceAvatar(String newAvatarKey, Instant now) {
		Optional<String> previous = Optional.ofNullable(avatarKey).filter(key -> !key.equals(newAvatarKey));
		avatarKey = newAvatarKey;
		touch(now);
		return previous;
	}

	/**
	 * @return the key of the removed avatar, so the caller can delete it
	 */
	public Optional<String> removeAvatar(Instant now) {
		Optional<String> previous = Optional.ofNullable(avatarKey);
		avatarKey = null;
		touch(now);
		return previous;
	}

	public void suspend(Instant until, Instant now) {
		status = status.afterSuspension();
		suspendedUntil = until;
		touch(now);
	}

	public void ban(Instant now) {
		status = status.afterBan();
		suspendedUntil = null;
		touch(now);
	}

	public void liftSanction(Instant now) {
		status = status.afterSanctionLifted();
		suspendedUntil = null;
		touch(now);
	}

	public boolean isSuspensionOverAt(Instant now) {
		return suspendedUntil != null && !now.isBefore(suspendedUntil);
	}

	public void requestDeletion(Instant now) {
		status = status.afterDeletionRequested();
		deletionRequestedAt = now;
		touch(now);
	}

	public void cancelDeletion(Instant now) {
		status = status.afterDeletionCancelled();
		deletionRequestedAt = null;
		touch(now);
	}

	/**
	 * Lifts an expired suspension or cancels a pending deletion, the two states a
	 * successful login ends (FR-ID-03, FR-ID-19).
	 */
	public Optional<LoginReactivation> reactivateOnLogin(Instant now) {
		if (isSuspensionOverAt(now)) {
			liftSanction(now);
			return Optional.of(LoginReactivation.SUSPENSION_ENDED);
		}
		if (status == AccountStatus.PENDING_DELETION) {
			cancelDeletion(now);
			return Optional.of(LoginReactivation.DELETION_CANCELLED);
		}
		return Optional.empty();
	}

	/**
	 * Removes every personal field once the deletion grace period is over (FR-ID-16). The
	 * password is replaced by one nobody knows.
	 * @return the key of the removed avatar, so the caller can delete it
	 */
	public Optional<String> anonymise(String unusablePasswordHash, Instant now) {
		status = status.afterAnonymisation();
		Optional<String> previousAvatar = Optional.ofNullable(avatarKey);
		phoneE164 = null;
		phoneVerifiedAt = null;
		email = null;
		emailVerifiedAt = null;
		passwordHash = unusablePasswordHash;
		displayName = DELETED_DISPLAY_NAME;
		avatarKey = null;
		anonymisedAt = now;
		touch(now);
		return previousAvatar;
	}

	private void touch(Instant now) {
		updatedAt = now;
	}

	// Cursor pagination encodes createdAt in milliseconds; storing more precision would
	// make the cursor skip rows created within the same millisecond.
	private static Instant truncated(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MILLIS);
	}

}
