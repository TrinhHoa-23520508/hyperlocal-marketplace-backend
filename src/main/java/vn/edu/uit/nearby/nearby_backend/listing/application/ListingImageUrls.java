package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingImage;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;

/**
 * Short-lived download URLs of listing image derivatives (FR-LS-02: originals are never
 * served).
 */
@Component
class ListingImageUrls {

	private final ImageStorage storage;

	private final ListingImageRepository images;

	ListingImageUrls(ImageStorage storage, ListingImageRepository images) {
		this.storage = storage;
		this.images = images;
	}

	List<ListingImageView> imagesOf(UUID listingId) {
		return images.findByListingIdOrderByPosition(listingId).stream().map(this::toView).toList();
	}

	/**
	 * @return the 320 px thumbnail URL of each listing's cover; listings without one are
	 * absent (the app shows a placeholder, FR-DC-05)
	 */
	Map<UUID, String> coverThumbnails(Collection<UUID> listingIds) {
		if (listingIds.isEmpty()) {
			return Map.of();
		}
		return images.findCovers(listingIds)
			.stream()
			.collect(Collectors.toMap(ListingImage::getListingId, this::thumbnailUrl, (first, second) -> first));
	}

	String thumbnailUrl(ListingImage image) {
		return urlOf(Objects.requireNonNull(image.getThumbKey()));
	}

	private ListingImageView toView(ListingImage image) {
		return new ListingImageView(image.getId(), Objects.requireNonNull(image.getPosition()), thumbnailUrl(image),
				urlOf(Objects.requireNonNull(image.getLargeKey())));
	}

	private String urlOf(String objectKey) {
		return storage.presignDownload(objectKey).url();
	}

}
