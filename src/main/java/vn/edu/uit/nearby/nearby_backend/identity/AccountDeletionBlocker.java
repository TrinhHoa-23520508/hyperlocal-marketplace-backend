package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.List;
import java.util.UUID;

/**
 * Implemented by modules that hold obligations an account deletion must wait for, such as
 * open transactions, payments held in escrow or pending payouts (FR-ID-16).
 */
public interface AccountDeletionBlocker {

	/**
	 * @return a short code for each obligation still open, empty when deletion can go on
	 */
	List<String> pendingObligations(UUID userId);

}
