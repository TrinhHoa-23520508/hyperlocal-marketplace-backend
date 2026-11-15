package vn.edu.uit.nearby.nearby_backend.identity.application.block;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record BlockedUser(UUID userId, String displayName, @Nullable String avatarUrl, Instant blockedAt) {

}
