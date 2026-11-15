package vn.edu.uit.nearby.nearby_backend.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * A new account with a verified phone number was created (FR-ID-01). Reputation starts
 * the user's snapshot at 50 / NEW.
 */
public record UserRegistered(UUID userId, Instant registeredAt) {

}
