package vn.edu.uit.nearby.nearby_backend.chat.domain;

import org.jspecify.annotations.Nullable;

/**
 * Text cleaning for chat fields: UTF-8 with control characters removed (SRS
 * {@code tab:validation}), line breaks kept in messages. Lengths count Unicode code
 * points, as PostgreSQL {@code char_length} does.
 */
final class ChatText {

	private static final char LINE_FEED = '\n';

	private ChatText() {
	}

	static String multiLine(@Nullable String text) {
		return clean(text, true);
	}

	static String singleLine(@Nullable String text) {
		return clean(text, false);
	}

	static int lengthOf(String text) {
		return text.codePointCount(0, text.length());
	}

	/**
	 * @return at most {@code maxChars} code points, never a split surrogate pair
	 */
	static String cut(String text, int maxChars) {
		if (lengthOf(text) <= maxChars) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, maxChars));
	}

	private static String clean(@Nullable String text, boolean keepLineBreaks) {
		if (text == null) {
			return "";
		}
		StringBuilder cleaned = new StringBuilder(text.length());
		text.replace("\r\n", "\n")
			.codePoints()
			.filter(codePoint -> !Character.isISOControl(codePoint) || (keepLineBreaks && codePoint == LINE_FEED))
			.forEach(cleaned::appendCodePoint);
		return cleaned.toString().strip();
	}

}
