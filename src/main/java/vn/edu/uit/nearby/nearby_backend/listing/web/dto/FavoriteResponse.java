package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.FavoriteService.FavoriteCard;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;

@Schema(description = "A saved listing on A15 (FR-LS-12)")
public record FavoriteResponse(UUID listingId, String title, long priceVnd, boolean free, ListingStatus status,
		@Schema(description = "false: grey it out as no longer available") boolean available,
		@Nullable String coverThumbnailUrl, Instant savedAt) {

	public static FavoriteResponse from(FavoriteCard card) {
		return new FavoriteResponse(card.listingId(), card.title(), card.priceVnd(), card.free(), card.status(),
				card.available(), card.coverThumbnailUrl(), card.savedAt());
	}

}
