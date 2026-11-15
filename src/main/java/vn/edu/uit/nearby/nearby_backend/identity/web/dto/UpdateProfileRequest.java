package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.edu.uit.nearby.nearby_backend.identity.application.profile.UpdateProfileCommand;

/**
 * @param homeArea omitted or null clears the default area
 */
public record UpdateProfileRequest(
		@Schema(description = "2-30 characters, trimmed; changeable once every 30 days",
				example = "An Nguyen") @NotBlank @Size(min = 2, max = 30) String displayName,
		@Schema(description = "Default area, rounded to 3 decimals; null clears it",
				nullable = true) @Valid @Nullable HomeAreaPayload homeArea) {

	public UpdateProfileCommand toCommand() {
		return new UpdateProfileCommand(displayName, homeArea == null ? null : homeArea.toGeoPoint());
	}

}
