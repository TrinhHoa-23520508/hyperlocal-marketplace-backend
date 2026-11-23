package vn.edu.uit.nearby.nearby_backend.listing;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The copy of the seller's reputation that ranking and cards use (FR-DC-03, FR-DC-05;
 * register LS-W7). The reputation module calls it whenever it publishes
 * {@code ReputationUpdated}, instead of listing listening to it (DEC-LS-2).
 */
public interface SellerScores {

	/**
	 * Updates every listing of the seller; new listings copy the latest value.
	 * @param score the seller score R, 0-100
	 * @param tier one of NEW, CAUTION, STANDARD, TRUSTED, HIGHLY_TRUSTED (FR-RP-08)
	 * @throws IllegalArgumentException for a score or tier outside those values
	 */
	void update(UUID sellerId, BigDecimal score, String tier);

}
