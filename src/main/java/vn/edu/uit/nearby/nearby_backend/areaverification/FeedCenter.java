package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * Center and radius of a user's feed. The center is the area's grid-cell center; it is
 * for server-side queries only and is never returned to another user (NFR-15).
 */
public record FeedCenter(UUID areaId, GeoPoint center, int radiusMeters) {
}
