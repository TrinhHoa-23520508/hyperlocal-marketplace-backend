package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Optional preferred meeting spot as free text, at most 100 characters (FR-LS-01;
 * {@code listings.meeting_spot_text}).
 */
public record MeetingSpot(@Nullable String value) {

	public static final int MAX_LENGTH = 100;

	public MeetingSpot(@Nullable String value) {
		String cleaned = ListingText.singleLine(value);
		if (ListingText.lengthOf(cleaned) > MAX_LENGTH) {
			throw InvalidRequestException.invalidField("meetingSpot",
					"The meeting spot must be at most " + MAX_LENGTH + " characters.");
		}
		this.value = cleaned.isEmpty() ? null : cleaned;
	}

	public Optional<String> text() {
		return Optional.ofNullable(value);
	}

}
