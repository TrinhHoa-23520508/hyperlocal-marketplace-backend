package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.listing.domain.Favorite;
import vn.edu.uit.nearby.nearby_backend.listing.domain.Listing;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.FavoriteRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;

/**
 * FR-LS-12: save and unsave a listing (UC-LS-08, idempotent, the counter moves by one per
 * distinct user) and the saved list (UC-LS-09, A15).
 */
@Service
@Transactional
public class FavoriteService {

	private final FavoriteRepository favorites;

	private final ListingRepository listings;

	private final ListingImageUrls imageUrls;

	private final Clock clock;

	FavoriteService(FavoriteRepository favorites, ListingRepository listings, ListingImageUrls imageUrls, Clock clock) {
		this.favorites = favorites;
		this.listings = listings;
		this.imageUrls = imageUrls;
		this.clock = clock;
	}

	/**
	 * The save time is kept in milliseconds, the precision of the page cursor.
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException
	 * {@code LISTING_NOT_FOUND} for a listing the user cannot see
	 */
	public void save(UUID userId, UUID listingId) {
		listings.getVisible(listingId, userId);
		if (favorites.insertIfAbsent(userId, listingId, clock.instant().truncatedTo(ChronoUnit.MILLIS)) == 1) {
			listings.addToFavoriteCount(listingId, 1);
		}
	}

	/**
	 * Works on any listing the user saved, including one that is no longer available.
	 */
	public void unsave(UUID userId, UUID listingId) {
		if (favorites.deleteIfPresent(userId, listingId) == 1) {
			listings.addToFavoriteCount(listingId, -1);
		}
	}

	@Transactional(readOnly = true)
	public CursorPage<FavoriteCard> savedListings(UUID userId, @Nullable String cursorToken,
			@Nullable Integer requestedLimit) {
		int limit = CursorPage.clampLimit(requestedLimit);
		Limit fetchLimit = Limit.of(limit + 1);
		List<Favorite> fetched = Cursor.decode(cursorToken)
			.map(cursor -> favorites.findPageAfter(userId, cursor.createdAt(), cursor.id(), fetchLimit))
			.orElseGet(() -> favorites.findFirstPage(userId, fetchLimit));
		CursorPage<Favorite> page = CursorPage.of(fetched, limit,
				favorite -> new Cursor(favorite.getCreatedAt(), favorite.getId().listingId()));
		return toCards(page);
	}

	private CursorPage<FavoriteCard> toCards(CursorPage<Favorite> page) {
		List<UUID> ids = page.items().stream().map(favorite -> favorite.getId().listingId()).toList();
		Map<UUID, Listing> saved = listings.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(Listing::getId, Function.identity()));
		Map<UUID, String> covers = imageUrls.coverThumbnails(ids);
		return page.map(favorite -> {
			Listing listing = saved.get(favorite.getId().listingId());
			return new FavoriteCard(listing.getId(), listing.getTitle(), listing.getPrice(),
					listing.askingPrice().isFree(), listing.getStatus(), listing.getStatus().isPubliclyVisible(),
					covers.get(listing.getId()), favorite.getCreatedAt());
		});
	}

	/**
	 * One row of A15.
	 *
	 * @param available false for a listing that was removed, deleted or hidden: the app
	 * greys it out as "Không còn khả dụng" (FR-LS-12)
	 */
	public record FavoriteCard(UUID listingId, String title, long priceVnd, boolean free, ListingStatus status,
			boolean available, @Nullable String coverThumbnailUrl, Instant savedAt) {
	}

}
