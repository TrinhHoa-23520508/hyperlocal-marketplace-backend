package vn.edu.uit.nearby.nearby_backend.identity.application.profile;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record PublicProfile(UUID id, String displayName, @Nullable String avatarUrl, boolean phoneVerified,
		Instant joinedAt, boolean deleted) {

}
