package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.util.UUID;

/**
 * One processed image of a listing: short-lived URLs of the two metadata-free WebP
 * derivatives; the original is never served (FR-LS-02).
 *
 * @param position 1 for the cover
 */
public record ListingImageView(UUID id, int position, String thumbnailUrl, String largeUrl) {

}
