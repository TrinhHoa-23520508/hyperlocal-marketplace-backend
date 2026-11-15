package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;

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

/**
 * Account identified by a verified Vietnamese mobile number (ADR-18); the email address
 * is an optional second channel that counts only once verified (FR-ID-09).
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

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

	@Column(name = "avatar_key")
	private @Nullable String avatarKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Getter(AccessLevel.NONE)
	@Column(name = "home_area", columnDefinition = "geography(Point,4326)")
	private @Nullable Point homeArea;

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

	public boolean isLocked() {
		return status.isLocked();
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

	public void ensureCanLogIn() {
		status.ensureCanLogIn();
	}

	public void changePassword(String newPasswordHash, Instant now) {
		passwordHash = newPasswordHash;
		passwordChangedAt = now;
		touch(now);
	}

	public void updateProfile(String newDisplayName, @Nullable HomeArea newHomeArea, Instant now) {
		displayName = newDisplayName;
		homeArea = newHomeArea == null ? null : newHomeArea.toPoint();
		touch(now);
	}

	public Optional<HomeArea> homeArea() {
		return Optional.ofNullable(homeArea).map(HomeArea::from);
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

	public void lock(Instant now) {
		status = status.afterLock();
		touch(now);
	}

	public void unlock(Instant now) {
		status = status.afterUnlock();
		touch(now);
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
