package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaRemovalBlocker;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

/**
 * FR-AR-07: an area whose listings still have an OFFERED or ACCEPTED transaction cannot
 * be removed or replaced. With no {@link AreaRemovalBlocker} registered yet nothing
 * blocks.
 */
@Component
class AreaRemovalGuard {

	private final List<AreaRemovalBlocker> blockers;

	AreaRemovalGuard(List<AreaRemovalBlocker> blockers) {
		this.blockers = List.copyOf(blockers);
	}

	/**
	 * @throws ConflictException {@code AREA_HAS_OPEN_TRANSACTIONS} with the
	 * {@code listingIds} to resolve first
	 */
	void requireRemovable(UserArea area) {
		List<UUID> listingIds = blockers.stream()
			.flatMap(blocker -> blocker.listingsWithOpenTransactions(area.getUserId(), area.getId()).stream())
			.distinct()
			.toList();
		if (!listingIds.isEmpty()) {
			throw new ConflictException(ErrorCodes.AREA_HAS_OPEN_TRANSACTIONS,
					"Finish or cancel the open transactions on this area's listings first.",
					Map.of("listingIds", listingIds));
		}
	}

}
