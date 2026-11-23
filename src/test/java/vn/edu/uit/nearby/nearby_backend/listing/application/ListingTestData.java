package vn.edu.uit.nearby.nearby_backend.listing.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaTestAccounts;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaVerificationService;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.LocationSubmission;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Verified;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerifyAreaCommand;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsFix;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsReading;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingImage;
import vn.edu.uit.nearby.nearby_backend.shared.image.ProcessedImage;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridSnapper;

/**
 * Sellers whose trading gate is open (phone verified, consent given, one verified area at
 * UIT) and processed images, for listing service tests.
 */
public class ListingTestData {

	public static final GeoPoint UIT = new GeoPoint(10.8700, 106.8031);

	private static final GridSnapper AREA_GRID = new GridSnapper(300);

	private final AreaTestAccounts accounts;

	private final AreaVerificationService areaVerification;

	private final ListingImageRepository images;

	private final TransactionTemplate transaction;

	public ListingTestData(AreaTestAccounts accounts, AreaVerificationService areaVerification,
			ListingImageRepository images, TransactionTemplate transaction) {
		this.accounts = accounts;
		this.areaVerification = areaVerification;
		this.images = images;
		this.transaction = transaction;
	}

	public Seller aSeller() {
		return aSellerAt(UIT);
	}

	public Seller aSellerAt(GeoPoint place) {
		UUID sellerId = accounts.eligibleUser();
		Verified verified = (Verified) areaVerification
			.verify(new VerifyAreaCommand(sellerId, realFixAt(place), null, null));
		return new Seller(sellerId, verified.area().id(), AREA_GRID.snap(place).center());
	}

	/**
	 * A user who can browse but never verified an area.
	 */
	public UUID aBuyerWithoutArea() {
		return accounts.eligibleUser();
	}

	public List<UUID> readyImages(UUID ownerId, int count) {
		return IntStream.range(0, count).mapToObj(index -> readyImage(ownerId)).toList();
	}

	public UUID readyImage(UUID ownerId) {
		UUID imageId = UUID.randomUUID();
		String base = "listing-images/" + imageId;
		return transaction.execute(status -> {
			ListingImage image = ListingImage.uploading(imageId, ownerId, "incoming/" + ownerId + "/" + imageId,
					Instant.now());
			image.markReady(new ProcessedImage(base + "_320.webp", base + "_1080.webp", "image/jpeg", 1_000, 800, 600),
					Instant.now());
			return images.save(image).getId();
		});
	}

	public static ListingForm form(GeoPoint pin, List<UUID> imageIds, long priceVnd) {
		return new ListingForm("Áo khoác da nam", "Còn mới 90%", ItemCondition.LIKE_NEW, priceVnd, true,
				"Cổng KTX khu A", (short) 1, pin, imageIds);
	}

	private static LocationSubmission realFixAt(GeoPoint place) {
		return new LocationSubmission(new GpsReading(new GpsFix(place, Instant.now().minusSeconds(5)), 15, false),
				DeviceSignalReport.UNKNOWN, UUID.randomUUID(), "1.0.0");
	}

	/**
	 * @param areaCenter the center of the seller's verified area, snapped to
	 * {@code area.grid_cell_m} = 300
	 */
	public record Seller(UUID id, UUID areaId, GeoPoint areaCenter) {
	}

}
