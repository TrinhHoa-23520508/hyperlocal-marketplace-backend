package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.MapQuery;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.DiscoveryQueries.Viewport;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * UC-DC-03 inputs from A09: the visible bounding box, the zoom level and the filters.
 */
public record MapParams(@NotNull @DecimalMin("-90") @DecimalMax("90") Double south,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double west,
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double north,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double east,
		@Parameter(description = "Map zoom level, 0-22") @NotNull @Min(0) @Max(22) Integer zoom,
		@Nullable Short categoryId, @PositiveOrZero @Nullable Long minPrice, @PositiveOrZero @Nullable Long maxPrice,
		@Nullable Boolean freeOnly) {

	public MapQuery toQuery(@Nullable UUID viewerId) {
		if (south >= north) {
			throw InvalidRequestException.invalidField("south", "south must be below north.");
		}
		if (west >= east) {
			throw InvalidRequestException.invalidField("west", "west must be left of east.");
		}
		return new MapQuery(viewerId, new Viewport(south, west, north, east), zoom, categoryId, minPrice, maxPrice,
				Boolean.TRUE.equals(freeOnly));
	}

}
