package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "One image to upload (FR-LS-02, A13)")
public record UploadUrlRequest(@Schema(example = "image/jpeg",
		description = "image/jpeg, image/png or image/webp; convert HEIC to JPEG first") @NotBlank String contentType,
		@Schema(example = "2048000", description = "At most 5 242 880 bytes") @NotNull @Positive Long sizeBytes) {

}
