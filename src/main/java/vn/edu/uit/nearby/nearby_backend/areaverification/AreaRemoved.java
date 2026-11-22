package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.time.Instant;
import java.util.UUID;

/**
 * The user removed or replaced an area (FR-AR-07). Listing hides the ACTIVE listings of
 * that area with {@code hidden_reason = AREA_REMOVED}.
 */
public record AreaRemoved(UUID userId, UUID areaId, Instant removedAt) {
}
