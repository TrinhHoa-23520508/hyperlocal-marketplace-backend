package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.time.Instant;
import java.util.UUID;

/**
 * An area reached its expiry and no longer grants trading (FR-AR-03). Its listings stay
 * visible but take no new chats or offers until it is renewed.
 */
public record AreaExpired(UUID userId, UUID areaId, Instant expiredAt) {
}
