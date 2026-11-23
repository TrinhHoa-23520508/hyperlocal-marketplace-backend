package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import vn.edu.uit.nearby.nearby_backend.areaverification.FeedArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.FeedCenter;
import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions.BuyerContext;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DisplayDistance;
import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SellerTier;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Favorite.FavoriteId;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.FavoriteRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * UC-DC-04 / A10 (FR-DC-08): the listing with its 1080 px images, blurred mini map,
 * distance, seller card, counters and the four buttons with their reasons. Opening it
 * counts a view (FR-LS-14).
 */
@Slf4j
@Service
public class ListingDetailService {

	private final ListingRepository listings;

	private final FavoriteRepository favorites;

	private final ListingImageUrls imageUrls;

	private final UserBlocks userBlocks;

	private final TradingGate tradingGate;

	private final InteractionRange interactionRange;

	private final FeedArea feedArea;

	private final ListingViewCounter viewCounter;

	ListingDetailService(ListingRepository listings, FavoriteRepository favorites, ListingImageUrls imageUrls,
			UserBlocks userBlocks, TradingGate tradingGate, InteractionRange interactionRange, FeedArea feedArea,
			ListingViewCounter viewCounter) {
		this.listings = listings;
		this.favorites = favorites;
		this.imageUrls = imageUrls;
		this.userBlocks = userBlocks;
		this.tradingGate = tradingGate;
		this.interactionRange = interactionRange;
		this.feedArea = feedArea;
		this.viewCounter = viewCounter;
	}

	/**
	 * @param origin the point distances are measured from (GPS or picked); null uses the
	 * viewer's active area, and no distance is shown without either
	 * @throws ResourceNotFoundException {@code LISTING_NOT_FOUND} for a DELETED, REMOVED
	 * or hidden listing of someone else, or between users who blocked each other
	 */
	@Transactional(readOnly = true)
	public ListingDetail detail(UUID listingId, Viewer viewer, @Nullable GeoPoint origin) {
		UUID viewerId = viewer.userId();
		Listing listing = listings.getVisible(listingId, viewerId);
		boolean owner = viewerId != null && listing.isOwnedBy(viewerId);
		if (!owner && viewerId != null && userBlocks.isBlockedEitherWay(viewerId, listing.getSellerId())) {
			throw new ResourceNotFoundException(ErrorCodes.LISTING_NOT_FOUND, "Listing not found.");
		}
		ListingDetail detail = toDetail(listing, viewer, owner, origin);
		if (!owner) {
			countViewQuietly(listing.getId(), viewer.key());
		}
		return detail;
	}

	private ListingDetail toDetail(Listing listing, Viewer viewer, boolean owner, @Nullable GeoPoint origin) {
		UUID viewerId = viewer.userId();
		GeoPoint publicPoint = listing.publicPoint();
		GeoPoint from = origin != null ? origin : activeAreaCenterOf(viewerId);
		Instant postedAt = listing.getBumpedAt() == null ? listing.getCreatedAt() : listing.getBumpedAt();
		return new ListingDetail(listing.getId(), listing.getStatus(), owner ? listing.getHiddenReason() : null,
				listing.getCategoryId(), listing.getTitle(), listing.getDescription(), listing.getCondition(),
				listing.getPrice(), listing.askingPrice().isFree(), listing.isAcceptOffers(),
				listing.getMeetingSpotText(), imageUrls.imagesOf(listing.getId()), publicPoint,
				owner ? listing.pin() : null,
				from == null ? null : DisplayDistance.of(from.distanceMetersTo(publicPoint)).meters(),
				listing.getLocalityName(),
				new SellerCard(listing.getSellerId(),
						listing.getSellerTier() == null ? SellerTier.NEW : listing.getSellerTier(),
						listing.getSellerScore()),
				listing.getViewCount(), listing.getFavoriteCount(),
				viewerId != null && favorites.existsById(new FavoriteId(viewerId, listing.getId())), postedAt,
				listing.getSoldAt(), actionsFor(listing, viewerId, owner), listing.getVersion());
	}

	private DetailActions actionsFor(Listing listing, @Nullable UUID viewerId, boolean owner) {
		if (viewerId == null) {
			return DetailActions.forGuest();
		}
		if (owner) {
			return DetailActions.forOwner();
		}
		List<String> gateFailures = tradingGate.evaluate(viewerId).failures().stream().map(Enum::name).toList();
		return DetailActions.forBuyer(new BuyerContext(listing.getStatus(), gateFailures,
				interactionRange.allowsNewContacts(listing.getAreaId()),
				interactionRange.isWithinRange(viewerId, listing.publicPoint())));
	}

	private @Nullable GeoPoint activeAreaCenterOf(@Nullable UUID viewerId) {
		return viewerId == null ? null : feedArea.activeFeedArea(viewerId).map(FeedCenter::center).orElse(null);
	}

	private void countViewQuietly(UUID listingId, String viewerKey) {
		try {
			viewCounter.countView(listingId, viewerKey);
		}
		catch (RuntimeException ex) {
			log.warn("View of listing {} not counted: {}", listingId, ex.getMessage());
		}
	}

	/**
	 * Who opens A10.
	 *
	 * @param userId null for a guest
	 * @param guestFingerprint identifies a guest for the daily view count; ignored for a
	 * signed-in user
	 */
	public record Viewer(@Nullable UUID userId, String guestFingerprint) {

		String key() {
			return userId == null ? "guest:" + guestFingerprint : userId.toString();
		}

	}

	/**
	 * The seller as listing knows them: the tier and score copied by the reputation
	 * module (register LS-W7). Name and avatar come from the public profile (FR-ID-13).
	 *
	 * @param score null until the seller has a score (FR-RP-01 baseline 50)
	 */
	public record SellerCard(UUID sellerId, SellerTier tier, @Nullable BigDecimal score) {
	}

	/**
	 * A10.
	 *
	 * @param hiddenReason only for the owner
	 * @param pin the exact pin, only for the owner (FR-LS-03)
	 * @param distanceMeters rounded to 100 m from the origin to the public point
	 * @param savedByViewer whether the viewer saved it (FR-LS-12)
	 */
	public record ListingDetail(UUID id, ListingStatus status, @Nullable HiddenReason hiddenReason, short categoryId,
			String title, @Nullable String description, ItemCondition condition, long priceVnd, boolean free,
			boolean acceptOffers, @Nullable String meetingSpot, List<ListingImageView> images, GeoPoint publicPoint,
			@Nullable GeoPoint pin, @Nullable Long distanceMeters, @Nullable String localityName, SellerCard seller,
			int views, int favorites, boolean savedByViewer, Instant postedAt, @Nullable Instant soldAt,
			DetailActions actions, long version) {
	}

}
