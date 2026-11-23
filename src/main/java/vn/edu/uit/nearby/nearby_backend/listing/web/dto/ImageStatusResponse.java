package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingImageService.ImageStatusView;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ImageProcessingStatus;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageRejectReason;

@Schema(description = "Poll until READY (use the image) or REJECTED (upload again) (UC-SY-10)")
public record ImageStatusResponse(UUID imageId, ImageProcessingStatus status,
		@Schema(description = "Only when REJECTED") @Nullable ImageRejectReason rejectReason,
		@Schema(description = "320 px preview, only when READY") @Nullable String thumbnailUrl) {

	public static ImageStatusResponse from(ImageStatusView view) {
		return new ImageStatusResponse(view.imageId(), view.status(), view.rejectReason(), view.thumbnailUrl());
	}

}
