package vn.edu.uit.nearby.nearby_backend.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * A suspension ended or an admin lifted a sanction; the account is ACTIVE again.
 */
public record SanctionLifted(UUID userId, Instant liftedAt) {

}
