package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea.NewArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.AntiSpoofingThresholds;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.VerificationContext;
import vn.edu.uit.nearby.nearby_backend.shared.constant.GeoConstants;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridSnapper;

/**
 * Test data for the area module, with the parameter values of SRS table
 * {@code tab:params}.
 */
public final class AreaFixtures {

	public static final GeoPoint UIT = new GeoPoint(10.8700, 106.8031);

	public static final Instant NOW = Instant.parse("2026-11-22T02:00:00Z");

	public static final Duration VALIDITY = Duration.ofDays(30);

	public static final Duration REMINDER_WINDOW = Duration.ofDays(3);

	public static final RenewalRadius RENEWAL_RADIUS = new RenewalRadius(1500);

	public static final AntiSpoofingThresholds SRS_THRESHOLDS = new AntiSpoofingThresholds(100, Duration.ofSeconds(120),
			300);

	public static final GridSnapper GRID = new GridSnapper(300);

	private static final double METERS_PER_DEGREE_OF_LATITUDE = GeoConstants.MEAN_EARTH_RADIUS_METERS * Math.PI / 180;

	private AreaFixtures() {
	}

	/**
	 * A point exactly {@code meters} north along the meridian, as measured by
	 * {@link GeoPoint#distanceMetersTo}.
	 */
	public static GeoPoint northOf(GeoPoint origin, double meters) {
		return new GeoPoint(origin.latitude() + meters / METERS_PER_DEGREE_OF_LATITUDE, origin.longitude());
	}

	public static UserArea aVerifiedArea() {
		return aVerifiedAreaAt(UIT);
	}

	public static UserArea aVerifiedAreaAt(GeoPoint location) {
		return UserArea.verified(
				new NewArea(UUID.randomUUID(), 1, new AreaLabel("Nhà"), GRID.snap(location), UUID.randomUUID()), NOW,
				VALIDITY);
	}

	public static ContextBuilder aContext() {
		return new ContextBuilder();
	}

	public static final class ContextBuilder {

		private GeoPoint location = UIT;

		private double accuracyMeters = 20;

		private Instant fixTime = NOW.minusSeconds(5);

		private boolean isMockLocation;

		private DeviceSignalReport device = DeviceSignalReport.UNKNOWN;

		private GpsFix previousAcceptedFix;

		public ContextBuilder at(GeoPoint newLocation) {
			location = newLocation;
			return this;
		}

		public ContextBuilder withAccuracyMeters(double meters) {
			accuracyMeters = meters;
			return this;
		}

		public ContextBuilder withFixAge(Duration age) {
			fixTime = NOW.minus(age);
			return this;
		}

		public ContextBuilder withFixAhead(Duration ahead) {
			fixTime = NOW.plus(ahead);
			return this;
		}

		public ContextBuilder withMockLocation() {
			isMockLocation = true;
			return this;
		}

		public ContextBuilder withDevice(DeviceSignalReport report) {
			device = report;
			return this;
		}

		public ContextBuilder withPreviousFix(GeoPoint previousLocation, Duration before) {
			previousAcceptedFix = new GpsFix(previousLocation, fixTime.minus(before));
			return this;
		}

		public VerificationContext build() {
			GpsReading reading = new GpsReading(new GpsFix(location, fixTime), accuracyMeters, isMockLocation);
			return new VerificationContext(reading, device, previousAcceptedFix, NOW, SRS_THRESHOLDS);
		}

	}

}
