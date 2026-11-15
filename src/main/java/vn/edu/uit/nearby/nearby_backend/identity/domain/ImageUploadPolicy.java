package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ImageContentTypes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * Accepted image formats and size (FR-ID-11). Checked once when the client asks for an
 * upload URL and again on the uploaded object, because a presigned URL cannot limit size.
 */
public record ImageUploadPolicy(long maxSizeBytes) {

	private static final long BYTES_PER_MEGABYTE = 1024 * 1024;

	/**
	 * @return the standard content type: case and parameters are dropped, and
	 * {@code image/jpg} becomes {@code image/jpeg}
	 * @throws InvalidRequestException {@code INVALID_IMAGE} for another format or a size
	 * over the limit
	 */
	public String validate(@Nullable String contentType, long sizeBytes) {
		String standardType = standardTypeOf(contentType);
		if (!ImageContentTypes.FILE_EXTENSIONS.containsKey(standardType)) {
			throw new InvalidRequestException(ErrorCodes.INVALID_IMAGE, "Only JPEG, PNG and WebP images are accepted.");
		}
		if (sizeBytes <= 0 || sizeBytes > maxSizeBytes) {
			throw new InvalidRequestException(ErrorCodes.INVALID_IMAGE,
					"The image must be at most " + maxSizeBytes / BYTES_PER_MEGABYTE + " MB.");
		}
		return standardType;
	}

	public String fileExtensionOf(String contentType) {
		return ImageContentTypes.FILE_EXTENSIONS.get(standardTypeOf(contentType));
	}

	private static String standardTypeOf(@Nullable String contentType) {
		if (contentType == null) {
			return "";
		}
		String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		return ImageContentTypes.ALIASES.getOrDefault(mediaType, mediaType);
	}

}
