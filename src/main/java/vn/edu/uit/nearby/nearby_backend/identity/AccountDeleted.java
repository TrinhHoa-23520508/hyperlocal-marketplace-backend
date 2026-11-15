package vn.edu.uit.nearby.nearby_backend.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * The account was anonymised (FR-ID-16, UC-SY-12). Other modules anonymise or drop the
 * personal data they keep about the user.
 */
public record AccountDeleted(UUID userId, Instant deletedAt) {

}
