package vn.edu.uit.nearby.nearby_backend.identity.application.admin;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserRole;

public record AdminUserView(UUID id, @Nullable String phone, @Nullable String email, String displayName, UserRole role,
		AccountStatus status, @Nullable Instant suspendedUntil, @Nullable Instant deletionRequestedAt,
		boolean phoneVerified, boolean emailVerified, Instant createdAt, Instant updatedAt) {

	static AdminUserView of(User user) {
		return new AdminUserView(user.getId(), user.phoneNumber().map(PhoneNumber::value).orElse(null),
				user.emailAddress().map(EmailAddress::value).orElse(null), user.getDisplayName(), user.getRole(),
				user.getStatus(), user.getSuspendedUntil(), user.getDeletionRequestedAt(), user.hasVerifiedPhone(),
				user.hasVerifiedEmail(), user.getCreatedAt(), user.getUpdatedAt());
	}

}
