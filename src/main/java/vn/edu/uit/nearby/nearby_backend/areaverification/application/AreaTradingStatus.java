package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AreaStanding;
import vn.edu.uit.nearby.nearby_backend.identity.TradingAreaStatus;

/**
 * The area condition of the trading gate (FR-ID-20, FR-AR-03).
 */
@Component
class AreaTradingStatus implements TradingAreaStatus {

	private final UserAreaRepository areas;

	AreaTradingStatus(UserAreaRepository areas) {
		this.areas = areas;
	}

	@Override
	@Transactional(readOnly = true)
	public AreaStanding standingOf(UUID userId) {
		List<UserArea> liveAreas = areas.findLiveAreas(userId);
		if (liveAreas.stream().anyMatch(UserArea::grantsTrading)) {
			return AreaStanding.VALID;
		}
		return liveAreas.isEmpty() ? AreaStanding.NONE : AreaStanding.ONLY_EXPIRED;
	}

}
