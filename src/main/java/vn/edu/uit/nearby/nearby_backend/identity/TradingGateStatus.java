package vn.edu.uit.nearby.nearby_backend.identity;

import java.util.List;

/**
 * The trading gate of one user (FR-ID-20): open when no condition fails.
 *
 * @param failures every unmet condition, in the order FR-ID-20 states them
 */
public record TradingGateStatus(List<TradingGateFailure> failures) {

	public TradingGateStatus {
		failures = List.copyOf(failures);
	}

	public boolean isOpen() {
		return failures.isEmpty();
	}

}
