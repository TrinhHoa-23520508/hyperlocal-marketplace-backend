package vn.edu.uit.nearby.nearby_backend.chat.domain;

/**
 * One processed image of an IMAGE message, as stored in {@code messages.payload} (data
 * dictionary: {@code {key, thumbKey, w, h}}).
 *
 * @param key object key of the 1080 px derivative
 * @param thumbKey object key of the 320 px derivative
 */
public record ChatImage(String key, String thumbKey, int width, int height) {

	public ChatImage {
		if (key.isBlank() || thumbKey.isBlank() || width <= 0 || height <= 0) {
			throw new IllegalArgumentException("An image needs both keys and positive dimensions.");
		}
	}

}
