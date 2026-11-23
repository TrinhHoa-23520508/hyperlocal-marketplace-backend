package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingImageView;

@Schema(description = "Short-lived URLs of the WebP derivatives; the original is never served (FR-LS-02)")
public record ListingImageResponse(UUID id, @Schema(description = "1 = cover") int position,
		@Schema(description = "320 px") String thumbnailUrl, @Schema(description = "1080 px") String largeUrl) {

	public static ListingImageResponse from(ListingImageView image) {
		return new ListingImageResponse(image.id(), image.position(), image.thumbnailUrl(), image.largeUrl());
	}

}
