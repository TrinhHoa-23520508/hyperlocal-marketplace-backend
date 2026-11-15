package vn.edu.uit.nearby.nearby_backend.identity;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An account was suspended until {@code until} or banned for good (FR-ID-19). Its
 * sessions are already revoked. Listing hides the user's listings; Transaction cancels
 * the open transactions without penalizing the other party; Notify drops its device
 * tokens.
 */
public record UserSanctioned(UUID userId, SanctionType type, @Nullable Instant until, Instant sanctionedAt) {

}
