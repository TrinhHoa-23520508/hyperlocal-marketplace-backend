package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.identity.application.profile.PublicProfile;

/**
 * @param phoneVerified shown as the verified phone badge
 * @param deleted the account was deleted; only the anonymised name is shown
 */
public record PublicProfileResponse(UUID id, String displayName, @Nullable String avatarUrl, boolean phoneVerified,
		Instant joinedAt, boolean deleted,
		@Schema(description = "Area label: the public neighborhood name, never a coordinate (FR-ID-13); "
				+ "null when unknown, when no area is valid, or for a deleted account") @Nullable String localityName) {

	public static PublicProfileResponse from(PublicProfile profile) {
		return new PublicProfileResponse(profile.id(), profile.displayName(), profile.avatarUrl(),
				profile.phoneVerified(), profile.joinedAt(), profile.deleted(), profile.localityName());
	}

}
