package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.edu.uit.nearby.nearby_backend.chat.application.ChatImageService.ImageStatusView;
import vn.edu.uit.nearby.nearby_backend.chat.application.ChatImageService.UploadTicket;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ChatImage;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ChatImageStatus;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageRejectReason;
import vn.edu.uit.nearby.nearby_backend.support.InMemoryImageStorage;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;
import vn.edu.uit.nearby.nearby_backend.support.TestImages;

/**
 * FR-CH-04, UC-SY-10 and UC-SY-15 for chat images with the real transcoder and the
 * in-memory object store.
 */
@IntegrationTest
class ChatImageIntegrationTests {

	private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(20);

	private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

	@Autowired
	ChatImageService imageService;

	@Autowired
	ChatImageProcessor processor;

	@Autowired
	ChatImageCleanupJob cleanup;

	@Autowired
	ChatImageRepository images;

	@Autowired
	ConversationRepository conversations;

	@Autowired
	InMemoryImageStorage storage;

	private final UUID buyer = UUID.randomUUID();

	private final UUID seller = UUID.randomUUID();

	private UUID openConversation() {
		return conversations.insertIfAbsent(UUID.randomUUID(), buyer, seller, NOW).conversation().id();
	}

	private UUID uploadedAndProcessed(UUID conversationId, UUID owner, String contentType, byte[] content) {
		UploadTicket ticket = imageService.requestUpload(owner, conversationId, contentType, content.length);
		storage.simulateUpload(images.findById(ticket.imageId()).orElseThrow().originalKey(), contentType, content);
		imageService.completeUpload(owner, ticket.imageId());
		await().atMost(PROCESSING_TIMEOUT).until(() -> images.findById(ticket.imageId()).orElseThrow().isProcessed());
		return ticket.imageId();
	}

	@Nested
	@DisplayName("upload (FR-CH-04)")
	class Upload {

		@Test
		@DisplayName("a member gets a presigned PUT URL under the private chat-incoming/ prefix")
		void handsOutAPresignedUploadUrl() {
			UUID id = openConversation();

			UploadTicket ticket = imageService.requestUpload(buyer, id, "image/jpeg", 2_000_000);

			assertThat(ticket.uploadMethod()).isEqualTo("PUT");
			assertThat(ticket.uploadUrl()).contains("chat-incoming/" + buyer + "/" + ticket.imageId());
			assertThat(ticket.maxSizeBytes()).isEqualTo(5_242_880);
			assertThat(images.findById(ticket.imageId())).hasValueSatisfying(row -> {
				assertThat(row.status()).isEqualTo(ChatImageStatus.UPLOADING);
				assertThat(row.conversationId()).isEqualTo(id);
			});
		}

		@Test
		@DisplayName("CONVERSATION_NOT_FOUND for someone who is not a member")
		void aStrangerCannotUpload() {
			UUID id = openConversation();

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> imageService.requestUpload(UUID.randomUUID(), id, "image/jpeg", 1_000));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
		}

		@Test
		@DisplayName("INVALID_IMAGE for another type or a size over 5 MB")
		void refusesWrongTypeAndSize() {
			UUID id = openConversation();

			assertThat(catchThrowableOfType(BusinessException.class,
					() -> imageService.requestUpload(buyer, id, "image/gif", 1_000))
				.getCode()).isEqualTo(ErrorCodes.INVALID_IMAGE);
			assertThat(catchThrowableOfType(BusinessException.class,
					() -> imageService.requestUpload(buyer, id, "image/jpeg", 5_242_881))
				.getCode()).isEqualTo(ErrorCodes.INVALID_IMAGE);
		}

