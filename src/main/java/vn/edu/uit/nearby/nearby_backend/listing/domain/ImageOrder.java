package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * The images of a listing in display order: 1 to {@code listing.max_images}, no
 * duplicates, the first one is the cover (FR-LS-01).
 */
public record ImageOrder(List<UUID> imageIds) {

	public ImageOrder {
		imageIds = List.copyOf(imageIds);
		if (imageIds.isEmpty()) {
			throw InvalidRequestException.invalidField("imageIds", "A listing needs at least one image.");
		}
		if (new HashSet<>(imageIds).size() != imageIds.size()) {
			throw InvalidRequestException.invalidField("imageIds", "An image can appear only once.");
		}
	}

	public static ImageOrder of(List<UUID> imageIds, int maxImages) {
		if (imageIds.size() > maxImages) {
			throw InvalidRequestException.invalidField("imageIds",
					"A listing can have at most " + maxImages + " images.");
		}
		return new ImageOrder(imageIds);
	}

	public UUID cover() {
		return imageIds.getFirst();
	}

	/**
	 * @return 1 for the cover, then 2, 3 ... ({@code listing_images.position})
	 */
	public int positionOf(UUID imageId) {
		int index = imageIds.indexOf(imageId);
		if (index < 0) {
			throw new IllegalArgumentException("Image " + imageId + " is not part of this listing.");
		}
		return index + 1;
	}

	public boolean contains(UUID imageId) {
		return imageIds.contains(imageId);
	}

}
