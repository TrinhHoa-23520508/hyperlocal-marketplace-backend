package vn.edu.uit.nearby.nearby_backend.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import vn.edu.uit.nearby.nearby_backend.identity.AreaStanding;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGateFailure;

class TradingGateConditionsTests {

	@Test
	@DisplayName("FR-ID-20: all four conditions met opens the gate")
	void allConditionsMetOpensTheGate() {
		assertThat(conditions(AccountStatus.ACTIVE, true, AreaStanding.VALID, true).unmet()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("FR-ID-20: any account status other than ACTIVE is ACCOUNT_RESTRICTED")
	void restrictedAccountFails(AccountStatus status) {
		assertThat(conditions(status, true, AreaStanding.VALID, true).unmet())
			.containsExactly(TradingGateFailure.ACCOUNT_RESTRICTED);
	}

	@Test
	@DisplayName("FR-ID-20: an unverified phone is PHONE_NOT_VERIFIED")
	void unverifiedPhoneFails() {
		assertThat(conditions(AccountStatus.ACTIVE, false, AreaStanding.VALID, true).unmet())
			.containsExactly(TradingGateFailure.PHONE_NOT_VERIFIED);
	}

	@Test
	@DisplayName("FR-ID-20: no area at all is NO_VALID_AREA, so the client offers to verify one")
	void noAreaFails() {
		assertThat(conditions(AccountStatus.ACTIVE, true, AreaStanding.NONE, true).unmet())
			.containsExactly(TradingGateFailure.NO_VALID_AREA);
	}

	@Test
	@DisplayName("FR-ID-20: only expired areas is AREA_EXPIRED, so the client offers to renew")
	void onlyExpiredAreasFail() {
		assertThat(conditions(AccountStatus.ACTIVE, true, AreaStanding.ONLY_EXPIRED, true).unmet())
			.containsExactly(TradingGateFailure.AREA_EXPIRED);
	}

	@Test
	@DisplayName("FR-ID-20: missing PRECISE_LOCATION consent is CONSENT_MISSING")
	void missingConsentFails() {
		assertThat(conditions(AccountStatus.ACTIVE, true, AreaStanding.VALID, false).unmet())
			.containsExactly(TradingGateFailure.CONSENT_MISSING);
	}

	@Test
	@DisplayName("FR-ID-20: every unmet condition is reported, in the order the SRS states them")
	void everyUnmetConditionIsReportedInOrder() {
		assertThat(conditions(AccountStatus.SUSPENDED, false, AreaStanding.NONE, false).unmet()).containsExactly(
				TradingGateFailure.ACCOUNT_RESTRICTED, TradingGateFailure.PHONE_NOT_VERIFIED,
				TradingGateFailure.NO_VALID_AREA, TradingGateFailure.CONSENT_MISSING);
	}

	private static TradingGateConditions conditions(AccountStatus status, boolean hasVerifiedPhone,
			AreaStanding areaStanding, boolean hasLocationConsent) {
		return new TradingGateConditions(status, hasVerifiedPhone, areaStanding, hasLocationConsent);
	}

}
