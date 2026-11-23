package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.chat.domain.ChatImage;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ChatImageStatus;
import vn.edu.uit.nearby.nearby_backend.chat.domain.MessageContent;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository.ChatImageRow;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.StorageKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageRejectReason;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage.PresignedUrl;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage.StoredObject;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageUploadPolicy;

/**
 * UC-CH-03: a member uploads up to five images for a message with presigned URLs
 * (FR-CH-04), the pipeline strips their metadata (UC-SY-10), and only the two members of
 * the conversation can fetch the result through short-lived signed links.
 */
@Service
@Transactional
public class ChatImageService {

	private final ChatImageRepository images;

	private final ConversationRepository conversations;

	private final ImageStorage storage;

	private final ChatParameters parameters;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ChatImageService(ChatImageRepository images, ConversationRepository conversations, ImageStorage storage,
			ChatParameters parameters, ApplicationEventPublisher events, Clock clock) {
		this.images = images;
		this.conversations = conversations;
		this.storage = storage;
		this.parameters = parameters;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * @param uploadMethod always PUT, with {@code contentType} as the Content-Type header
	 */
	public record UploadTicket(UUID imageId, String uploadUrl, String uploadMethod, String contentType,
			Instant expiresAt, long maxSizeBytes) {
	}

	/**
	 * What the app polls until the image is READY or REJECTED; a rejected image shows the
	 * retry button (FR-CH-04 "nút thử lại").
	 */
	public record ImageStatusView(UUID imageId, ChatImageStatus status, @Nullable String rejectReason,
			@Nullable String thumbnailUrl, @Nullable String url) {
	}

	/**
	 * Raised inside the module when an upload is complete; processed after commit.
	 */
	record ChatImageUploaded(UUID imageId) {
	}

	/**
	 * @throws ResourceNotFoundException {@code CONVERSATION_NOT_FOUND} for a stranger
	 * @throws InvalidRequestException {@code INVALID_IMAGE} for a type other than JPEG,
	 * PNG or WebP, or a declared size over {@code image.max_bytes}
	 */
	public UploadTicket requestUpload(UUID ownerId, UUID conversationId, @Nullable String contentType, long sizeBytes) {
		requireMember(ownerId, conversationId);
		ImageUploadPolicy policy = parameters.imageUploadPolicy();
		String standardType = policy.validate(contentType, sizeBytes);
		UUID imageId = UUID.randomUUID();
		String originalKey = StorageKeyPrefixes.CHAT_INCOMING + ownerId + "/" + imageId + "."
				+ policy.fileExtensionOf(standardType);
		images.insertUploading(imageId, ownerId, conversationId, originalKey, clock.instant());
		PresignedUrl upload = storage.presignUpload(originalKey, standardType);
		return new UploadTicket(imageId, upload.url(), upload.method(), standardType, upload.expiresAt(),
				policy.maxSizeBytes());
	}

	/**
	 * Upload-complete: checks what was really stored, refuses an object over the size
	 * limit at once, otherwise queues UC-SY-10. Calling it again returns the status.
	 * @throws InvalidRequestException {@code INVALID_IMAGE} when nothing was uploaded or
	 * the object is too large; the image is still marked REJECTED, so this method does
	 * not roll back for it
	 * @throws ResourceNotFoundException {@code IMAGE_NOT_FOUND} unless the caller owns it
	 */
	@Transactional(noRollbackFor = InvalidRequestException.class)
	public ImageStatusView completeUpload(UUID ownerId, UUID imageId) {
		ChatImageRow image = images.findById(imageId)
			.filter(candidate -> candidate.ownerId().equals(ownerId))
			.orElseThrow(ChatImageService::notFound);
		if (image.isProcessed()) {
			return statusOf(image);
		}
		StoredObject stored = storage.find(image.originalKey())
			.orElseThrow(() -> new InvalidRequestException(ErrorCodes.INVALID_IMAGE,
					"Nothing was uploaded for this image; request a new upload URL."));
		if (stored.sizeBytes() > parameters.imageUploadPolicy().maxSizeBytes()) {
			images.markRejected(imageId, ImageRejectReason.TOO_LARGE, clock.instant());
			storage.delete(image.originalKey());
			throw new InvalidRequestException(ErrorCodes.INVALID_IMAGE, "The image is larger than allowed.");
		}
		events.publishEvent(new ChatImageUploaded(imageId));
		return statusOf(image);
	}

	/**
	 * The owner sees the image from the upload on; the other member only once a message
	 * uses it, so nobody sees a photo that was never sent (BR-05).
	 * @throws ResourceNotFoundException {@code IMAGE_NOT_FOUND} for everyone else
	 */
	@Transactional(readOnly = true)
	public ImageStatusView view(UUID userId, UUID imageId) {
		ChatImageRow image = images.findById(imageId)
			.filter(candidate -> conversations.findById(candidate.conversationId())
				.filter(conversation -> conversation.isMember(userId))
				.isPresent())
			.filter(candidate -> candidate.ownerId().equals(userId) || candidate.isAttached())
			.orElseThrow(ChatImageService::notFound);
		return statusOf(image);
	}

	/**
	 * FR-CH-04: the images of an IMAGE message, in the order the sender chose.
	 * @throws InvalidRequestException {@code VALIDATION_FAILED} for none, more than five
	 * or a repeated id
	 * @throws UnprocessableRequestException {@code IMAGE_INVALID} when one is not the
	 * sender's READY, unused image of this conversation
	 */
	List<ChatImage> readyImagesOf(UUID senderId, UUID conversationId, List<UUID> imageIds) {
		if (imageIds.isEmpty() || imageIds.size() > MessageContent.MAX_IMAGES
				|| new LinkedHashSet<>(imageIds).size() != imageIds.size()) {
			throw InvalidRequestException.invalidField("imageIds",
					"A message carries 1 to " + MessageContent.MAX_IMAGES + " different images.");
		}
		Map<UUID, ChatImageRow> found = images.findAllById(imageIds)
			.stream()
			.collect(Collectors.toMap(ChatImageRow::id, Function.identity()));
		return imageIds.stream().map(id -> {
			ChatImageRow image = found.get(id);
			if (image == null || !image.ownerId().equals(senderId) || !image.conversationId().equals(conversationId)
					|| image.status() != ChatImageStatus.READY || image.isAttached()) {
				throw new UnprocessableRequestException(ErrorCodes.IMAGE_INVALID,
						"Image " + id + " is not ready to be sent in this conversation.");
			}
			return new ChatImage(image.largeKey(), image.thumbKey(), image.width(), image.height());
		}).toList();
	}

	/**
	 * Marks the images as used by the message. A concurrent send of the same image
	 * attaches fewer rows, and the whole send is rolled back.
	 */
	void attach(List<UUID> imageIds, long messageId) {
		if (images.attach(imageIds, messageId) != imageIds.size()) {
			throw new UnprocessableRequestException(ErrorCodes.IMAGE_INVALID,
					"An image was already used by another message.");
		}
	}

	private void requireMember(UUID userId, UUID conversationId) {
		conversations.findById(conversationId)
			.filter(conversation -> conversation.isMember(userId))
			.orElseThrow(
					() -> new ResourceNotFoundException(ErrorCodes.CONVERSATION_NOT_FOUND, "Conversation not found."));
	}

	private ImageStatusView statusOf(ChatImageRow image) {
		boolean ready = image.status() == ChatImageStatus.READY;
		return new ImageStatusView(image.id(), image.status(), image.rejectReason(),
				ready ? storage.presignDownload(image.thumbKey()).url() : null,
				ready ? storage.presignDownload(image.largeKey()).url() : null);
	}

	private static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException(ErrorCodes.IMAGE_NOT_FOUND, "Image not found.");
	}

}
