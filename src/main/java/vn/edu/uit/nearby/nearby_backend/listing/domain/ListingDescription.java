package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Optional free-text description, at most 2 000 characters; line breaks are kept
 * (FR-LS-01; {@code listings.description}).
 */
public record ListingDescription(@Nullable String value) {

	public static final int MAX_LENGTH = 2000;

	public ListingDescription(@Nullable String value) {
		String cleaned = ListingText.multiLine(value);
		if (ListingText.lengthOf(cleaned) > MAX_LENGTH) {
			throw InvalidRequestException.invalidField("description",
					"The description must be at most " + MAX_LENGTH + " characters.");
		}
		this.value = cleaned.isEmpty() ? null : cleaned;
	}

	public Optional<String> text() {
		return Optional.ofNullable(value);
	}

}
