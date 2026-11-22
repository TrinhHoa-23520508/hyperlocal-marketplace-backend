package vn.edu.uit.nearby.nearby_backend.areaverification.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VerifyAreaRequest(
		@Schema(example = "10.8700") @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
		@Schema(example = "106.8031") @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
		@Schema(description = "Horizontal accuracy reported by the OS, in meters; over 100 is rejected",
				example = "12.5") @NotNull @PositiveOrZero Double accuracyMeters,
		@Schema(description = "When the GPS fix was taken; older than 120 s is rejected",
				example = "2026-11-22T02:00:00Z") @NotNull Instant fixTime,
		@Schema(description = "The OS mock-location flag; true is rejected") @NotNull Boolean mockLocation,
		@Valid @Nullable DeviceSignalsPayload deviceSignals,
		@Schema(example = "1.0.0") @Size(max = 20) @Nullable String appVersion,
		@Schema(description = "The user's name for the area; \"Khu vực {slot}\" when absent", example = "Nhà") @Size(
				max = 30) @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Nullable String label,
		@Schema(description = "Replace this area of the user instead of adding one (UC-AR-03)") @Nullable UUID replaceAreaId)
		implements
			GpsFixFields {
}
