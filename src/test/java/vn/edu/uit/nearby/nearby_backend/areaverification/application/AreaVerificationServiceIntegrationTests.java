package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.DISTRICT_1;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.DI_AN;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.THU_DUC_MARKET;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.UIT;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.mockFixAt;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.realFixAt;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaRemoved;
import vn.edu.uit.nearby.nearby_backend.areaverification.AreaVerified;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Rejected;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Verified;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaLabel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaStatus;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AreaVerificationAttempt;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AttemptResult;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.RecordedDeviceSignal;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.VerificationKind;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.DeviceSignalCode;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.RejectionReason;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.RateLimitExceededException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
@RecordApplicationEvents
class AreaVerificationServiceIntegrationTests {

	@Autowired
	AreaVerificationService verification;

	@Autowired
	UserAreaRepository areas;

	@Autowired
	AreaVerificationAttemptRepository attempts;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ApplicationEvents events;

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
	@DisplayName("UC-AR-01: the first area is VERIFIED for 30 days, active with MEDIUM range, and published")
	void firstAreaIsVerifiedActiveAndPublished() {
		Verified verified = verify(UIT);

		AreaView area = verified.area();
		assertThat(verified.isRenewal()).isFalse();
		assertThat(area.status()).isEqualTo(AreaStatus.VERIFIED);
		assertThat(area.slot()).isEqualTo(1);
		assertThat(area.label()).isEqualTo("Khu vực 1");
		assertThat(area.isActive()).isTrue();
		assertThat(area.rangeLevel()).isEqualTo(RangeLevel.MEDIUM);
		assertThat(Duration.between(area.verifiedAt(), area.expiresAt())).isEqualTo(Duration.ofDays(30));
		assertThat(events.stream(AreaVerified.class))
			.containsExactly(new AreaVerified(userId, area.id(), area.expiresAt(), false));
	}

	@Test
	@DisplayName("FR-AR-10: an accepted attempt is stored with the area, the precise fix and its grid cell")
	void acceptedAttemptIsStored() {
		Verified verified = verify(UIT);

		AreaVerificationAttempt attempt = onlyAttempt();
		assertThat(attempt.getResult()).isEqualTo(AttemptResult.ACCEPTED);
		assertThat(attempt.getKind()).isEqualTo(VerificationKind.INITIAL);
		assertThat(attempt.getUserAreaId()).isEqualTo(verified.area().id());
		assertThat(attempt.preciseFix()).isPresent();
		assertThat(attempt.getGridCell()).startsWith("300:");
		assertThat(areas.findById(verified.area().id()).orElseThrow().getLastVerificationId())
			.isEqualTo(attempt.getId());
	}

	@Test
	@DisplayName("UC-AR-01 E4: a mock fix is rejected, stored with its reason and signal, and creates no area")
	void mockFixIsRejectedAndStored() {
		Rejected rejected = (Rejected) verification.verify(command(mockFixAt(UIT)));

		assertThat(rejected.reasons()).containsExactly(RejectionReason.MOCK_LOCATION);
		assertThat(areas.findLiveAreas(userId)).isEmpty();
		AreaVerificationAttempt attempt = onlyAttempt();
		assertThat(attempt.getResult()).isEqualTo(AttemptResult.REJECTED);
		assertThat(attempt.getUserAreaId()).isNull();
		assertThat(attempt.rejectionReasons()).containsExactly(RejectionReason.MOCK_LOCATION);
		assertThat(attempt.isMockSuspected()).isTrue();
		List<DeviceSignalCode> storedSignals = transaction.execute(status -> attempts.findById(attempt.getId())
			.orElseThrow()
			.deviceSignals()
			.stream()
			.map(RecordedDeviceSignal::getSignalCode)
			.toList());
		assertThat(storedSignals).containsExactly(DeviceSignalCode.MOCK_FLAG);
		assertThat(events.stream(AreaVerified.class)).isEmpty();
	}

	@Test
	@DisplayName("FR-AR-05: reaching a fix faster than 300 km/h since the last accepted one is rejected")
	void impossibleJumpSinceTheLastAcceptedFixIsRejected() {
		verify(UIT);

		Rejected rejected = (Rejected) verification.verify(command(realFixAt(DISTRICT_1)));

		assertThat(rejected.reasons()).containsExactly(RejectionReason.IMPOSSIBLE_SPEED);
	}

	@Test
	@DisplayName("UC-AR-01: a second area far away takes slot 2 and does not replace the active one")
	void secondAreaTakesTheNextSlot() {
		verify(UIT);
		forgetPreviousFixes();

		Verified second = verify(DISTRICT_1);

		assertThat(second.area().slot()).isEqualTo(2);
		assertThat(second.area().isActive()).isFalse();
		assertThat(areas.findLiveAreas(userId)).hasSize(2);
	}

	@Test
	@DisplayName("UC-AR-01 A4: a fix within 1,500 m of an existing area renews it instead of adding one")
	void fixNearAnExistingAreaRenewsIt() {
		Verified first = verify(UIT);
		forgetPreviousFixes();

		Verified again = verify(northOf(UIT, 800));

		assertThat(again.isRenewal()).isTrue();
		assertThat(again.area().id()).isEqualTo(first.area().id());
		assertThat(areas.findLiveAreas(userId)).hasSize(1);
	}

