package vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Who made an attempt, for what, and which request it came from.
 *
 * @param attemptId id of the attempt row, known before the area is created so the area
 * can point back to it
 * @param userAreaId the area created or renewed; null when a first verification was
 * rejected
 * @param appVersion client version for diagnosis; cut to 20 characters when stored
 */
public record AttemptSubject(UUID attemptId, UUID userId, @Nullable UUID userAreaId, VerificationKind kind,
		UUID idempotencyKey, @Nullable String appVersion) {
}
