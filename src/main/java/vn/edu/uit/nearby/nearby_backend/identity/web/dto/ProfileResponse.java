package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.identity.application.profile.UserProfile;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserRole;

public record ProfileResponse(UUID id, @Nullable String phone, @Nullable String email, String displayName,
		@Nullable String avatarUrl, UserRole role, AccountStatus status,
		@Schema(description = "Areas that are not removed, in slot order; empty before the first verification") List<ProfileAreaResponse> areas,
		boolean phoneVerified, boolean emailVerified, Instant createdAt, @Nullable Instant displayNameChangeAllowedAt) {

	public static ProfileResponse from(UserProfile profile) {
		return new ProfileResponse(profile.id(), profile.phone(), profile.email(), profile.displayName(),
				profile.avatarUrl(), profile.role(), profile.status(),
				profile.areas().stream().map(ProfileAreaResponse::from).toList(), profile.phoneVerified(),
				profile.emailVerified(), profile.createdAt(), profile.displayNameChangeAllowedAt());
	}

}
