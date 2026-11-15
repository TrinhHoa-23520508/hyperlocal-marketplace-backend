package vn.edu.uit.nearby.nearby_backend.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * The user asked to delete the account (FR-ID-16). Logging in before {@code deleteAfter}
 * cancels the request; after it the account is anonymised.
 */
public record AccountDeletionRequested(UUID userId, Instant deleteAfter) {

}
