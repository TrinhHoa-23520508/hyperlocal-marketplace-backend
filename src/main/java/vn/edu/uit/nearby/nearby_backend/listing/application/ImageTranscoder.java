package vn.edu.uit.nearby.nearby_backend.listing.application;

import vn.edu.uit.nearby.nearby_backend.listing.domain.ImageFormat;
import vn.edu.uit.nearby.nearby_backend.listing.domain.UnusableImageException;

/**
 * UC-SY-10: turns an uploaded original into the 320 px and 1080 px WebP derivatives,
 * upright and without EXIF/GPS (FR-LS-02, NFR-31). Implemented with native libwebp in
 * infrastructure.
 */
public interface ImageTranscoder {

	int THUMBNAIL_PX = 320;

	int LARGE_PX = 1080;

	/**
	 * @throws UnusableImageException {@code DECODE_ERROR} for content that cannot be
	 * decoded, {@code TOO_LARGE} for dimensions the pipeline will not decode
	 */
	TranscodedImage transcode(byte[] original, ImageFormat format);

	/**
	 * @param width width of the original as shown, after its EXIF orientation
	 */
	record TranscodedImage(int width, int height, byte[] thumbnail, byte[] large) {
	}

}
