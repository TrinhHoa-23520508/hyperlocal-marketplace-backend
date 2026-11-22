package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.edu.uit.nearby.nearby_backend.identity.application.profile.UpdateProfileCommand;

public record UpdateProfileRequest(@Schema(description = "2-30 characters, trimmed; changeable once every 30 days",
		example = "An Nguyen") @NotBlank @Size(min = 2, max = 30) String displayName) {

	public UpdateProfileCommand toCommand() {
		return new UpdateProfileCommand(displayName);
	}

}
