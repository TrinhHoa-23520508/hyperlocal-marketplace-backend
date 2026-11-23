package vn.edu.uit.nearby.nearby_backend.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.support.TestImages;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingImageService.ImageStatusView;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingImageService.UploadTicket;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ImageProcessingStatus;
import vn.edu.uit.nearby.nearby_backend.shared.image.ImageRejectReason;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingImage;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.support.InMemoryImageStorage;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * FR-LS-02, UC-SY-10 and UC-SY-15 with the real transcoder and the in-memory object
 * store.
 */
@IntegrationTest
class ListingImageIntegrationTests {

	private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(20);

	@Autowired
	ListingImageService imageService;

	@Autowired
	ListingImageProcessor processor;

	@Autowired
	OrphanImageCleanupJob orphanCleanup;

	@Autowired
	ListingImageRepository images;

	@Autowired
	InMemoryImageStorage storage;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ListingRepository listings;

	private final UUID owner = UUID.randomUUID();

	@Nested
	class Upload {

		@Test
		@DisplayName("FR-LS-02: one presigned PUT URL per image under the private incoming/ prefix")
		void handsOutAPresignedUploadUrl() {
			UploadTicket ticket = imageService.requestUpload(owner, "image/jpeg", 2_000_000);

			assertThat(ticket.uploadMethod()).isEqualTo("PUT");
			assertThat(ticket.uploadUrl()).contains("incoming/" + owner + "/" + ticket.imageId() + ".jpg");
			assertThat(ticket.maxSizeBytes()).isEqualTo(5_242_880);
			assertThat(images.findById(ticket.imageId()).orElseThrow().getProcessingStatus())
				.isEqualTo(ImageProcessingStatus.UPLOADING);
		}

		@Test
		@DisplayName("FR-LS-02: a declared size over 5 242 880 bytes is a 400 INVALID_IMAGE")
		void refusesADeclaredSizeOverTheLimit() {
			assertCode(() -> imageService.requestUpload(owner, "image/jpeg", 5_242_881), "INVALID_IMAGE");
		}

		@Test
		@DisplayName("DEC-LS-4: HEIC and other types are a 400 INVALID_IMAGE")
		void refusesUnsupportedTypes() {
			assertCode(() -> imageService.requestUpload(owner, "image/heic", 1_000), "INVALID_IMAGE");
			assertCode(() -> imageService.requestUpload(owner, "image/gif", 1_000), "INVALID_IMAGE");
		}

		@Test
		@DisplayName("FR-LS-02: upload-complete without an upload is a 400 INVALID_IMAGE")
		void refusesToCompleteWithoutAnUpload() {
			UploadTicket ticket = imageService.requestUpload(owner, "image/jpeg", 1_000);

			assertCode(() -> imageService.completeUpload(owner, ticket.imageId()), "INVALID_IMAGE");
		}

		@Test
		@DisplayName("FR-LS-02: an uploaded object over the limit is rejected and deleted at once")
		void rejectsAnOversizedUploadAtOnce() {
			UploadTicket ticket = imageService.requestUpload(owner, "image/jpeg", 1_000);
			String key = originalKeyOf(ticket.imageId());
			storage.simulateUpload(key, "image/jpeg", 6_000_000);

			assertCode(() -> imageService.completeUpload(owner, ticket.imageId()), "INVALID_IMAGE");
			assertThat(storage.contains(key)).isFalse();
		}

		@Test
		@DisplayName("FR-ID-18: another user's image does not exist for them")
		void anotherUsersImageIsNotFound() {
			UploadTicket ticket = imageService.requestUpload(owner, "image/jpeg", 1_000);

			assertCode(() -> imageService.status(UUID.randomUUID(), ticket.imageId()), "IMAGE_NOT_FOUND");
			assertCode(() -> imageService.completeUpload(UUID.randomUUID(), ticket.imageId()), "IMAGE_NOT_FOUND");
		}

	}

	@Nested
	class Processing {

