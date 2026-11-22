package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.util.ArrayList;
import java.util.List;

import vn.edu.uit.nearby.nearby_backend.identity.AreaStanding;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGateFailure;

/**
 * The four conditions of FR-ID-20 that must all hold before trading: an ACTIVE account, a
 * verified phone, at least one VERIFIED or EXPIRING area, and PRECISE_LOCATION consent.
 */
public record TradingGateConditions(AccountStatus accountStatus, boolean hasVerifiedPhone, AreaStanding areaStanding,
		boolean hasLocationConsent) {

	/**
	 * @return every unmet condition, in the order FR-ID-20 states them; an expired-only
	 * user gets {@code AREA_EXPIRED} (renew) instead of {@code NO_VALID_AREA} (verify)
	 */
	public List<TradingGateFailure> unmet() {
		List<TradingGateFailure> failures = new ArrayList<>();
		if (accountStatus != AccountStatus.ACTIVE) {
			failures.add(TradingGateFailure.ACCOUNT_RESTRICTED);
		}
		if (!hasVerifiedPhone) {
			failures.add(TradingGateFailure.PHONE_NOT_VERIFIED);
		}
		switch (areaStanding) {
			case VALID -> {
			}
			case ONLY_EXPIRED -> failures.add(TradingGateFailure.AREA_EXPIRED);
			case NONE -> failures.add(TradingGateFailure.NO_VALID_AREA);
		}
		if (!hasLocationConsent) {
			failures.add(TradingGateFailure.CONSENT_MISSING);
		}
		return List.copyOf(failures);
	}

}
