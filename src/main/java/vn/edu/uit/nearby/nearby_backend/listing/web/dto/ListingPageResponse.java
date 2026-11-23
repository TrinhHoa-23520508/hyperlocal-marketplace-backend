package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.ResultPage;

@Schema(description = "One page of the feed (FR-DC-01) or of a search (FR-DC-02)")
public record ListingPageResponse(List<ListingCardResponse> items, @Schema(
		description = "Pass back as cursor for the next page; null on the last page") @Nullable String nextCursor,
		boolean hasMore, @Schema(description = "Radius actually searched") int radiusMeters,
		@Schema(description = "The requested radius was outside 1-20 km and was clamped (FR-DC-06)") boolean radiusAdjusted,
		@Schema(description = "Nothing found: offer to widen the radius (FR-DC-06)") boolean suggestWiderRadius) {

	public static ListingPageResponse from(ResultPage page) {
		return new ListingPageResponse(page.items().stream().map(ListingCardResponse::from).toList(), page.nextCursor(),
				page.hasMore(), page.radiusMeters(), page.radiusAdjusted(), page.suggestWiderRadius());
	}

}
