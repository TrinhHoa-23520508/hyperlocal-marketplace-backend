package vn.edu.uit.nearby.nearby_backend.areaverification.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaView;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaStatus;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;

@Schema(description = "An area of the caller. It never carries coordinates (NFR-15)")
public record AreaResponse(UUID id, @Schema(description = "1 or 2") int slot, @Schema(example = "Nhà") String label,
		@Schema(description = "Public neighborhood name; null until known") @Nullable String localityName,
		@Schema(description = "VERIFIED and EXPIRING unlock trading; EXPIRED needs renewal") AreaStatus status,
		RangeLevel rangeLevel, @Schema(description = "Drives the feed (FR-AR-08)") boolean active, Instant verifiedAt,
		@Schema(description = "verifiedAt + 30 days") Instant expiresAt) {

	public static AreaResponse from(AreaView area) {
		return new AreaResponse(area.id(), area.slot(), area.label(), area.localityName(), area.status(),
				area.rangeLevel(), area.isActive(), area.verifiedAt(), area.expiresAt());
	}

}
