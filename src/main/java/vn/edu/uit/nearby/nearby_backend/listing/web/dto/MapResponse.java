package vn.edu.uit.nearby.nearby_backend.listing.web.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.listing.application.DiscoveryService.MapResult;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;

@Schema(description = "A09 markers (FR-DC-07): points when at most 200 listings are in view, else clusters only")
public record MapResponse(List<Point> points, List<Cluster> clusters) {

	public static MapResponse from(MapResult result) {
		return new MapResponse(
				result.points()
					.stream()
					.map(point -> new Point(point.listingId(), point.priceVnd(), point.status(),
							PointResponse.from(point.publicPoint())))
					.toList(),
				result.clusters()
					.stream()
					.map(cluster -> new Cluster(cluster.listings(), PointResponse.from(cluster.center())))
					.toList());
	}

	public record Point(UUID listingId, long priceVnd, ListingStatus status,
			@Schema(description = "Blurred public point") PointResponse location) {
	}

	public record Cluster(@Schema(description = "Listings in the cluster") long listings, PointResponse center) {
	}

}
