package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@Schema(example = "Secret123") @NotBlank @Size(max = 200) String currentPassword,
		@Schema(description = "At least 8 characters, at most 72 UTF-8 bytes, letters and digits",
				example = "NewSecret456") @NotBlank @Size(max = 200) String newPassword) {

}
