package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import vn.edu.uit.nearby.nearby_backend.listing.application.ImageTranscoder.TranscodedImage;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ImageFormat;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ImageRejectReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingImage;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ProcessedImage;
import vn.edu.uit.nearby.nearby_backend.listing.domain.UnusableImageException;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.StorageKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;

/**
 * UC-SY-10 for one upload, run by {@link ListingImageQueue}: verify the type from the
 * magic bytes, re-encode without metadata, store the two WebP derivatives. A refused
 * upload is REJECTED and its original deleted (FR-LS-02). Images already processed are
 * left alone, so a replayed event changes nothing.
 */
@Slf4j
@Component
class ListingImageProcessor {

	static final String WEBP_CONTENT_TYPE = "image/webp";

	private final ListingImageRepository images;

	private final ImageStorage storage;

	private final ImageTranscoder transcoder;

	private final ListingParameters parameters;

	private final Clock clock;

	ListingImageProcessor(ListingImageRepository images, ImageStorage storage, ImageTranscoder transcoder,
			ListingParameters parameters, Clock clock) {
		this.images = images;
		this.storage = storage;
		this.transcoder = transcoder;
		this.parameters = parameters;
		this.clock = clock;
	}

	@Transactional
	public void process(UUID imageId) {
		ListingImage image = images.findById(imageId).orElse(null);
		if (image == null || image.isProcessed()) {
			return;
		}
		String originalKey = image.getOriginalKey();
		try {
			byte[] original = storage.read(originalKey)
				.orElseThrow(() -> new UnusableImageException(ImageRejectReason.DECODE_ERROR, "The upload is gone."));
			ImageFormat format = verifiedFormat(original, image);
			TranscodedImage transcoded = transcoder.transcode(original, format);
			String thumbKey = derivativeKey(imageId, ImageTranscoder.THUMBNAIL_PX);
			String largeKey = derivativeKey(imageId, ImageTranscoder.LARGE_PX);
			storage.store(thumbKey, transcoded.thumbnail(), WEBP_CONTENT_TYPE);
			storage.store(largeKey, transcoded.large(), WEBP_CONTENT_TYPE);
			image.markReady(new ProcessedImage(thumbKey, largeKey, format.mimeType(), original.length,
					transcoded.width(), transcoded.height()), clock.instant());
		}
		catch (UnusableImageException ex) {
			log.info("Listing image {} rejected: {}", imageId, ex.reason());
			image.reject(ex.reason(), clock.instant());
			storage.delete(originalKey);
		}
	}

	/**
	 * The magic bytes decide the type; it must also be the type declared for the upload
	 * URL (the Content-Type it was signed for) and one the pipeline decodes.
	 */
	private ImageFormat verifiedFormat(byte[] original, ListingImage image) {
		if (original.length > parameters.imageUploadPolicy().maxSizeBytes()) {
			throw new UnusableImageException(ImageRejectReason.TOO_LARGE, "The image is larger than allowed.");
		}
		ImageFormat format = ImageFormat.detect(original)
			.orElseThrow(() -> new UnusableImageException(ImageRejectReason.TYPE_MISMATCH,
					"The content is not a JPEG, PNG, WebP or HEIC image."));
		String declaredType = storage.find(image.getOriginalKey())
			.map(ImageStorage.StoredObject::contentType)
			.orElse(null);
		if (!format.mimeType().equals(declaredType)) {
			throw new UnusableImageException(ImageRejectReason.TYPE_MISMATCH,
					"The content is " + format.mimeType() + ", not " + declaredType + ".");
		}
		if (!format.isDecodable()) {
			throw new UnusableImageException(ImageRejectReason.UNSUPPORTED_TYPE,
					format.mimeType() + " is not supported yet; upload a JPEG.");
		}
		return format;
	}

	private static String derivativeKey(UUID imageId, int sizePx) {
		return StorageKeyPrefixes.LISTING_IMAGES + imageId + "_" + sizePx + ".webp";
	}

}
