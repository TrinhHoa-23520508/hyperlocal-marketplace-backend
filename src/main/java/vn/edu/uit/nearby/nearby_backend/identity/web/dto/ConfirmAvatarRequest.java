package vn.edu.uit.nearby.nearby_backend.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmAvatarRequest(@Schema(description = "objectKey returned by the upload-url step",
		example = "avatars/1b0c6f1e-0000-4000-8000-000000000000/7f3e6a2b-0000-4000-8000-000000000000.jpg") @NotBlank @Size(
				max = 200) String objectKey) {

}
