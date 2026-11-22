package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import java.time.Duration;
import java.time.Instant;

import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * Where the phone was and when its GPS fix was taken (SRS glossary "Fix").
 */
public record GpsFix(GeoPoint location, Instant fixTime) {

	private static final double SECONDS_PER_HOUR = 3600;

	private static final double METERS_PER_KILOMETER = 1000;

	private static final double MILLIS_PER_SECOND = 1000;

	/**
	 * Speed needed to travel from {@code earlier} to this fix (FR-AR-05), using the
	 * great-circle distance and the time between the two fix times. Two fixes at the same
	 * instant in different places imply an infinite speed.
	 */
	public double impliedSpeedKmhSince(GpsFix earlier) {
		double distanceMeters = location.distanceMetersTo(earlier.location);
		double elapsedSeconds = Math.abs(Duration.between(earlier.fixTime, fixTime).toMillis()) / MILLIS_PER_SECOND;
		if (elapsedSeconds == 0) {
			return distanceMeters == 0 ? 0 : Double.POSITIVE_INFINITY;
		}
		return (distanceMeters / METERS_PER_KILOMETER) / (elapsedSeconds / SECONDS_PER_HOUR);
	}

}
