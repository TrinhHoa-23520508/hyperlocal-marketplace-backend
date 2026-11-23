package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingDetailService.ListingDetail;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions.ActionState;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions.DisabledReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SellerTier;

@Schema(description = "A10 listing detail (FR-DC-08)")
public record ListingDetailResponse(UUID id, ListingStatus status,
		@Schema(description = "Only for the owner") @Nullable HiddenReason hiddenReason, short categoryId, String title,
		@Nullable String description, ItemCondition condition, long priceVnd, boolean free, boolean acceptOffers,
		@Nullable String meetingSpot,
		@Schema(description = "1080 px images in order") List<ListingImageResponse> images,
		@Schema(description = "Blurred point for the mini map") PointResponse publicPoint,
		@Schema(description = "Exact pin, only for the owner (FR-LS-03)") @Nullable PointResponse pin,
		@Schema(description = "Rounded to 100 m; null without a center") @Nullable Long distanceMeters,
		@Nullable String localityName, Seller seller, int views, int favorites,
		@Schema(description = "The caller saved it") boolean savedByViewer, Instant postedAt, @Nullable Instant soldAt,
		Actions actions, long version) {

	public static ListingDetailResponse from(ListingDetail detail) {
		return new ListingDetailResponse(detail.id(), detail.status(), detail.hiddenReason(), detail.categoryId(),
				detail.title(), detail.description(), detail.condition(), detail.priceVnd(), detail.free(),
				detail.acceptOffers(), detail.meetingSpot(),
				detail.images().stream().map(ListingImageResponse::from).toList(),
				PointResponse.from(detail.publicPoint()),
				detail.pin() == null ? null : PointResponse.from(detail.pin()), detail.distanceMeters(),
				detail.localityName(),
				new Seller(detail.seller().sellerId(), detail.seller().tier(), detail.seller().score()), detail.views(),
				detail.favorites(), detail.savedByViewer(), detail.postedAt(), detail.soldAt(),
				Actions.from(detail.actions()), detail.version());
	}

	@Schema(description = "Name and avatar come from GET /users/{sellerId}")
	public record Seller(UUID id, SellerTier tier, @Schema(
			description = "0-100; null until the seller has a score (baseline 50)") @Nullable BigDecimal score) {
	}

	@Schema(description = "Each button: enabled, or disabled with the reason to show and act on")
	public record Actions(@Schema(description = "Show edit actions instead of Chat and Offer") boolean ownListing,
			Action chat, Action offer, Action save, Action report) {

		static Actions from(DetailActions actions) {
			return new Actions(actions.ownListing(), Action.from(actions.chat()), Action.from(actions.offer()),
					Action.from(actions.save()), Action.from(actions.report()));
		}

	}

	public record Action(boolean enabled, @Nullable DisabledReason reason, @Schema(
			description = "Trading gate conditions to fix, only for TRADING_GATE_FAILED") List<String> gateFailures) {

		static Action from(ActionState state) {
			return new Action(state.enabled(), state.reason(), state.gateFailures());
		}

	}

}
