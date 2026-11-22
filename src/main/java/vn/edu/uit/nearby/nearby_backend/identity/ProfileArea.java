package vn.edu.uit.nearby.nearby_backend.identity;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One area as the owner's profile shows it (FR-ID-12). It never holds coordinates
 * (NFR-15).
 *
 * @param status VERIFIED, EXPIRING or EXPIRED, as the area module names it
 */
public record ProfileArea(UUID id, String label, @Nullable String localityName, String status, boolean active,
		Instant expiresAt) {

}