		@Test
		@DisplayName("UC-SY-10: after upload-complete the image becomes READY with two WebP derivatives")
		void processesAnUploadedJpeg() {
			UploadTicket ticket = imageService.requestUpload(owner, "image/jpeg", 1_000);
			storage.simulateUpload(originalKeyOf(ticket.imageId()), "image/jpeg", TestImages.jpeg(2000, 1500));

			ImageStatusView accepted = imageService.completeUpload(owner, ticket.imageId());

			assertThat(accepted.status()).isEqualTo(ImageProcessingStatus.UPLOADING);
			ImageStatusView ready = awaitProcessed(ticket.imageId());
			assertThat(ready.status()).isEqualTo(ImageProcessingStatus.READY);
			assertThat(ready.thumbnailUrl()).contains("listing-images/" + ticket.imageId() + "_320.webp");
			ListingImage stored = images.findById(ticket.imageId()).orElseThrow();
			assertThat(stored.getWidth()).isEqualTo(2000);
			assertThat(stored.getMimeType()).isEqualTo("image/jpeg");
			assertThat(storage.read(stored.getLargeKey())).hasValueSatisfying(
					webp -> assertThat(new String(webp, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF"));
		}

		@Test
		@DisplayName("UC-SY-10: upload-complete again after processing returns the current status")
		void completingAgainReturnsTheStatus() {
			UUID imageId = uploadedAndProcessed("image/png", TestImages.png(100, 100));

			assertThat(imageService.completeUpload(owner, imageId).status()).isEqualTo(ImageProcessingStatus.READY);
		}

		@Test
		@DisplayName("FR-LS-02: magic bytes that do not match the declared type: REJECTED, original deleted")
		void rejectsATypeMismatch() {
			UUID imageId = uploadedAndProcessed("image/png", TestImages.jpeg(100, 100));

			ListingImage rejected = images.findById(imageId).orElseThrow();
			assertThat(rejected.getProcessingStatus()).isEqualTo(ImageProcessingStatus.REJECTED);
			assertThat(rejected.getRejectReason()).isEqualTo(ImageRejectReason.TYPE_MISMATCH);
			assertThat(storage.contains(rejected.getOriginalKey())).isFalse();
		}

		@Test
		@DisplayName("FR-LS-02: a text file renamed .jpg is REJECTED (TYPE_MISMATCH)")
		void rejectsContentThatIsNotAnImage() {
			UUID imageId = uploadedAndProcessed("image/jpeg", "not an image".getBytes(StandardCharsets.US_ASCII));

			assertThat(images.findById(imageId).orElseThrow().getRejectReason())
				.isEqualTo(ImageRejectReason.TYPE_MISMATCH);
		}

		@Test
		@DisplayName("DEC-LS-4: a HEIC upload is REJECTED (UNSUPPORTED_TYPE)")
		void rejectsHeic() {
			byte[] heic = "\0\0\0\u0018ftypheic\0\0\0\0mif1heic".getBytes(StandardCharsets.ISO_8859_1);

			UUID imageId = uploadedAndProcessed("image/heic", heic);

			assertThat(images.findById(imageId).orElseThrow().getRejectReason())
				.isEqualTo(ImageRejectReason.UNSUPPORTED_TYPE);
		}

		@Test
		void processingTwiceChangesNothing() {
			UUID imageId = uploadedAndProcessed("image/jpeg", TestImages.jpeg(300, 200));
			Instant processedAt = images.findById(imageId).orElseThrow().getProcessedAt();

			processor.process(imageId);

			assertThat(images.findById(imageId).orElseThrow().getProcessedAt()).isEqualTo(processedAt);
		}

	}

	@Nested
	class OrphanCleanup {

		@Test
		@DisplayName("UC-SY-15: an image not attached 24 h after upload is deleted with its objects")
		void deletesAnOldOrphan() {
			UUID imageId = uploadedAndProcessed("image/jpeg", TestImages.jpeg(300, 200));
			ListingImage image = images.findById(imageId).orElseThrow();

			orphanCleanup.runAt(image.getCreatedAt().plus(Duration.ofHours(24)));

			assertThat(images.findById(imageId)).isEmpty();
			assertThat(storage.contains(image.getOriginalKey())).isFalse();
			assertThat(storage.contains(image.getThumbKey())).isFalse();
			assertThat(storage.contains(image.getLargeKey())).isFalse();
		}

		@Test
		@DisplayName("UC-SY-15: a younger orphan and an attached image are kept")
		void keepsYoungOrphansAndAttachedImages() {
			UUID young = uploadedAndProcessed("image/jpeg", TestImages.jpeg(300, 200));
			UUID attached = uploadedAndProcessed("image/jpeg", TestImages.jpeg(300, 200));
			transaction.executeWithoutResult(
					status -> images.findById(attached).orElseThrow().attachTo(owner, existingListingId(), 9));
			Instant youngCreatedAt = images.findById(young).orElseThrow().getCreatedAt();

			orphanCleanup.runAt(youngCreatedAt.plus(Duration.ofHours(24)).minusMillis(1));
			assertThat(images.findById(young)).isPresent();

			orphanCleanup.runAt(Instant.now().plus(Duration.ofDays(3)));
			assertThat(images.findById(attached)).isPresent();
			assertThat(images.findById(young)).isEmpty();
		}

		@Test
		void theScheduledRunLeavesFreshUploadsAlone() {
			UploadTicket ticket = imageService.requestUpload(owner, "image/jpeg", 1_000);

			orphanCleanup.run();

			assertThat(images.findById(ticket.imageId())).isPresent();
		}

	}

	private UUID uploadedAndProcessed(String declaredType, byte[] content) {
		UploadTicket ticket = imageService.requestUpload(owner,
				declaredType.equals("image/heic") ? "image/jpeg" : declaredType, 1_000);
		storage.simulateUpload(originalKeyOf(ticket.imageId()), declaredType, content);
		processor.process(ticket.imageId());
		return ticket.imageId();
	}

	private ImageStatusView awaitProcessed(UUID imageId) {
		await().atMost(PROCESSING_TIMEOUT)
			.until(() -> imageService.status(owner, imageId).status() != ImageProcessingStatus.UPLOADING);
		return imageService.status(owner, imageId);
	}

	private String originalKeyOf(UUID imageId) {
		return images.findById(imageId).orElseThrow().getOriginalKey();
	}

	/**
	 * A listing row the image can point to (the foreign key needs one).
	 */
	private UUID existingListingId() {
		return listings.save(ListingFixtures.anActiveListing(owner)).getId();
	}

	private static void assertCode(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", code);
	}

}
