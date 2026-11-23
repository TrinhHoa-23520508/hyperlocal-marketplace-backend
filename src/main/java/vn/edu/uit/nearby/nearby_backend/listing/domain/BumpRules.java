package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.time.Duration;

/**
 * FR-LS-10: {@code listing.bump_cooldown_hours} between two bumps of a listing and at
 * most {@code listing.bump_max_per_listing} bumps in total.
 */
public record BumpRules(Duration cooldown, int maxBumps) {

}
