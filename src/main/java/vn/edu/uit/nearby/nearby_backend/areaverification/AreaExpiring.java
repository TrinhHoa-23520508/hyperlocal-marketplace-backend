package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.time.Instant;
import java.util.UUID;

/**
 * An area has at most {@code area.renewal_reminder_days} left (UC-SY-05). Notification
 * sends the renewal reminder that opens A28.
 */
public record AreaExpiring(UUID userId, UUID areaId, Instant expiresAt) {
}