		@Test
		void completingWithoutAnUploadIsInvalid() {
			UUID id = openConversation();
			UploadTicket ticket = imageService.requestUpload(buyer, id, "image/jpeg", 1_000);

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> imageService.completeUpload(buyer, ticket.imageId()));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.INVALID_IMAGE);
		}

		@Test
		void onlyTheOwnerCanComplete() {
			UUID id = openConversation();
			UploadTicket ticket = imageService.requestUpload(buyer, id, "image/jpeg", 1_000);

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> imageService.completeUpload(seller, ticket.imageId()));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.IMAGE_NOT_FOUND);
		}

		@Test
		@DisplayName("an object over the limit is refused at upload-complete and deleted")
		void anOversizedObjectIsRefused() {
			UUID id = openConversation();
			UploadTicket ticket = imageService.requestUpload(buyer, id, "image/jpeg", 1_000);
			String key = images.findById(ticket.imageId()).orElseThrow().originalKey();
			storage.simulateUpload(key, "image/jpeg", 9_000_000);

			BusinessException error = catchThrowableOfType(BusinessException.class,
					() -> imageService.completeUpload(buyer, ticket.imageId()));

			assertThat(error.getCode()).isEqualTo(ErrorCodes.INVALID_IMAGE);
			assertThat(storage.contains(key)).isFalse();
			assertThat(images.findById(ticket.imageId()).orElseThrow().status()).isEqualTo(ChatImageStatus.REJECTED);
		}

	}

	@Nested
	@DisplayName("processing (UC-SY-10)")
	class Processing {

		@Test
		@DisplayName("a JPEG becomes two WebP derivatives and the private original stays untouched")
		void producesTwoDerivatives() {
			UUID id = openConversation();

			UUID imageId = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(2000, 1500));

			var row = images.findById(imageId).orElseThrow();
			assertThat(row.status()).isEqualTo(ChatImageStatus.READY);
			assertThat(row.thumbKey()).isEqualTo("chat-images/" + imageId + "_320.webp");
			assertThat(row.largeKey()).isEqualTo("chat-images/" + imageId + "_1080.webp");
			assertThat(row.width()).isEqualTo(2000);
			assertThat(row.height()).isEqualTo(1500);
			assertThat(storage.contains(row.thumbKey())).isTrue();
			assertThat(storage.contains(row.largeKey())).isTrue();
		}

		@Test
		@DisplayName("content that does not match its declared type is REJECTED and the original deleted")
		void aTypeMismatchIsRejected() {
			UUID id = openConversation();

			UUID imageId = uploadedAndProcessed(id, buyer, "image/png", TestImages.jpeg(100, 100));

			var row = images.findById(imageId).orElseThrow();
			assertThat(row.status()).isEqualTo(ChatImageStatus.REJECTED);
			assertThat(row.rejectReason()).isEqualTo(ImageRejectReason.TYPE_MISMATCH.name());
			assertThat(storage.contains(row.originalKey())).isFalse();
		}

		@Test
		@DisplayName("FR-CH-04 / NFR-31: the derivatives carry no EXIF, even when the original has it")
		void theDerivativesHaveNoExif() {
			UUID id = openConversation();
			byte[] original = TestImages.jpegWithExif(800, 600, 6, java.nio.ByteOrder.BIG_ENDIAN);
			assertThat(new String(original, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("Exif");

			UUID imageId = uploadedAndProcessed(id, buyer, "image/jpeg", original);

			var row = images.findById(imageId).orElseThrow();
			assertThat(row.status()).isEqualTo(ChatImageStatus.READY);
			for (String key : List.of(row.thumbKey(), row.largeKey())) {
				byte[] derivative = storage.read(key).orElseThrow();
				assertThat(new String(derivative, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Exif")
					.doesNotContain("EXIF");
			}
		}

		@Test
		void processingTwiceChangesNothing() {
			UUID id = openConversation();
			UUID imageId = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(300, 300));
			var before = images.findById(imageId).orElseThrow();

			processor.process(imageId);

			assertThat(images.findById(imageId).orElseThrow()).isEqualTo(before);
		}

	}

	@Nested
	@DisplayName("who can see an image (BR-05, NFR-31)")
	class Visibility {

		@Test
		void theOwnerSeesItBeforeItIsSentAndTheCounterpartOnlyAfter() {
			UUID id = openConversation();
			UUID imageId = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(300, 300));

			ImageStatusView mine = imageService.view(buyer, imageId);
			assertThat(mine.status()).isEqualTo(ChatImageStatus.READY);
			assertThat(mine.url()).contains("chat-images/" + imageId + "_1080.webp");
			assertThat(mine.thumbnailUrl()).contains("chat-images/" + imageId + "_320.webp");
			assertThat(
					catchThrowableOfType(BusinessException.class, () -> imageService.view(seller, imageId)).getCode())
				.isEqualTo(ErrorCodes.IMAGE_NOT_FOUND);

			images.attach(List.of(imageId), 1);

			assertThat(imageService.view(seller, imageId).url()).isNotNull();
		}

		@Test
		void aStrangerNeverSeesIt() {
			UUID id = openConversation();
			UUID imageId = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(300, 300));
			images.attach(List.of(imageId), 1);

			assertThat(
					catchThrowableOfType(BusinessException.class, () -> imageService.view(UUID.randomUUID(), imageId))
						.getCode())
				.isEqualTo(ErrorCodes.IMAGE_NOT_FOUND);
		}

		@Test
		void aProcessingImageHasNoLinksYet() {
			UUID id = openConversation();
			UploadTicket ticket = imageService.requestUpload(buyer, id, "image/jpeg", 1_000);

			ImageStatusView view = imageService.view(buyer, ticket.imageId());

			assertThat(view.status()).isEqualTo(ChatImageStatus.UPLOADING);
			assertThat(view.url()).isNull();
			assertThat(view.thumbnailUrl()).isNull();
		}

	}

	@Nested
	@DisplayName("choosing the images of a message (FR-CH-04)")
	class Choosing {

		@Test
		void theSendersReadyImagesAreReturnedInTheOrderChosen() {
			UUID id = openConversation();
			UUID first = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(400, 300));
			UUID second = uploadedAndProcessed(id, buyer, "image/png", TestImages.png(200, 100));

			List<ChatImage> chosen = imageService.readyImagesOf(buyer, id, List.of(second, first));

			assertThat(chosen).extracting(ChatImage::width).containsExactly(200, 400);
			assertThat(chosen.getFirst().key()).isEqualTo("chat-images/" + second + "_1080.webp");
		}

		@Test
		@DisplayName("IMAGE_INVALID for someone else's image, an unfinished one, one of another conversation or an unknown id")
		void refusesImagesThatAreNotTheSenders() {
			UUID id = openConversation();
			UUID mine = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(100, 100));
			UUID pending = imageService.requestUpload(buyer, id, "image/jpeg", 1_000).imageId();
			UUID otherConversation = openConversation();

			assertThat(codeOfChoosing(seller, id, mine)).isEqualTo(ErrorCodes.IMAGE_INVALID);
			assertThat(codeOfChoosing(buyer, id, pending)).isEqualTo(ErrorCodes.IMAGE_INVALID);
			assertThat(codeOfChoosing(buyer, otherConversation, mine)).isEqualTo(ErrorCodes.IMAGE_INVALID);
			assertThat(codeOfChoosing(buyer, id, UUID.randomUUID())).isEqualTo(ErrorCodes.IMAGE_INVALID);
		}

		@Test
		void aUsedImageCannotBeSentAgain() {
			UUID id = openConversation();
			UUID imageId = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(100, 100));
			imageService.attach(List.of(imageId), 5);

			assertThat(codeOfChoosing(buyer, id, imageId)).isEqualTo(ErrorCodes.IMAGE_INVALID);
			assertThat(catchThrowableOfType(BusinessException.class, () -> imageService.attach(List.of(imageId), 6))
				.getCode()).isEqualTo(ErrorCodes.IMAGE_INVALID);
		}

		@Test
		@DisplayName("one to five different images: none, six or a repeated id is 400")
		void countAndRepetitionAreChecked() {
			UUID id = openConversation();
			UUID imageId = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(100, 100));

			assertThat(catchThrowableOfType(BusinessException.class,
					() -> imageService.readyImagesOf(buyer, id, List.of()))
				.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
			assertThat(catchThrowableOfType(BusinessException.class,
					() -> imageService.readyImagesOf(buyer, id, Collections.nCopies(6, UUID.randomUUID())))
				.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
			assertThat(catchThrowableOfType(BusinessException.class,
					() -> imageService.readyImagesOf(buyer, id, List.of(imageId, imageId)))
				.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
		}

		private String codeOfChoosing(UUID sender, UUID conversationId, UUID imageId) {
			return catchThrowableOfType(BusinessException.class,
					() -> imageService.readyImagesOf(sender, conversationId, List.of(imageId)))
				.getCode();
		}

	}

	@Nested
	@DisplayName("UC-SY-15: orphan cleanup")
	class Orphans {

		@Test
		@DisplayName("an unused image is deleted with its objects once the TTL has passed; a used one stays")
		void deletesOnlyUnusedImagesAfterTheTtl() {
			UUID id = openConversation();
			UUID unused = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(100, 100));
			UUID used = uploadedAndProcessed(id, buyer, "image/jpeg", TestImages.jpeg(100, 100));
			images.attach(List.of(used), 9);
			var unusedRow = images.findById(unused).orElseThrow();

			assertThat(cleanup.runAt(Instant.now().plus(Duration.ofHours(1)))).isZero();
			int deleted = cleanup.runAt(Instant.now().plus(Duration.ofHours(25)));

			assertThat(deleted).isGreaterThanOrEqualTo(1);
			assertThat(images.findById(unused)).isEmpty();
			assertThat(storage.contains(unusedRow.originalKey())).isFalse();
			assertThat(storage.contains(unusedRow.thumbKey())).isFalse();
			assertThat(storage.contains(unusedRow.largeKey())).isFalse();
			assertThat(images.findById(used)).isPresent();
		}

		@Test
		void runningTwiceIsSafe() {
			cleanup.runAt(Instant.now().plus(Duration.ofHours(25)));

			assertThat(cleanup.runAt(Instant.now().plus(Duration.ofHours(25)))).isZero();
		}

		@Test
		void theScheduledRunWorks() {
			cleanup.run();
		}

	}

}
