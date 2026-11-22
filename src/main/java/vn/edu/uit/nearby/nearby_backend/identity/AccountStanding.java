package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

/**
 * Whether an account may take part in location features: ACTIVE with a verified phone
 * number, the precondition of area verification (FR-AR-01) and part of the trading gate
 * (FR-ID-20).
 */
public interface AccountStanding {

	boolean isActiveWithVerifiedPhone(UUID userId);

	/**
	 * @throws OperationNotAllowedException {@code ACCESS_DENIED} when
	 * {@link #isActiveWithVerifiedPhone(UUID)} is false
	 */
	void requireActiveWithVerifiedPhone(UUID userId);

}
