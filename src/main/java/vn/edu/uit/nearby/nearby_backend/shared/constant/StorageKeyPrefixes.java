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

	private StorageKeyPrefixes() {
	}

}
