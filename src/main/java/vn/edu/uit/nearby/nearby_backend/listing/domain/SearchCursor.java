package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Position after the last listing of a feed or search page, so pages neither repeat nor
 * skip listings inserted meanwhile (FR-DC-01). Rows are ordered by {@code rank} (distance
 * or relevance score, none for NEWEST), then {@code feed_at} descending, then {@code id}.
 *
 * @param rankedAt the time freshness was measured at on the first page; later pages reuse
 * it so relevance scores do not drift between pages
 */
public record SearchCursor(SortOrder sort, Instant rankedAt, double rank, Instant feedAt, UUID id) {

	private static final String VERSION = "v1";

	private static final String SEPARATOR = "|";

	private static final int PART_COUNT = 6;

	public static SearchCursor newest(Instant feedAt, UUID id) {
		return new SearchCursor(SortOrder.NEWEST, feedAt, 0, feedAt, id);
	}

	public String encode() {
		String raw = String.join(SEPARATOR, VERSION, sort.name(), String.valueOf(rankedAt.toEpochMilli()),
				Double.toString(rank), String.valueOf(feedAt.toEpochMilli()), id.toString());
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @return empty for the first page
	 * @throws InvalidRequestException {@code INVALID_CURSOR} for a token that cannot be
	 * decoded or was made for another sort order
	 */
	public static Optional<SearchCursor> decode(@Nullable String token, SortOrder expectedSort) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		try {
			String[] parts = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8)
				.split(Pattern.quote(SEPARATOR));
			if (parts.length != PART_COUNT || !VERSION.equals(parts[0])
					|| SortOrder.valueOf(parts[1]) != expectedSort) {
				throw invalidCursor();
			}
			return Optional.of(new SearchCursor(expectedSort, Instant.ofEpochMilli(Long.parseLong(parts[2])),
					Double.parseDouble(parts[3]), Instant.ofEpochMilli(Long.parseLong(parts[4])),
					UUID.fromString(parts[5])));
		}
		catch (IllegalArgumentException ex) {
			throw invalidCursor();
		}
	}

	private static InvalidRequestException invalidCursor() {
		return new InvalidRequestException(ErrorCodes.INVALID_CURSOR,
				"The pagination cursor is invalid; reload from the first page.");
	}

}
