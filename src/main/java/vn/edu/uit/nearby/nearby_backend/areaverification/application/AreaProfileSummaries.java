package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.identity.ProfileArea;
import vn.edu.uit.nearby.nearby_backend.identity.ProfileAreas;

/**
 * The area part of the own profile (FR-ID-12) and of the public profile (FR-ID-13).
 */
@Component
class AreaProfileSummaries implements ProfileAreas {

	private static final Comparator<UserArea> ACTIVE_FIRST = Comparator.comparing(UserArea::isActive).reversed();

	private final UserAreaRepository areas;

	AreaProfileSummaries(UserAreaRepository areas) {
		this.areas = areas;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProfileArea> areasOf(UUID userId) {
		return areas.findLiveAreas(userId).stream().map(AreaProfileSummaries::toProfileArea).toList();
	}

	/**
	 * Taken only from an area that still grants trading, the active one first, so an
	 * expired area never advertises where the user trades.
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<String> publicLocalityOf(UUID userId) {
		return areas.findLiveAreas(userId)
			.stream()
			.filter(UserArea::grantsTrading)
			.sorted(ACTIVE_FIRST)
			.map(UserArea::getLocalityName)
			.filter(Objects::nonNull)
			.findFirst();
	}

	private static ProfileArea toProfileArea(UserArea area) {
		return new ProfileArea(area.getId(), area.getLabel(), area.getLocalityName(), area.getStatus().name(),
				area.isActive(), area.getExpiresAt());
	}

}
