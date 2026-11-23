package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository.ChatImageRow;
import vn.edu.uit.nearby.nearby_backend.shared.constant.StorageKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageFormat;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageRejectReason;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageTranscoder;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageTranscoder.TranscodedImage;
import vn.edu.uit.nearby.nearby_backend.shared.image.UnusableImageException;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;

/**
 * UC-SY-10 for one chat image, run by {@link ChatImageQueue}: verify the type from the
 * magic bytes, re-encode without EXIF or GPS, store the two WebP derivatives (FR-CH-04,
 * NFR-31). A refused upload is REJECTED and its original deleted. An image already
 * processed is left alone, so a replayed event changes nothing.
 */
@Slf4j
@Component
class ChatImageProcessor {

	static final String WEBP_CONTENT_TYPE = "image/webp";

	private final ChatImageRepository images;

	private final ImageStorage storage;

	private final ImageTranscoder transcoder;

	private final ChatParameters parameters;

	private final Clock clock;

	ChatImageProcessor(ChatImageRepository images, ImageStorage storage, ImageTranscoder transcoder,
			ChatParameters parameters, Clock clock) {
		this.images = images;
		this.storage = storage;
		this.transcoder = transcoder;
		this.parameters = parameters;
		this.clock = clock;
	}

	@Transactional
	public void process(UUID imageId) {
		ChatImageRow image = images.findById(imageId).orElse(null);
		if (image == null || image.isProcessed()) {
			return;
		}
		try {
			byte[] original = storage.read(image.originalKey())
				.orElseThrow(() -> new UnusableImageException(ImageRejectReason.DECODE_ERROR, "The upload is gone."));
			ImageFormat format = verifiedFormat(original, image);
			TranscodedImage transcoded = transcoder.transcode(original, format);
			String thumbKey = derivativeKey(imageId, ImageTranscoder.THUMBNAIL_PX);
			String largeKey = derivativeKey(imageId, ImageTranscoder.LARGE_PX);
			storage.store(thumbKey, transcoded.thumbnail(), WEBP_CONTENT_TYPE);
			storage.store(largeKey, transcoded.large(), WEBP_CONTENT_TYPE);
			images.markReady(imageId, thumbKey, largeKey, transcoded.width(), transcoded.height(), clock.instant());
		}
		catch (UnusableImageException ex) {
			log.info("Chat image {} rejected: {}", imageId, ex.reason());
			images.markRejected(imageId, ex.reason(), clock.instant());
			storage.delete(image.originalKey());
		}
	}

	private ImageFormat verifiedFormat(byte[] original, ChatImageRow image) {
		if (original.length > parameters.imageUploadPolicy().maxSizeBytes()) {
			throw new UnusableImageException(ImageRejectReason.TOO_LARGE, "The image is larger than allowed.");
		}
		ImageFormat format = ImageFormat.detect(original)
			.orElseThrow(() -> new UnusableImageException(ImageRejectReason.TYPE_MISMATCH,
					"The content is not a JPEG, PNG, WebP or HEIC image."));
		String declaredType = storage.find(image.originalKey())
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
		return StorageKeyPrefixes.CHAT_IMAGES + imageId + "_" + sizePx + ".webp";
	}

}
