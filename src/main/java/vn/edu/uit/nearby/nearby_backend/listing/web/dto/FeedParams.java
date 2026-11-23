package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * UC-DC-01 inputs: a center (GPS or picked point, or the active area when absent), a
 * range level for that point, and the page cursor.
 */
public record FeedParams(@Parameter(
		description = "Latitude of a GPS position or picked point; with lon") @DecimalMin("-90") @DecimalMax("90") @Nullable Double lat,
		@Parameter(description = "Longitude; with lat") @DecimalMin("-180") @DecimalMax("180") @Nullable Double lon,
		@Parameter(
				description = "NEAR, MEDIUM or FAR (2/4/6 km) for a point; default MEDIUM") @Nullable String rangeLevel,
		@Parameter(description = "nextCursor of the previous page") @Nullable String cursor) implements OriginParams {

}
