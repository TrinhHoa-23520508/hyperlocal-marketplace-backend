package vn.edu.uit.nearby.nearby_backend.areaverification;

import java.util.List;
import java.util.UUID;

/**
 * Implemented by the module that owns transactions: an area cannot be removed or replaced
 * while a listing of that area has an OFFERED or ACCEPTED transaction (FR-AR-07).
 */
public interface AreaRemovalBlocker {

	/**
	 * @return ids of the area's listings that still have an open transaction, empty when
	 * the area can be removed
	 */
	List<UUID> listingsWithOpenTransactions(UUID userId, UUID areaId);

}
