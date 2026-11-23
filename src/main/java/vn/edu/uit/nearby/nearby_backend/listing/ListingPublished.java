package vn.edu.uit.nearby.nearby_backend.listing;

import java.time.Instant;
import java.util.UUID;

/**
 * A listing went public (FR-LS-01). Consumed by discovery keyword alerts (R2, UC-SY-07).
 */
public record ListingPublished(UUID listingId, UUID sellerId, short categoryId, Instant publishedAt) {

}
