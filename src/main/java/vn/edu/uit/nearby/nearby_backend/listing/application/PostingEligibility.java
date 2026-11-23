package vn.edu.uit.nearby.nearby_backend.listing.application;

import vn.edu.uit.nearby.nearby_backend.identity.TradingGateStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.PostingAllowance;

/**
 * UC-LS-01 step 1: whether the seller may post now and why not.
 */
public record PostingEligibility(TradingGateStatus tradingGate, PostingAllowance allowance) {

	public boolean canPost() {
		return tradingGate.isOpen() && allowance.canPost();
	}

}
