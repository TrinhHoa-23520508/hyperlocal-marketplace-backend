package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.listing.domain.ImageProcessingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ImageRejectReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingImage;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.StorageKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage.PresignedUrl;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage.StoredObject;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageUploadPolicy;

/**
 * FR-LS-02 upload steps of A13: one presigned PUT URL per image under the private
 * {@code incoming/} prefix, then upload-complete, which hands the image to UC-SY-10.
 */
@Service
@Transactional
public class ListingImageService {

	private final ListingImageRepository images;

	private final ImageStorage storage;

	private final ListingParameters parameters;

	private final ListingImageUrls imageUrls;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ListingImageService(ListingImageRepository images, ImageStorage storage, ListingParameters parameters,
			ListingImageUrls imageUrls, ApplicationEventPublisher events, Clock clock) {
		this.images = images;
		this.storage = storage;
		this.parameters = parameters;
		this.imageUrls = imageUrls;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * @throws InvalidRequestException {@code INVALID_IMAGE} for a type other than JPEG,
	 * PNG or WebP (HEIC: DEC-LS-4) or a declared size over {@code image.max_bytes}
	 */
	public UploadTicket requestUpload(UUID ownerId, @Nullable String contentType, long sizeBytes) {
		ImageUploadPolicy policy = parameters.imageUploadPolicy();
		String standardType = policy.validate(contentType, sizeBytes);
		UUID imageId = UUID.randomUUID();
		String originalKey = StorageKeyPrefixes.LISTING_INCOMING + ownerId + "/" + imageId + "."
				+ policy.fileExtensionOf(standardType);
		images.save(ListingImage.uploading(imageId, ownerId, originalKey, clock.instant()));
		PresignedUrl upload = storage.presignUpload(originalKey, standardType);
		return new UploadTicket(imageId, upload.url(), upload.method(), standardType, upload.expiresAt(),
				policy.maxSizeBytes());
	}

	/**
	 * Upload-complete: checks what was really stored, refuses an object over the size
	 * limit at once (and deletes it), otherwise queues UC-SY-10. Calling it again returns
	 * the current status.
	 * @throws InvalidRequestException {@code INVALID_IMAGE} when nothing was uploaded or
	 * the object is too large
	 */
	public ImageStatusView completeUpload(UUID ownerId, UUID imageId) {
		ListingImage image = getOwned(ownerId, imageId);
		if (image.isProcessed()) {
			return statusOf(image);
		}
		StoredObject stored = storage.find(image.getOriginalKey())
			.orElseThrow(() -> new InvalidRequestException(ErrorCodes.INVALID_IMAGE,
					"Nothing was uploaded for this image; request a new upload URL."));
		if (stored.sizeBytes() > parameters.imageUploadPolicy().maxSizeBytes()) {
			image.reject(ImageRejectReason.TOO_LARGE, clock.instant());
			storage.delete(image.getOriginalKey());
			throw new InvalidRequestException(ErrorCodes.INVALID_IMAGE, "The image is larger than allowed.");
		}
		events.publishEvent(new ListingImageUploaded(imageId));
		return statusOf(image);
	}

	@Transactional(readOnly = true)
	public ImageStatusView status(UUID ownerId, UUID imageId) {
		return statusOf(getOwned(ownerId, imageId));
	}

	private ListingImage getOwned(UUID ownerId, UUID imageId) {
		return images.findByIdAndOwnerId(imageId, ownerId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.IMAGE_NOT_FOUND, "Image not found."));
	}

	private ImageStatusView statusOf(ListingImage image) {
		return new ImageStatusView(image.getId(), image.getProcessingStatus(), image.getRejectReason(),
				image.isReady() ? imageUrls.thumbnailUrl(image) : null);
	}

	/**
	 * @param uploadMethod always PUT, with {@code contentType} as the Content-Type header
	 */
	public record UploadTicket(UUID imageId, String uploadUrl, String uploadMethod, String contentType,
			Instant expiresAt, long maxSizeBytes) {
	}

	/**
	 * What A13 polls until the image is READY or REJECTED (UC-LS-01 A5).
	 */
	public record ImageStatusView(UUID imageId, ImageProcessingStatus status, @Nullable ImageRejectReason rejectReason,
			@Nullable String thumbnailUrl) {
	}

	/**
	 * Raised inside the module when an upload is complete; processed after commit.
	 */
	record ListingImageUploaded(UUID imageId) {
	}

}
