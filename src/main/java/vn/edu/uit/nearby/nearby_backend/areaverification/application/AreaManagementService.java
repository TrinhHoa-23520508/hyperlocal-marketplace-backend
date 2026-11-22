package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaRemoved;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaLabel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;

/**
 * The user's area list on A27: naming an area, choosing the active area and its range
 * (UC-AR-04, FR-AR-08) and removing an area (UC-AR-03, FR-AR-07). Another user's area is
 * always {@code 404 AREA_NOT_FOUND} (FR-ID-18).
 */
@Service
public class AreaManagementService {

	private final UserAreaRepository areas;

	private final AreaRemovalGuard removalGuard;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	AreaManagementService(UserAreaRepository areas, AreaRemovalGuard removalGuard, ApplicationEventPublisher events,
			Clock clock) {
		this.areas = areas;
		this.removalGuard = removalGuard;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<AreaView> listAreas(UUID userId) {
		return areas.findLiveAreas(userId).stream().map(AreaView::from).toList();
	}

	@Transactional
	public AreaView rename(UUID userId, UUID areaId, AreaLabel label) {
		UserArea area = areas.getOwned(areaId, userId);
		area.rename(label, clock.instant());
		return AreaView.from(area);
	}

	/**
	 * Makes a VERIFIED or EXPIRING area the one that drives the feed, with the chosen
	 * range; the previously active area stops being active.
	 */
	@Transactional
	public AreaView selectActive(UUID userId, UUID areaId, RangeLevel range) {
		UserArea selected = areas.getOwned(areaId, userId);
		Instant now = clock.instant();
		areas.findLiveAreas(userId)
			.stream()
			.filter(area -> area.isActive() && !area.getId().equals(areaId))
			.forEach(previous -> {
				previous.deactivate(now);
				areas.saveAndFlush(previous);
			});
		selected.activate(range, now);
		return AreaView.from(selected);
	}

	/**
	 * Removes the area unless its listings have open transactions. When the active area
	 * is removed, the user's remaining valid area becomes the active one so the feed
	 * keeps a center.
	 */
	@Transactional
	public void remove(UUID userId, UUID areaId) {
		UserArea area = areas.getOwned(areaId, userId);
		removalGuard.requireRemovable(area);
		boolean wasActive = area.isActive();
		Instant now = clock.instant();
		area.remove(now);
		areas.saveAndFlush(area);
		if (wasActive) {
			areas.findLiveAreas(userId)
				.stream()
				.filter(UserArea::grantsTrading)
				.findFirst()
				.ifPresent(next -> next.activate(next.getRangeLevel(), now));
		}
		events.publishEvent(new AreaRemoved(userId, areaId, now));
	}

}
