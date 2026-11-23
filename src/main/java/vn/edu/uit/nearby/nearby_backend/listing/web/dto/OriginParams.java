package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.SearchOrigin;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * The optional center of FR-DC-04: both coordinates or neither.
 */
public interface OriginParams {

	@Nullable Double lat();

	@Nullable Double lon();

	@Nullable String rangeLevel();

	default @Nullable GeoPoint point() {
		if (lat() == null && lon() == null) {
			return null;
		}
		if (lat() == null || lon() == null) {
			throw InvalidRequestException.invalidField(lat() == null ? "lat" : "lon",
					"Send both lat and lon, or neither to use your active area.");
		}
		return new GeoPoint(lat(), lon());
	}

	default SearchOrigin origin(@Nullable UUID viewerId) {
		return new SearchOrigin(viewerId, point(), rangeLevel());
	}

}
