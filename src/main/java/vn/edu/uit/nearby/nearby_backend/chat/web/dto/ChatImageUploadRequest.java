package vn.edu.uit.nearby.nearby_backend.chat.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "One image to send in a conversation (FR-CH-04, A18)")
public record ChatImageUploadRequest(@NotNull UUID conversationId, @Schema(example = "image/jpeg",
		description = "image/jpeg, image/png or image/webp; convert HEIC to JPEG first") @NotBlank String contentType,
		@Schema(example = "2048000", description = "At most 5 242 880 bytes") @NotNull @Positive Long sizeBytes) {

}
