package vn.edu.uit.nearby.nearby_backend.listing.domain;

import vn.edu.uit.nearby.nearby_backend.shared.image.ProcessedImage;

import vn.edu.uit.nearby.nearby_backend.shared.image.ImageRejectReason;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

/**
 * One uploaded listing image (FR-LS-02, OI-03): a private original that is never served
 * and, once processed, two WebP derivatives without EXIF/GPS. It belongs to its uploader
 * until it is attached to one of the uploader's listings.
 */
@Getter
@Entity
@Table(name = "listing_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingImage {

	@Id
	private UUID id;

	@Column(name = "listing_id")
	private @Nullable UUID listingId;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(name = "original_key", nullable = false, updatable = false)
	private String originalKey;

	@Column(name = "thumb_key")
	private @Nullable String thumbKey;

	@Column(name = "large_key")
	private @Nullable String largeKey;

	private @Nullable Short position;

	@Column(name = "mime_type")
	private @Nullable String mimeType;

	@Column(name = "size_bytes")
	private @Nullable Integer sizeBytes;

	private @Nullable Integer width;

	private @Nullable Integer height;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false)
	private ImageProcessingStatus processingStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "reject_reason")
	private @Nullable ImageRejectReason rejectReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "processed_at")
	private @Nullable Instant processedAt;

	private ListingImage(UUID id, UUID ownerId, String originalKey, Instant now) {
		this.id = id;
		this.ownerId = ownerId;
		this.originalKey = originalKey;
		this.processingStatus = ImageProcessingStatus.UPLOADING;
		this.createdAt = now.truncatedTo(ChronoUnit.MILLIS);
	}

	/**
	 * An upload URL was handed out; the object may not exist yet.
	 */
	public static ListingImage uploading(UUID id, UUID ownerId, String originalKey, Instant now) {
		return new ListingImage(id, ownerId, originalKey, now);
	}

	public boolean isReady() {
		return processingStatus == ImageProcessingStatus.READY;
	}

	public boolean isProcessed() {
		return processingStatus != ImageProcessingStatus.UPLOADING;
	}

	public boolean isAttached() {
		return listingId != null;
	}

	public void markReady(ProcessedImage processed, Instant now) {
		requireUploading();
		processingStatus = ImageProcessingStatus.READY;
		thumbKey = processed.thumbKey();
		largeKey = processed.largeKey();
		mimeType = processed.mimeType();
		sizeBytes = processed.sizeBytes();
		width = processed.width();
		height = processed.height();
		processedAt = now.truncatedTo(ChronoUnit.MILLIS);
	}

	public void reject(ImageRejectReason reason, Instant now) {
		requireUploading();
		processingStatus = ImageProcessingStatus.REJECTED;
		rejectReason = reason;
		processedAt = now.truncatedTo(ChronoUnit.MILLIS);
	}

	/**
	 * Puts the image at {@code position} of the seller's listing (FR-LS-01). An image
	 * already on that listing can move to another position (FR-LS-06).
	 * @throws UnprocessableRequestException {@code IMAGE_INVALID} when the image is not
	 * the seller's, not READY, or attached to another listing
	 */
	public void attachTo(UUID sellerId, UUID targetListingId, int imagePosition) {
		boolean attachedElsewhere = listingId != null && !listingId.equals(targetListingId);
		if (!ownerId.equals(sellerId) || !isReady() || attachedElsewhere) {
			throw new UnprocessableRequestException(ErrorCodes.IMAGE_INVALID, "Image " + id
					+ " cannot be used: it must be your own processed image, not used by another listing.");
		}
		listingId = targetListingId;
		position = (short) imagePosition;
	}

	/**
	 * Removed from its listing by an edit; the orphan cleanup deletes it later
	 * (UC-SY-15).
	 */
	public void detach() {
		listingId = null;
		position = null;
	}

	/**
	 * UC-SY-15: not attached {@code ttl} after the upload started.
	 */
	public boolean isOrphanAt(Instant now, Duration ttl) {
		return listingId == null && !now.isBefore(createdAt.plus(ttl));
	}

	private void requireUploading() {
		if (isProcessed()) {
			throw new IllegalStateException("Image " + id + " was already processed: " + processingStatus);
		}
	}

}
