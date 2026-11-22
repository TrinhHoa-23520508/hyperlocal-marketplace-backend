package vn.edu.uit.nearby.nearby_backend.areaverification.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param rangeLevel a string, not an enum, so a value outside the set answers
 * {@code 400 INVALID_RANGE} as FR-AR-08 requires instead of a JSON parse error
 */
public record SelectActiveAreaRequest(@NotNull UUID areaId,
		@Schema(description = "NEAR 2 km, MEDIUM 4 km or FAR 6 km", allowableValues = {
				"NEAR", "MEDIUM", "FAR" }, example = "MEDIUM") @NotBlank String rangeLevel){
}
