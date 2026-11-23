package vn.edu.uit.nearby.nearby_backend.shared.image;

/**
 * Why the pipeline refused an upload ({@code listing_images.reject_reason}).
 */
public enum ImageRejectReason {

	/**
	 * The magic bytes do not match a supported type, or the declared type.
	 */
	TYPE_MISMATCH,

	/**
	 * A valid image of a type the pipeline does not decode, such as HEIC (DEC-LS-4).
	 */
	UNSUPPORTED_TYPE,

	TOO_LARGE,

	DECODE_ERROR

}
