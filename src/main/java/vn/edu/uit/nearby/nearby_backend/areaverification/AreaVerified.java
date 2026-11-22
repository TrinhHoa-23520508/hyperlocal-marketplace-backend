package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.time.Instant;
import java.util.UUID;

/**
 * An area was verified or renewed and is VERIFIED until {@code expiresAt} (FR-AR-01,
 * FR-AR-04). Consumers: trading gate, listing (listings of the area take contacts again),
 * notification.
 *
 * @param isRenewal true when an existing area was renewed instead of a new one created
 */
public record AreaVerified(UUID userId, UUID areaId, Instant expiresAt, boolean isRenewal) {
}
