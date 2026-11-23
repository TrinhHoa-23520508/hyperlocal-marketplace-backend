package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.listing.domain.ListingFixtures.NOW;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;

class ListingImageTests {

	private static final UUID OWNER = UUID.randomUUID();

	private static final ProcessedImage PROCESSED = new ProcessedImage("listing-images/a_320.webp",
			"listing-images/a_1080.webp", "image/jpeg", 2_000_000, 4000, 3000);

	@Test
	void startsUploadingAndUnattached() {
		ListingImage image = anUpload();

		assertThat(image.getProcessingStatus()).isEqualTo(ImageProcessingStatus.UPLOADING);
		assertThat(image.isProcessed()).isFalse();
		assertThat(image.isAttached()).isFalse();
	}

	@Test
	@DisplayName("UC-SY-10: a processed image is READY with its two derivatives")
	void becomesReadyWithItsDerivatives() {
		ListingImage image = anUpload();

		image.markReady(PROCESSED, NOW.plusSeconds(5));

		assertThat(image.isReady()).isTrue();
		assertThat(image.getThumbKey()).isEqualTo("listing-images/a_320.webp");
		assertThat(image.getLargeKey()).isEqualTo("listing-images/a_1080.webp");
		assertThat(image.getMimeType()).isEqualTo("image/jpeg");
		assertThat(image.getWidth()).isEqualTo(4000);
		assertThat(image.getProcessedAt()).isEqualTo(NOW.plusSeconds(5));
	}

	@Test
	@DisplayName("FR-LS-02: a refused upload is REJECTED with its reason")
	void isRejectedWithAReason() {
		ListingImage image = anUpload();

		image.reject(ImageRejectReason.TYPE_MISMATCH, NOW);

		assertThat(image.getProcessingStatus()).isEqualTo(ImageProcessingStatus.REJECTED);
		assertThat(image.getRejectReason()).isEqualTo(ImageRejectReason.TYPE_MISMATCH);
		assertThat(image.isProcessed()).isTrue();
	}

	@Test
	void isProcessedOnlyOnce() {
		ListingImage image = aReadyImage();

		assertThatThrownBy(() -> image.reject(ImageRejectReason.DECODE_ERROR, NOW))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("FR-LS-01: the owner attaches a READY image at a position")
	void attachesToTheOwnersListing() {
		ListingImage image = aReadyImage();
		UUID listingId = UUID.randomUUID();

		image.attachTo(OWNER, listingId, 1);

		assertThat(image.getListingId()).isEqualTo(listingId);
		assertThat(image.getPosition()).isEqualTo((short) 1);
	}

	@Test
	@DisplayName("FR-LS-06: an image of the same listing can move to another position")
	void movesWithinItsListing() {
		ListingImage image = aReadyImage();
		UUID listingId = UUID.randomUUID();
		image.attachTo(OWNER, listingId, 1);

		image.attachTo(OWNER, listingId, 3);

		assertThat(image.getPosition()).isEqualTo((short) 3);
	}

	@Test
	@DisplayName("FR-LS-01: another user's image is a 422 IMAGE_INVALID")
	void refusesAnotherUsersImage() {
		ListingImage image = aReadyImage();

		assertImageInvalid(() -> image.attachTo(UUID.randomUUID(), UUID.randomUUID(), 1));
	}

	@Test
	@DisplayName("FR-LS-01: an image that is not READY is a 422 IMAGE_INVALID")
	void refusesAnImageThatIsNotReady() {
		ListingImage uploading = anUpload();
		ListingImage rejected = anUpload();
		rejected.reject(ImageRejectReason.TOO_LARGE, NOW);

		assertImageInvalid(() -> uploading.attachTo(OWNER, UUID.randomUUID(), 1));
		assertImageInvalid(() -> rejected.attachTo(OWNER, UUID.randomUUID(), 1));
	}

	@Test
	@DisplayName("FR-LS-01: an image attached to another listing is a 422 IMAGE_INVALID")
	void refusesAnImageOfAnotherListing() {
		ListingImage image = aReadyImage();
		image.attachTo(OWNER, UUID.randomUUID(), 1);

		assertImageInvalid(() -> image.attachTo(OWNER, UUID.randomUUID(), 1));
	}

	@Test
	void detachingMakesTheImageAnOrphan() {
		ListingImage image = aReadyImage();
		image.attachTo(OWNER, UUID.randomUUID(), 2);

		image.detach();

		assertThat(image.isAttached()).isFalse();
		assertThat(image.getPosition()).isNull();
	}

	@Test
	@DisplayName("UC-SY-15: an unattached image is an orphan from 24 h after the upload started")
	void becomesAnOrphanAfterTheTtl() {
		ListingImage image = anUpload();
		Duration ttl = Duration.ofHours(24);

		assertThat(image.isOrphanAt(NOW.plus(ttl).minusMillis(1), ttl)).isFalse();
		assertThat(image.isOrphanAt(NOW.plus(ttl), ttl)).isTrue();
	}

	@Test
	void anAttachedImageIsNeverAnOrphan() {
		ListingImage image = aReadyImage();
		image.attachTo(OWNER, UUID.randomUUID(), 1);

		assertThat(image.isOrphanAt(NOW.plus(Duration.ofDays(30)), Duration.ofHours(24))).isFalse();
	}

	private static ListingImage anUpload() {
		return ListingImage.uploading(UUID.randomUUID(), OWNER, "incoming/" + OWNER + "/a.jpg", NOW);
	}

	private static ListingImage aReadyImage() {
		ListingImage image = anUpload();
		image.markReady(PROCESSED, NOW);
		return image;
	}

	private static void assertImageInvalid(Runnable attach) {
		assertThatThrownBy(attach::run).isInstanceOf(UnprocessableRequestException.class)
			.hasFieldOrPropertyWithValue("code", "IMAGE_INVALID");
	}

}
