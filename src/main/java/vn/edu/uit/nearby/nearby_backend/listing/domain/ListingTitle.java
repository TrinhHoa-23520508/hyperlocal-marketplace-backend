package vn.edu.uit.nearby.nearby_backend.listing.domain;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Listing title, 5-100 characters after cleaning (FR-LS-01; {@code listings.title}).
 */
public record ListingTitle(String value) {

	public static final int MIN_LENGTH = 5;

	public static final int MAX_LENGTH = 100;

	public ListingTitle(@Nullable String value) {
		String cleaned = ListingText.singleLine(value);
		int length = ListingText.lengthOf(cleaned);
		if (length < MIN_LENGTH || length > MAX_LENGTH) {
			throw InvalidRequestException.invalidField("title",
					"The title must be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters.");
		}
		this.value = cleaned;
	}

}
