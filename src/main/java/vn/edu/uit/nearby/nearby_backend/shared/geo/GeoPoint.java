package vn.edu.uit.nearby.nearby_backend.shared.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import vn.edu.uit.nearby.nearby_backend.shared.constant.GeoConstants;

public record GeoPoint(double latitude, double longitude) {

	private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), GeoConstants.WGS84_SRID);

	public GeoPoint {
		if (latitude < -90 || latitude > 90) {
			throw new IllegalArgumentException("Latitude out of range: " + latitude);
		}
		if (longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("Longitude out of range: " + longitude);
		}
	}

	public static GeoPoint from(Point point) {
		return new GeoPoint(point.getY(), point.getX());
	}

	/**
	 * Great-circle (haversine) distance on a spherical Earth; within about 0.5 % of the
	 * geodesic distance PostGIS computes on the ellipsoid.
	 */
	public double distanceMetersTo(GeoPoint other) {
		double deltaLatitude = Math.toRadians(other.latitude - latitude);
		double deltaLongitude = Math.toRadians(other.longitude - longitude);
		double haversine = Math.pow(Math.sin(deltaLatitude / 2), 2) + Math.cos(Math.toRadians(latitude))
				* Math.cos(Math.toRadians(other.latitude)) * Math.pow(Math.sin(deltaLongitude / 2), 2);
		return 2 * GeoConstants.MEAN_EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(haversine)));
	}

	public Point toPoint() {
		// JTS orders coordinates as (x = longitude, y = latitude).
		return WGS84.createPoint(new Coordinate(longitude, latitude));
	}

}
