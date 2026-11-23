package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

public record PointResponse(double latitude, double longitude) {

	public static PointResponse from(GeoPoint point) {
		return new PointResponse(point.latitude(), point.longitude());
	}

}
