package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.MyListingsService.MyListingsPage;
import vn.edu.uit.nearby.nearby_backend.listing.application.MyListingsService.OwnerListingCard;
import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;

@Schema(description = "A14 Tin cua toi (FR-LS-11)")
public record MyListingsResponse(@Schema(
		description = "Listings per state for the tabs, zeros included; DELETED is never shown") Map<ListingStatus, Long> countsByStatus,
		List<Item> items, @Nullable String nextCursor, boolean hasMore) {

	public static MyListingsResponse from(MyListingsPage page) {
		return new MyListingsResponse(page.countsByStatus(), page.listings().items().stream().map(Item::from).toList(),
				page.listings().nextCursor(), page.listings().hasMore());
	}

	public record Item(UUID id, ListingStatus status, @Nullable HiddenReason hiddenReason, String title, long priceVnd,
			boolean free, @Schema(description = "null: show a placeholder") @Nullable String coverThumbnailUrl,
			int views, int chats, int favorites,
			@Schema(description = "OFFERED transactions; 0 until the transaction module exists") int openOffers,
			Instant createdAt, long version) {

		static Item from(OwnerListingCard card) {
			return new Item(card.id(), card.status(), card.hiddenReason(), card.title(), card.priceVnd(), card.free(),
					card.coverThumbnailUrl(), card.views(), card.chats(), card.favorites(), card.openOffers(),
					card.createdAt(), card.version());
		}

	}

}