	@Test
	@DisplayName("FR-AR-02: a third area is refused with the areas to remove or replace, and costs no attempt")
	void thirdAreaIsRefused() {
		twoAreas();

		assertThatThrownBy(() -> verification.verify(command(realFixAt(THU_DUC_MARKET))))
			.isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_LIMIT_REACHED")
			.satisfies(ex -> assertThat(((BusinessException) ex).getProperties().get("areaIds"))
				.asInstanceOf(InstanceOfAssertFactories.LIST)
				.hasSize(2));
		assertThat(attempts.findByUserIdOrderByCreatedAt(userId)).hasSize(2);
	}

	@Test
	@DisplayName("UC-AR-03: replacing an area removes it and puts the new one in its slot")
	void replacingAnAreaReusesItsSlot() {
		List<Verified> both = twoAreas();
		UUID replacedId = both.get(0).area().id();
		forgetPreviousFixes();

		Verified replacement = (Verified) verification
			.verify(new VerifyAreaCommand(userId, realFixAt(THU_DUC_MARKET), new AreaLabel("Công ty"), replacedId));

		assertThat(replacement.area().slot()).isEqualTo(1);
		assertThat(replacement.area().label()).isEqualTo("Công ty");
		assertThat(replacement.area().isActive()).isTrue();
		assertThat(areas.findById(replacedId).orElseThrow().getStatus()).isEqualTo(AreaStatus.REMOVED);
		assertThat(events.stream(AreaRemoved.class)).extracting(AreaRemoved::areaId).containsExactly(replacedId);
		assertThat(attempts.findByUserIdOrderByCreatedAt(userId)).last()
			.extracting(AreaVerificationAttempt::getKind)
			.isEqualTo(VerificationKind.REPLACEMENT);
	}

	@Test
	@DisplayName("UC-AR-03: a rejected replacement keeps the old area")
	void rejectedReplacementKeepsTheOldArea() {
		Verified first = verify(UIT);
		forgetPreviousFixes();

		verification.verify(new VerifyAreaCommand(userId, mockFixAt(DISTRICT_1), null, first.area().id()));

		assertThat(areas.findById(first.area().id()).orElseThrow().getStatus()).isEqualTo(AreaStatus.VERIFIED);
	}

