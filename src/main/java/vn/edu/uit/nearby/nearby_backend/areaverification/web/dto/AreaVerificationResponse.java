package vn.edu.uit.nearby.nearby_backend.areaverification.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AreaVerificationResponse(AreaResponse area,
		@Schema(description = "True when an existing area was renewed instead of a new one created "
				+ "(the fix was within 1 500 m of it)") boolean renewal) {
}
