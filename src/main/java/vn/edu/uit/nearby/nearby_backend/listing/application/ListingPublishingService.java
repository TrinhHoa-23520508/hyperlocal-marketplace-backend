package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaLocality;
import vn.edu.uit.nearby.nearby_backend.areaverification.ListingPinRule;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.CreateListing;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingCommands.EditListing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ImageOrder;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing.ListingChanges;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing.NewListing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingContent;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingImage;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingLocation;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Price;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SellerReputation;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.CategoryRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

/**
 * UC-LS-01 (post a listing, with the gate and quota shown first) and UC-LS-02 (edit).
 */
@Service
public class ListingPublishingService {

	private final ListingRepository listings;

	private final ListingImageRepository images;

	private final CategoryRepository categories;

	private final TradingGate tradingGate;

	private final ListingPinRule pinRule;

	private final AreaLocality areaLocality;

	private final PostingQuotaCheck postingQuota;

	private final ListingParameters parameters;

	private final ListingEvents events;

	private final OwnerListingViews views;

	private final Clock clock;

	ListingPublishingService(ListingRepository listings, ListingImageRepository images, CategoryRepository categories,
			TradingGate tradingGate, ListingPinRule pinRule, AreaLocality areaLocality, PostingQuotaCheck postingQuota,
			ListingParameters parameters, ListingEvents events, OwnerListingViews views, Clock clock) {
		this.listings = listings;
		this.images = images;
		this.categories = categories;
		this.tradingGate = tradingGate;
		this.pinRule = pinRule;
		this.areaLocality = areaLocality;
		this.postingQuota = postingQuota;
		this.parameters = parameters;
		this.events = events;
		this.views = views;
		this.clock = clock;
	}

	/**
	 * UC-LS-01 step 1: the gate status and the remaining quota, shown before A12.
	 */
	@Transactional(readOnly = true)
	public PostingEligibility postingEligibility(UUID sellerId) {
		return new PostingEligibility(tradingGate.evaluate(sellerId),
				postingQuota.allowanceFor(sellerId, clock.instant()));
	}

	/**
	 * UC-LS-01 steps 6-9, in the SRS order: trading gate and pin, quota, fields and
	 * images, then an ACTIVE listing with its blurred location and
	 * {@code ListingPublished}.
	 */
	@Transactional
	public OwnerListingView create(CreateListing command) {
		UUID sellerId = command.sellerId();
		ListingForm form = command.form();
		Instant now = clock.instant();
		tradingGate.requireOpen(sellerId);
		pinRule.requirePinInsideArea(sellerId, command.areaId(), form.pin());
		listings.lockPostingOf(sellerId);
		postingQuota.requireCanPost(sellerId, now);
		ListingContent content = form.content();
		categories.getExisting(form.categoryId()).requireSelectable();
		ImageOrder order = ImageOrder.of(form.imageIds(), parameters.maxImages());
		Listing published = Listing.publish(new NewListing(sellerId, command.areaId(), form.categoryId(), content,
				locationOf(form), areaLocality.localityNameOf(command.areaId()).orElse(null)), now);
		currentReputationOf(sellerId).ifPresent(published::copySellerReputation);
		Listing listing = listings.save(published);
		attachImages(listing, order);
		events.published(listing);
		return views.of(listing);
	}

	/**
	 * UC-LS-02: everything but the area. A moved pin must stay inside the listing's area;
	 * a new category must be active; {@code ListingPriceDropped} once per drop.
	 */
	@Transactional
	public OwnerListingView edit(EditListing command) {
		Listing listing = listings.getOwned(command.listingId(), command.sellerId());
		listing.requireVersion(command.version());
		ListingForm form = command.form();
		ListingContent content = form.content();
		if (form.categoryId() != listing.getCategoryId()) {
			categories.getExisting(form.categoryId()).requireSelectable();
		}
		if (!form.pin().equals(listing.pin())) {
			pinRule.requirePinInsideArea(listing.getSellerId(), listing.getAreaId(), form.pin());
		}
		ImageOrder order = ImageOrder.of(form.imageIds(), parameters.maxImages());
		Price priceBefore = listing.askingPrice();
		Instant now = clock.instant();
		listing.edit(new ListingChanges(form.categoryId(), content, locationOf(form)), now);
		attachImages(listing, order);
		events.priceChanged(listing, priceBefore, now);
		return views.of(listing);
	}

	/**
	 * The latest value the reputation module pushed for the seller, kept on their other
	 * listings; empty until it pushes one (register LS-W7).
	 */
	private Optional<SellerReputation> currentReputationOf(UUID sellerId) {
		return listings.findFirstBySellerIdAndSellerScoreIsNotNull(sellerId)
			.filter(listing -> listing.getSellerTier() != null)
			.map(listing -> new SellerReputation(listing.getSellerScore(), listing.getSellerTier()));
	}

	private ListingLocation locationOf(ListingForm form) {
		return ListingLocation.pinnedAt(form.pin(), parameters.blurGrid());
	}

	/**
	 * Puts exactly the chosen images on the listing, in order. Images dropped by an edit
	 * become orphans for UC-SY-15. Current images are detached and flushed first so the
	 * unique (listing, position) never sees two images in one position.
	 */
	private void attachImages(Listing listing, ImageOrder order) {
		images.findByListingIdOrderByPosition(listing.getId()).forEach(ListingImage::detach);
		images.flush();
		Map<UUID, ListingImage> chosen = images.findAllById(order.imageIds())
			.stream()
			.collect(Collectors.toMap(ListingImage::getId, Function.identity()));
		for (UUID imageId : order.imageIds()) {
			ListingImage image = chosen.get(imageId);
			if (image == null) {
				throw new UnprocessableRequestException(ErrorCodes.IMAGE_INVALID,
						"Image " + imageId + " does not exist.");
			}
			image.attachTo(listing.getSellerId(), listing.getId(), order.positionOf(imageId));
		}
		images.flush();
	}

}
