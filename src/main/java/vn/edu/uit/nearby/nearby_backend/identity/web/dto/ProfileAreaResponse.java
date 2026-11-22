package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.identity.ProfileArea;

@Schema(description = "One of the caller's areas (FR-ID-12). It never carries coordinates (NFR-15)")
public record ProfileAreaResponse(UUID id, @Schema(example = "Nhà") String label,
		@Schema(description = "Public neighborhood name; null until known") @Nullable String localityName,
		@Schema(description = "VERIFIED | EXPIRING | EXPIRED; EXPIRED needs renewal") String status,
		@Schema(description = "Drives the feed (FR-AR-08)") boolean active, Instant expiresAt) {

	public static ProfileAreaResponse from(ProfileArea area) {
		return new ProfileAreaResponse(area.id(), area.label(), area.localityName(), area.status(), area.active(),
				area.expiresAt());
	}

}
