package vn.edu.uit.nearby.nearby_backend.identity.application.gate;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.AreaStanding;
import vn.edu.uit.nearby.nearby_backend.identity.LocationConsents;
import vn.edu.uit.nearby.nearby_backend.identity.TradingAreaStatus;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGateStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.TradingGateConditions;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;

/**
 * Evaluates FR-ID-20 on every call instead of reading a Redis cache (decision DEC-AR-2):
 * the inputs are three indexed reads, and a fresh read can never be stale after an area
 * or consent change.
 */
@Service
public class TradingGateService implements TradingGate {

	private final UserRepository users;

	private final LocationConsents locationConsents;

	private final List<TradingAreaStatus> areaStatuses;

	public TradingGateService(UserRepository users, LocationConsents locationConsents,
			List<TradingAreaStatus> areaStatuses) {
		this.users = users;
		this.locationConsents = locationConsents;
		this.areaStatuses = List.copyOf(areaStatuses);
	}

	@Override
	@Transactional(readOnly = true)
	public TradingGateStatus evaluate(UUID userId) {
		User user = users.getRequired(userId);
		TradingGateConditions conditions = new TradingGateConditions(user.getStatus(), user.hasVerifiedPhone(),
				areaStandingOf(userId), locationConsents.hasConsented(userId));
		return new TradingGateStatus(conditions.unmet());
	}

	private AreaStanding areaStandingOf(UUID userId) {
		return areaStatuses.stream().map(status -> status.standingOf(userId)).findFirst().orElse(AreaStanding.NONE);
	}

}
