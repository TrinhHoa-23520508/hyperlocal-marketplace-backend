package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaStatus;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;

/**
 * What the owner sees of an area (screen A27). It never holds coordinates (NFR-15).
 */
public record AreaView(UUID id, int slot, String label, @Nullable String localityName, AreaStatus status,
		RangeLevel rangeLevel, boolean isActive, Instant verifiedAt, Instant expiresAt) {

	public static AreaView from(UserArea area) {
		return new AreaView(area.getId(), area.getSlot(), area.getLabel(), area.getLocalityName(), area.getStatus(),
				area.getRangeLevel(), area.isActive(), area.getVerifiedAt(), area.getExpiresAt());
	}

}
