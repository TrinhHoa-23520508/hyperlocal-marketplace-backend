package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.OwnerListingView;
import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;

@Schema(description = "The caller's own listing after a change (A12, A14), with its exact pin")
public record OwnerListingResponse(UUID id, ListingStatus status,
		@Schema(description = "OWNER, AREA_REMOVED or SANCTION while HIDDEN") @Nullable HiddenReason hiddenReason,
		short categoryId, UUID areaId, String title, @Nullable String description, ItemCondition condition,
		long priceVnd, boolean free, boolean acceptOffers, @Nullable String meetingSpot,
		@Schema(description = "Exact pin; only ever returned to the owner") PointResponse pin,
		@Schema(description = "Blurred point everyone else sees") PointResponse publicPoint,
		@Nullable String localityName, List<ListingImageResponse> images, int views, int favorites, int chats,
		int bumpCount, @Nullable Instant bumpedAt,
		@Schema(description = "When the next bump is allowed; null before the first bump") @Nullable Instant nextBumpAllowedAt,
		@Nullable Instant publishedAt, @Nullable Instant soldAt, Instant createdAt, Instant updatedAt,
		@Schema(description = "Send it back with the next change") long version) {

	public static OwnerListingResponse from(OwnerListingView view) {
		return new OwnerListingResponse(view.id(), view.status(), view.hiddenReason(), view.categoryId(), view.areaId(),
				view.title(), view.description(), view.condition(), view.priceVnd(), view.free(), view.acceptOffers(),
				view.meetingSpot(), PointResponse.from(view.pin()), PointResponse.from(view.publicPoint()),
				view.localityName(), view.images().stream().map(ListingImageResponse::from).toList(),
				view.counters().views(), view.counters().favorites(), view.counters().chats(), view.bumpCount(),
				view.bumpedAt(), view.nextBumpAllowedAt(), view.publishedAt(), view.soldAt(), view.createdAt(),
				view.updatedAt(), view.version());
	}

}
