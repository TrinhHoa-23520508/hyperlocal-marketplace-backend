package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaLabel;

/**
 * Verify a new area (UC-AR-01), or replace an existing one when {@code replaceAreaId} is
 * given (UC-AR-03).
 *
 * @param label the user's name for the area; a default is used when absent
 */
public record VerifyAreaCommand(UUID userId, LocationSubmission submission, @Nullable AreaLabel label,
		@Nullable UUID replaceAreaId) {
}
