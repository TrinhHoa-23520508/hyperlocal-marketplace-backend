package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import vn.edu.uit.nearby.nearby_backend.identity.application.profile.AvatarUploadCommand;

public record AvatarUploadUrlRequest(
		@Schema(description = "image/jpeg (also image/jpg), image/png or image/webp",
				example = "image/jpeg") @NotBlank @Size(max = 50) String contentType,
		@Schema(description = "File size in bytes, at most image.max_bytes (5 MB)",
				example = "204800") @Positive long sizeBytes) {

	public AvatarUploadCommand toCommand() {
		return new AvatarUploadCommand(contentType, sizeBytes);
	}

}
