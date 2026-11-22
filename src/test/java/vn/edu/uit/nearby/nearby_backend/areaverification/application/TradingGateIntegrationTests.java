package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.UIT;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.realFixAt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Verified;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGateFailure;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * FR-ID-20 end to end: identity's gate reading the area module through the
 * {@code TradingAreaStatus} SPI. Acceptance: turning any one condition off closes the
 * gate, turning it back on reopens it.
 */
@IntegrationTest
class TradingGateIntegrationTests {

	@Autowired
	TradingGate tradingGate;

	@Autowired
	AreaVerificationService verification;

	@Autowired
	AreaManagementService management;

	@Autowired
	UserAreaRepository areas;

	@Autowired
	AreaVerificationAttemptRepository attempts;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	UserRepository users;

	@Autowired
	LocationConsentService locationConsents;

	@Autowired
	AppParameters appParameters;

	private AreaTestAccounts accounts;

	private UUID userId;

	@BeforeEach
	void createEligibleUser() {
		accounts = new AreaTestAccounts(users, locationConsents, appParameters);
		userId = accounts.eligibleUser();
	}

	@Test
	@DisplayName("FR-ID-20: without an area the gate is closed with NO_VALID_AREA")
	void closedWithoutAnArea() {
		assertThat(tradingGate.evaluate(userId).failures()).containsExactly(TradingGateFailure.NO_VALID_AREA);
	}

	@Test
	@DisplayName("UC-AR-01 postcondition: verifying an area opens the gate at once")
	void verifyingAnAreaOpensTheGate() {
		verifyAnArea();

		assertThat(tradingGate.evaluate(userId).isOpen()).isTrue();
		assertThatCode(() -> tradingGate.requireOpen(userId)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-AR-03: an EXPIRING area keeps the gate open")
	void expiringAreaKeepsTheGateOpen() {
		UUID areaId = verifyAnArea();
		change(areaId, area -> area.markExpiring(area.getExpiresAt().minus(Duration.ofDays(2))));

		assertThat(tradingGate.evaluate(userId).isOpen()).isTrue();
	}

	@Test
	@DisplayName("FR-AR-03: when every area is EXPIRED the gate closes with AREA_EXPIRED, and renewal reopens it")
	void expiredAreaClosesTheGateUntilRenewed() {
		UUID areaId = verifyAnArea();
		change(areaId, area -> area.expire(area.getExpiresAt()));

		assertThat(tradingGate.evaluate(userId).failures()).containsExactly(TradingGateFailure.AREA_EXPIRED);

		forgetPreviousFixes();
		verification.renew(userId, areaId, realFixAt(areas.findById(areaId).orElseThrow().center()));

		assertThat(tradingGate.evaluate(userId).isOpen()).isTrue();
	}

	@Test
	@DisplayName("FR-AR-07: removing the only area closes the gate with NO_VALID_AREA")
	void removingTheOnlyAreaClosesTheGate() {
		UUID areaId = verifyAnArea();

		management.remove(userId, areaId);

		assertThat(tradingGate.evaluate(userId).failures()).containsExactly(TradingGateFailure.NO_VALID_AREA);
	}

	@Test
	@DisplayName("FR-ID-15: withdrawing PRECISE_LOCATION closes the gate, consenting again reopens it")
	void withdrawingConsentClosesTheGate() {
		verifyAnArea();

		locationConsents.withdrawConsent(userId);

		assertThat(tradingGate.evaluate(userId).failures()).containsExactly(TradingGateFailure.CONSENT_MISSING);

		locationConsents.grantConsent(userId, appParameters.get("consent.location_terms_version"));

		assertThat(tradingGate.evaluate(userId).isOpen()).isTrue();
	}

	@Test
	@DisplayName("FR-ID-19: a suspended account is ACCOUNT_RESTRICTED even with a valid area")
	void suspendedAccountIsRestricted() {
		verifyAnArea();
		transaction.executeWithoutResult(status -> {
			User user = users.getRequired(userId);
			user.suspend(Instant.now().plus(Duration.ofDays(1)), Instant.now());
		});

		assertThat(tradingGate.evaluate(userId).failures()).containsExactly(TradingGateFailure.ACCOUNT_RESTRICTED);
	}

	@Test
	@DisplayName("FR-ID-20: an account without a phone reports every unmet condition")
	void accountWithoutPhoneReportsEveryUnmetCondition() {
		UUID admin = accounts.admin();

		assertThat(tradingGate.evaluate(admin).failures()).containsExactly(TradingGateFailure.PHONE_NOT_VERIFIED,
				TradingGateFailure.NO_VALID_AREA, TradingGateFailure.CONSENT_MISSING);
	}

	@Test
	@DisplayName("UC-LS-01 E1 / UC-TX-01 E1: a closed gate answers 403 TRADING_GATE_FAILED with its reasons")
	void closedGateIsRefusedWithReasons() {
		assertThatThrownBy(() -> tradingGate.requireOpen(userId)).isInstanceOf(OperationNotAllowedException.class)
			.hasFieldOrPropertyWithValue("code", "TRADING_GATE_FAILED")
			.satisfies(ex -> assertThat(((BusinessException) ex).getProperties()).containsEntry("reasons",
					List.of("NO_VALID_AREA")));
	}

	@Test
	@DisplayName("FR-ID-20: an unknown user is USER_NOT_FOUND")
	void unknownUserIsNotFound() {
		assertThatThrownBy(() -> tradingGate.evaluate(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class)
			.hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
	}

	private UUID verifyAnArea() {
		return ((Verified) verification.verify(new VerifyAreaCommand(userId, realFixAt(UIT), null, null))).area().id();
	}

	private void change(UUID areaId, Consumer<UserArea> change) {
		transaction.executeWithoutResult(status -> change.accept(areas.findById(areaId).orElseThrow()));
	}

	private void forgetPreviousFixes() {
		transaction.executeWithoutResult(status -> attempts.purgeLocationsOfUser(userId, Instant.now()));
	}

}
