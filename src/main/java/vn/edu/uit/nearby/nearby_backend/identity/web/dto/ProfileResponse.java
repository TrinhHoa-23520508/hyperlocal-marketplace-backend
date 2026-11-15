package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.identity.application.profile.UserProfile;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserRole;

public record ProfileResponse(UUID id, @Nullable String phone, @Nullable String email, String displayName,
		@Nullable String avatarUrl, UserRole role, AccountStatus status, @Nullable HomeAreaPayload homeArea,
		boolean phoneVerified, boolean emailVerified, Instant createdAt, @Nullable Instant displayNameChangeAllowedAt) {

	public static ProfileResponse from(UserProfile profile) {
		HomeAreaPayload homeArea = profile.homeArea() == null ? null : HomeAreaPayload.from(profile.homeArea());
		return new ProfileResponse(profile.id(), profile.phone(), profile.email(), profile.displayName(),
				profile.avatarUrl(), profile.role(), profile.status(), homeArea, profile.phoneVerified(),
				profile.emailVerified(), profile.createdAt(), profile.displayNameChangeAllowedAt());
	}

}