	@Test
	@DisplayName("FR-AR-06: a fourth area change within 30 days is refused with AREA_CHANGES and a retry time")
	void fourthAreaChangeInThirtyDaysIsRefused() {
		UUID current = verify(UIT).area().id();
		for (GeoPoint place : List.of(DISTRICT_1, THU_DUC_MARKET)) {
			forgetPreviousFixes();
			current = ((Verified) verification.verify(new VerifyAreaCommand(userId, realFixAt(place), null, current)))
				.area()
				.id();
		}
		forgetPreviousFixes();
		UUID lastArea = current;

		assertThatThrownBy(() -> verification.verify(new VerifyAreaCommand(userId, realFixAt(DI_AN), null, lastArea)))
			.isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("AREA_RATE_LIMITED");
				assertThat(ex.getProperties()).containsEntry("reason", "AREA_CHANGES");
				assertThat(ex.getRetryAfter()).hasValueSatisfying(
						retryAfter -> assertThat(retryAfter).isBetween(Duration.ofDays(29), Duration.ofDays(30)));
			});
	}

	@Test
	@DisplayName("FR-AR-06 / BR-06: the 6th attempt in a day is refused even with valid data")
	void sixthAttemptOfTheDayIsRefused() {
		for (int attempt = 0; attempt < 5; attempt++) {
			verification.verify(command(mockFixAt(UIT)));
		}

		assertThatThrownBy(() -> verification.verify(command(realFixAt(UIT))))
			.isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("AREA_RATE_LIMITED");
				assertThat(ex.getProperties()).containsEntry("reason", "DAILY_ATTEMPTS");
				assertThat(ex.getRetryAfter()).isPresent();
			});
		assertThat(attempts.findByUserIdOrderByCreatedAt(userId)).hasSize(5);
	}

	@Test
	@DisplayName("UC-AR-01 E1: without PRECISE_LOCATION consent nothing is checked or stored")
	void verificationNeedsLocationConsent() {
		UUID withoutConsent = accounts.userWithoutConsent();

		assertThatThrownBy(() -> verification.verify(new VerifyAreaCommand(withoutConsent, realFixAt(UIT), null, null)))
			.isInstanceOf(OperationNotAllowedException.class)
			.hasFieldOrPropertyWithValue("code", "LOCATION_CONSENT_REQUIRED");
		assertThat(attempts.findByUserIdOrderByCreatedAt(withoutConsent)).isEmpty();
	}

	@Test
	@DisplayName("FR-AR-01 precondition: an account without a verified phone cannot verify an area")
	void verificationNeedsAVerifiedPhone() {
		UUID admin = accounts.admin();

		assertThatThrownBy(() -> verification.verify(new VerifyAreaCommand(admin, realFixAt(UIT), null, null)))
			.isInstanceOf(OperationNotAllowedException.class)
			.hasFieldOrPropertyWithValue("code", "ACCESS_DENIED");
	}

	@Test
	@DisplayName("UC-AR-02: a fix 1,499 m from the center renews the area for 30 more days")
	void renewalWithinTheRadiusRenews() {
		AreaView area = verify(UIT).area();
		GeoPoint center = areas.findById(area.id()).orElseThrow().center();
		forgetPreviousFixes();

		Verified renewed = (Verified) verification.renew(userId, area.id(), realFixAt(northOf(center, 1499)));

		assertThat(renewed.isRenewal()).isTrue();
		assertThat(renewed.area().expiresAt()).isAfter(area.expiresAt());
		assertThat(attempts.findByUserIdOrderByCreatedAt(userId)).last()
			.extracting(AreaVerificationAttempt::getKind)
			.isEqualTo(VerificationKind.RENEWAL);
	}

	@Test
	@DisplayName("UC-AR-02 A2: a fix 1,501 m away is refused with OUT_OF_RENEW_RADIUS and the rounded distance")
	void renewalOutsideTheRadiusIsRefused() {
		AreaView area = verify(UIT).area();
		GeoPoint center = areas.findById(area.id()).orElseThrow().center();
		forgetPreviousFixes();

		Rejected rejected = (Rejected) verification.renew(userId, area.id(), realFixAt(northOf(center, 1501)));

		assertThat(rejected.reasons()).containsExactly(RejectionReason.OUT_OF_RENEW_RADIUS);
		assertThat(rejected.roundedDistanceMeters()).hasValue(1500);
		AreaVerificationAttempt attempt = attempts.findByUserIdOrderByCreatedAt(userId).getLast();
		assertThat(attempt.getDistanceMeters()).isGreaterThan(1500f);
		assertThat(attempt.getUserAreaId()).isEqualTo(area.id());
		assertThat(areas.findById(area.id()).orElseThrow().getExpiresAt()).isEqualTo(area.expiresAt());
	}

	@Test
	@DisplayName("UC-AR-02 E3: renewing another user's area answers AREA_NOT_FOUND")
	void renewingSomeoneElsesAreaIsNotFound() {
		AreaView area = verify(UIT).area();
		UUID otherUser = accounts.eligibleUser();

		assertThatThrownBy(() -> verification.renew(otherUser, area.id(), realFixAt(UIT)))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_NOT_FOUND");
	}

	@Test
	@DisplayName("tab:sm-area: a removed area can be neither renewed nor replaced")
	void removedAreaCanBeNeitherRenewedNorReplaced() {
		AreaView area = verify(UIT).area();
		transaction.executeWithoutResult(status -> areas.findById(area.id()).orElseThrow().remove(Instant.now()));

		assertThatThrownBy(() -> verification.renew(userId, area.id(), realFixAt(DISTRICT_1)))
			.isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_STATUS_CONFLICT");
		assertThatThrownBy(
				() -> verification.verify(new VerifyAreaCommand(userId, realFixAt(DISTRICT_1), null, area.id())))
			.isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_STATUS_CONFLICT");
	}

	@Test
	@DisplayName("NFR-07: two concurrent verifications for the last free slot create one area, never two")
	void concurrentVerificationsNeverExceedTheSlots() throws Exception {
		verify(UIT);
		forgetPreviousFixes();
		CountDownLatch start = new CountDownLatch(1);

		List<CompletableFuture<Boolean>> results = List.of(DISTRICT_1, THU_DUC_MARKET)
			.stream()
			.map(place -> CompletableFuture.supplyAsync(() -> {
				await(start);
				try {
					return verification.verify(command(realFixAt(place))) instanceof Verified;
				}
				catch (RuntimeException ex) {
					return false;
				}
			}))
			.toList();
		start.countDown();

		assertThat(results.stream().map(CompletableFuture::join)).containsExactlyInAnyOrder(true, false);
		assertThat(areas.findLiveAreas(userId)).hasSize(2);
	}

	private Verified verify(GeoPoint place) {
		return (Verified) verification.verify(command(realFixAt(place)));
	}

	private List<Verified> twoAreas() {
		Verified first = verify(UIT);
		forgetPreviousFixes();
		return List.of(first, verify(DISTRICT_1));
	}

	private VerifyAreaCommand command(LocationSubmission submission) {
		return new VerifyAreaCommand(userId, submission, null, null);
	}

	private AreaVerificationAttempt onlyAttempt() {
		List<AreaVerificationAttempt> all = attempts.findByUserIdOrderByCreatedAt(userId);
		assertThat(all).hasSize(1);
		return all.getFirst();
	}

	/**
	 * Lets a test verify places far apart in quick succession, as if the previous fixes
	 * were older than the retention period.
	 */
	private void forgetPreviousFixes() {
		transaction.executeWithoutResult(status -> attempts.purgeLocationsOfUser(userId, Instant.now()));
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
