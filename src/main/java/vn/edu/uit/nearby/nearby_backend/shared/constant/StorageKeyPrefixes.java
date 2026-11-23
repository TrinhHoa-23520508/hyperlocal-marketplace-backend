package vn.edu.uit.nearby.nearby_backend.shared.constant;

public final class StorageKeyPrefixes {

	public static final String AVATARS = "avatars/";

	/**
	 * Private originals of listing images, never served (FR-LS-02).
	 */
	public static final String LISTING_INCOMING = "incoming/";

	/**
	 * Public 320 px and 1080 px WebP derivatives without metadata (UC-SY-10).
	 */
	public static final String LISTING_IMAGES = "listing-images/";

	/**
	 * Private originals of chat images, never served (FR-CH-04).
	 */
	public static final String CHAT_INCOMING = "chat-incoming/";

	/**
	 * Metadata-free WebP derivatives of chat images, served only through short-lived
	 * signed URLs to the two members (FR-CH-04, NFR-31).
	 */
	public static final String CHAT_IMAGES = "chat-images/";

	private StorageKeyPrefixes() {
	}

}
