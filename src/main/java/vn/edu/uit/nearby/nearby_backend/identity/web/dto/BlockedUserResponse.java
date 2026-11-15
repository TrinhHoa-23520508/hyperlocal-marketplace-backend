package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.identity.application.block.BlockedUser;

public record BlockedUserResponse(UUID userId, String displayName, @Nullable String avatarUrl, Instant blockedAt) {

	public static BlockedUserResponse from(BlockedUser user) {
		return new BlockedUserResponse(user.userId(), user.displayName(), user.avatarUrl(), user.blockedAt());
	}

}
