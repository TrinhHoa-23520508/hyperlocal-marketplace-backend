package vn.edu.uit.nearby.nearby_backend.listing.domain;

import org.jspecify.annotations.Nullable;

/**
 * Text cleaning for listing fields (SRS table {@code tab:validation}, "Văn bản": UTF-8,
 * control characters removed). Lengths count Unicode code points, as PostgreSQL
 * {@code char_length} does.
 */
final class ListingText {

	private static final char LINE_FEED = '\n';

	private ListingText() {
	}

	static String singleLine(@Nullable String text) {
		return withoutControlCharacters(text, false);
	}

	static String multiLine(@Nullable String text) {
		return withoutControlCharacters(text, true);
	}

	static int lengthOf(String text) {
		return text.codePointCount(0, text.length());
	}

	private static String withoutControlCharacters(@Nullable String text, boolean keepLineBreaks) {
		if (text == null) {
			return "";
		}
		StringBuilder cleaned = new StringBuilder(text.length());
		text.replace("\r\n", "\n")
			.codePoints()
			.filter(codePoint -> isKept(codePoint, keepLineBreaks))
			.forEach(cleaned::appendCodePoint);
		return cleaned.toString().strip();
	}

	private static boolean isKept(int codePoint, boolean keepLineBreaks) {
		return !Character.isISOControl(codePoint) || (keepLineBreaks && codePoint == LINE_FEED);
	}

}
