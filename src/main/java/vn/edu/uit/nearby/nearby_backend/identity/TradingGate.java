package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.Map;
import java.util.UUID;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;

/**
 * The trading gate (FR-ID-20, BR-08) that Listing, Chat and Transaction check before
 * posting a listing (UC-LS-01), opening a chat (UC-CH-01) or making an offer (UC-TX-01).
 */
public interface TradingGate {

	/**
	 * @throws vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException
	 * {@code USER_NOT_FOUND} for an unknown user
	 */
	TradingGateStatus evaluate(UUID userId);

	/**
	 * @throws OperationNotAllowedException {@code TRADING_GATE_FAILED} with the unmet
	 * conditions in the {@code reasons} property
	 */
	default void requireOpen(UUID userId) {
		TradingGateStatus status = evaluate(userId);
		if (!status.isOpen()) {
			throw new OperationNotAllowedException(ErrorCodes.TRADING_GATE_FAILED,
					"Complete the missing steps before trading.",
					Map.of("reasons", status.failures().stream().map(Enum::name).toList()));
		}
	}

}
