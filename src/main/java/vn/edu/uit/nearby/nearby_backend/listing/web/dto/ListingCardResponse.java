package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.ListingCard;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SellerTier;

@Schema(description = "Result card (FR-DC-05); never a location")
public record ListingCardResponse(UUID id, String title, long priceVnd,
		@Schema(description = "Show Mien phi when true") boolean free,
		@Schema(description = "RESERVED shows a label; SOLD only when includeSold") ListingStatus status,
		@Schema(description = "320 px; null: show a placeholder") @Nullable String coverThumbnailUrl,
		@Schema(description = "Rounded to 100 m; show m below 1 km, km with one decimal from 1 km") @Nullable Long distanceMeters,
		@Schema(description = "Neighborhood label; null until known") @Nullable String localityName,
		@Schema(description = "Posted or last bumped") Instant postedAt, SellerTier sellerTier) {

	public static ListingCardResponse from(ListingCard card) {
		return new ListingCardResponse(card.id(), card.title(), card.priceVnd(), card.free(), card.status(),
				card.coverThumbnailUrl(), card.distanceMeters(), card.localityName(), card.postedAt(),
				card.sellerTier());
	}

}
