package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.listing.OpenOfferCounts;
import vn.edu.uit.nearby.nearby_backend.listing.domain.HiddenReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository.StatusCount;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;

/**
 * UC-LS-07 / A14 "Tin của tôi" (FR-LS-11): the seller's listings grouped by state, with
 * the counters stored on each listing at the time of loading.
 */
@Service
@Transactional(readOnly = true)
public class MyListingsService {

	private static final Set<ListingStatus> SHOWN = EnumSet.complementOf(EnumSet.of(ListingStatus.DELETED));

	private final ListingRepository listings;

	private final ListingImageUrls imageUrls;

	private final List<OpenOfferCounts> openOfferCounts;

	MyListingsService(ListingRepository listings, ListingImageUrls imageUrls, List<OpenOfferCounts> openOfferCounts) {
		this.listings = listings;
		this.imageUrls = imageUrls;
		this.openOfferCounts = openOfferCounts;
	}

	/**
	 * @param statuses the group to list; empty means every state but DELETED
	 */
	public MyListingsPage myListings(UUID sellerId, Set<ListingStatus> statuses, @Nullable String cursorToken,
			@Nullable Integer requestedLimit) {
		Set<ListingStatus> shown = statuses.isEmpty() ? SHOWN : EnumSet.copyOf(statuses);
		shown.retainAll(SHOWN);
		int limit = CursorPage.clampLimit(requestedLimit);
		List<Listing> fetched = shown.isEmpty() ? List.of() : fetchPage(sellerId, shown, cursorToken, limit + 1);
		CursorPage<Listing> page = CursorPage.of(fetched, limit,
				listing -> new Cursor(listing.getCreatedAt(), listing.getId()));
		return new MyListingsPage(countsByStatus(sellerId), toCards(page));
	}

	private List<Listing> fetchPage(UUID sellerId, Set<ListingStatus> statuses, @Nullable String cursorToken,
			int fetchLimit) {
		return Cursor.decode(cursorToken)
			.map(cursor -> listings.findOwnPageAfter(sellerId, statuses, cursor.createdAt(), cursor.id(),
					Limit.of(fetchLimit)))
			.orElseGet(() -> listings.findOwnFirstPage(sellerId, statuses, Limit.of(fetchLimit)));
	}

	private Map<ListingStatus, Long> countsByStatus(UUID sellerId) {
		Map<ListingStatus, Long> counts = new EnumMap<>(ListingStatus.class);
		SHOWN.forEach(status -> counts.put(status, 0L));
		for (StatusCount count : listings.countBySellerGroupedByStatus(sellerId, SHOWN)) {
			counts.put(count.getStatus(), count.getCount());
		}
		return counts;
	}

	private CursorPage<OwnerListingCard> toCards(CursorPage<Listing> page) {
		List<UUID> ids = page.items().stream().map(Listing::getId).toList();
		Map<UUID, String> covers = imageUrls.coverThumbnails(ids);
		Map<UUID, Integer> openOffers = openOffersOf(ids);
		return page.map(listing -> new OwnerListingCard(listing.getId(), listing.getStatus(), listing.getHiddenReason(),
				listing.getTitle(), listing.getPrice(), listing.askingPrice().isFree(), covers.get(listing.getId()),
				listing.getViewCount(), listing.getChatCount(), listing.getFavoriteCount(),
				openOffers.getOrDefault(listing.getId(), 0), listing.getCreatedAt(), listing.getVersion()));
	}

	private Map<UUID, Integer> openOffersOf(Collection<UUID> listingIds) {
		Map<UUID, Integer> counts = new HashMap<>();
		openOfferCounts.forEach(source -> counts.putAll(source.countOpenOffers(listingIds)));
		return counts;
	}

	/**
	 * @param countsByStatus how many listings each tab of A14 holds, every state but
	 * DELETED, zeros included
	 */
	public record MyListingsPage(Map<ListingStatus, Long> countsByStatus, CursorPage<OwnerListingCard> listings) {
	}

	/**
	 * One row of A14 with the FR-LS-11 counters.
	 *
	 * @param coverThumbnailUrl null when the cover is missing (placeholder)
	 * @param openOffers OFFERED transactions; 0 until the transaction module exists
	 * (register LS-W6)
	 */
	public record OwnerListingCard(UUID id, ListingStatus status, @Nullable HiddenReason hiddenReason, String title,
			long priceVnd, boolean free, @Nullable String coverThumbnailUrl, int views, int chats, int favorites,
			int openOffers, Instant createdAt, long version) {
	}

}
