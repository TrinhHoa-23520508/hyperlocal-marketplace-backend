package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.FeedArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.FeedCenter;
import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.areaverification.ListingPinRule;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaStatus;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GeoCircle;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * FR-AR-09 (interaction range, listing pins), FR-AR-03 (new contacts on a listing's area)
 * and FR-AR-08 / FR-DC-01 (feed center).
 */
@Service
@Transactional(readOnly = true)
public class AreaSpatialRules implements InteractionRange, ListingPinRule, FeedArea {

	private final UserAreaRepository areas;

	private final AreaParameters parameters;

	AreaSpatialRules(UserAreaRepository areas, AreaParameters parameters) {
		this.areas = areas;
		this.parameters = parameters;
	}

	@Override
	public boolean isWithinRange(UUID buyerId, GeoPoint listingPublicLocation) {
		return areas.existsValidAreaInRange(buyerId, listingPublicLocation, parameters.rangeLevels());
	}

	@Override
	public boolean allowsNewContacts(UUID listingAreaId) {
		return areas.findById(listingAreaId).filter(UserArea::grantsTrading).isPresent();
	}

	@Override
	public boolean isPinInsideArea(UUID sellerId, UUID areaId, GeoPoint pin) {
		return areas.isValidAreaCenterInside(sellerId, areaId, new GeoCircle(pin, parameters.listingPinRadiusMeters()));
	}

	/**
	 * The active area keeps centering the feed while it is EXPIRED, because browsing
	 * never needs a valid area; only trading does (FR-AR-03).
	 */
	@Override
	public Optional<FeedCenter> activeFeedArea(UUID userId) {
		return areas.findFirstByUserIdAndActiveTrueAndStatusNot(userId, AreaStatus.REMOVED)
			.map(area -> new FeedCenter(area.getId(), area.center(),
					parameters.rangeLevels().radiusMetersOf(area.getRangeLevel())));
	}

}
