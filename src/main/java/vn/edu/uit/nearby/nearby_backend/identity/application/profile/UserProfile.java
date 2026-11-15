package vn.edu.uit.nearby.nearby_backend.identity.application.profile;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserRole;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

public record UserProfile(UUID id, @Nullable String phone, @Nullable String email, String displayName,
		@Nullable String avatarUrl, UserRole role, AccountStatus status, @Nullable GeoPoint homeArea,
		boolean phoneVerified, boolean emailVerified, Instant createdAt, @Nullable Instant displayNameChangeAllowedAt) {

}
