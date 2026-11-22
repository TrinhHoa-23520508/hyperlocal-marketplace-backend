package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.DISTRICT_1;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.UIT;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.realFixAt;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.AreaExpired;
import vn.edu.uit.nearby.nearby_backend.areaverification.AreaExpiring;
import vn.edu.uit.nearby.nearby_backend.areaverification.AreaRemoved;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Verified;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaLabel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaStatus;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt.AreaVerificationAttempt;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * Managing areas (UC-AR-03, UC-AR-04), the UC-SY-05 and UC-SY-11 jobs, and anonymisation
 * of a deleted account.
 */
@IntegrationTest
@RecordApplicationEvents
class AreaLifecycleIntegrationTests {

	@Autowired
	AreaVerificationService verification;

	@Autowired
	AreaManagementService management;

	@Autowired
	AreaRenewalReminderJob reminderJob;

	@Autowired
	AreaExpiryJob expiryJob;

	@Autowired
	VerificationDataRetentionJob retentionJob;

	@Autowired
	UserAreaRepository areas;

	@Autowired
	AreaVerificationAttemptRepository attempts;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ApplicationEvents events;

	@Autowired
	Clock clock;

	@Autowired
	UserRepository users;

	@Autowired
	LocationConsentService locationConsents;

	@Autowired
	AppParameters appParameters;

	private UUID userId;

	@BeforeEach
	void createEligibleUser() {
		userId = new AreaTestAccounts(users, locationConsents, appParameters).eligibleUser();
	}

	@Test
	@DisplayName("A27: the list shows the user's live areas in slot order")
	void listsLiveAreasInSlotOrder() {
		AreaView first = verify(UIT);
		AreaView second = verifyFarAway();

		assertThat(management.listAreas(userId)).extracting(AreaView::id).containsExactly(first.id(), second.id());
	}

	@Test
	@DisplayName("UC-AR-01 step 9: the user names the area")
	void renamesAnArea() {
		AreaView area = verify(UIT);

		AreaView renamed = management.rename(userId, area.id(), new AreaLabel("Nhà"));

		assertThat(renamed.label()).isEqualTo("Nhà");
	}

	@Test
	@DisplayName("FR-ID-18: another user's area is not found")
	void anotherUsersAreaIsNotFound() {
		AreaView area = verify(UIT);
		UUID stranger = UUID.randomUUID();

		assertThatThrownBy(() -> management.rename(stranger, area.id(), new AreaLabel("X")))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_NOT_FOUND");
	}

	@Test
	@DisplayName("UC-AR-04: choosing another area moves the active flag and sets its range")
	void selectingAnAreaMovesTheActiveFlag() {
		AreaView first = verify(UIT);
		AreaView second = verifyFarAway();

		AreaView selected = management.selectActive(userId, second.id(), RangeLevel.FAR);

		assertThat(selected.isActive()).isTrue();
		assertThat(selected.rangeLevel()).isEqualTo(RangeLevel.FAR);
		assertThat(areas.findById(first.id()).orElseThrow().isActive()).isFalse();
	}

	@Test
	@DisplayName("UC-AR-04: changing the range of the active area keeps it active")
	void changingTheRangeOfTheActiveArea() {
		AreaView area = verify(UIT);

		AreaView selected = management.selectActive(userId, area.id(), RangeLevel.NEAR);

		assertThat(selected.isActive()).isTrue();
		assertThat(selected.rangeLevel()).isEqualTo(RangeLevel.NEAR);
	}

	@Test
	@DisplayName("FR-AR-07: removing the active area makes the other valid area active and publishes AreaRemoved")
	void removingTheActiveAreaActivatesTheOther() {
		AreaView first = verify(UIT);
		AreaView second = verifyFarAway();

		management.remove(userId, first.id());

		assertThat(areas.findById(first.id()).orElseThrow().getStatus()).isEqualTo(AreaStatus.REMOVED);
		assertThat(areas.findById(second.id()).orElseThrow().isActive()).isTrue();
		assertThat(management.listAreas(userId)).extracting(AreaView::id).containsExactly(second.id());
		assertThat(events.stream(AreaRemoved.class)).extracting(AreaRemoved::areaId).containsExactly(first.id());
	}

	@Test
	@DisplayName("FR-AR-02: a removed area frees its slot for a new verification")
	void removalFreesTheSlot() {
		AreaView first = verify(UIT);
		management.remove(userId, first.id());
		forgetPreviousFixes();

		AreaView next = verify(DISTRICT_1);

		assertThat(next.slot()).isEqualTo(1);
		assertThat(next.isActive()).isTrue();
	}

	@Test
	@DisplayName("tab:sm-area: a removed area cannot be removed again")
	void removedAreaCannotBeRemovedAgain() {
		AreaView area = verify(UIT);
		management.remove(userId, area.id());

		assertThatThrownBy(() -> management.remove(userId, area.id())).isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_STATUS_CONFLICT");
	}

	@Test
	@DisplayName("UC-SY-05: three days before expiry the area becomes EXPIRING and is reminded once")
	void reminderJobMarksExpiringOnce() {
		AreaView area = verify(UIT);
		Instant threeDaysBefore = area.expiresAt().minus(Duration.ofDays(3));

		reminderJob.runAt(threeDaysBefore);
		reminderJob.runAt(threeDaysBefore.plus(Duration.ofHours(1)));

		UserArea stored = areas.findById(area.id()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(AreaStatus.EXPIRING);
		assertThat(stored.getReminderSentAt()).isEqualTo(threeDaysBefore);
		assertThat(events.stream(AreaExpiring.class).filter(event -> event.areaId().equals(area.id()))).hasSize(1);
	}

	@Test
	@DisplayName("UC-SY-05: the reminder does not run earlier than three days before expiry")
	void reminderJobWaitsForTheWindow() {
		AreaView area = verify(UIT);

		reminderJob.runAt(area.expiresAt().minus(Duration.ofDays(3)).minusSeconds(1));

		assertThat(areas.findById(area.id()).orElseThrow().getStatus()).isEqualTo(AreaStatus.VERIFIED);
	}

	@Test
	@DisplayName("FR-AR-03: at expiry the area becomes EXPIRED, stops granting trading and is published once")
	void expiryJobExpiresTheArea() {
		AreaView area = verify(UIT);
		reminderJob.runAt(area.expiresAt().minus(Duration.ofDays(2)));

		expiryJob.runAt(area.expiresAt().minusMillis(1));
		expiryJob.runAt(area.expiresAt());
		expiryJob.runAt(area.expiresAt().plus(Duration.ofHours(1)));

		UserArea stored = areas.findById(area.id()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(AreaStatus.EXPIRED);
		assertThat(stored.grantsTrading()).isFalse();
		assertThat(events.stream(AreaExpired.class).filter(event -> event.areaId().equals(area.id()))).hasSize(1);
	}

	@Test
	@DisplayName("FR-AR-03: an area whose reminder never ran still expires through EXPIRING")
	void expiryJobExpiresAnOverdueVerifiedArea() {
		AreaView area = verify(UIT);

		expiryJob.runAt(area.expiresAt().plus(Duration.ofMinutes(30)));

		assertThat(areas.findById(area.id()).orElseThrow().getStatus()).isEqualTo(AreaStatus.EXPIRED);
	}

	@Test
	@DisplayName("UC-AR-02 A1: an EXPIRED area can be renewed back to VERIFIED")
	void expiredAreaCanBeRenewed() {
		AreaView area = verify(UIT);
		expiryJob.runAt(area.expiresAt());
		UserArea expired = areas.findById(area.id()).orElseThrow();
		forgetPreviousFixes();

		Verified renewed = (Verified) verification.renew(userId, area.id(), realFixAt(expired.center()));

		assertThat(renewed.area().status()).isEqualTo(AreaStatus.VERIFIED);
	}

	@Test
	@DisplayName("UC-SY-05: the scheduled runs use the current time and leave a fresh area alone")
	void scheduledRunsLeaveAFreshAreaAlone() {
		AreaView area = verify(UIT);

		reminderJob.run();
		expiryJob.run();
		retentionJob.run();

		assertThat(areas.findById(area.id()).orElseThrow().getStatus()).isEqualTo(AreaStatus.VERIFIED);
		assertThat(attempts.findByUserIdOrderByCreatedAt(userId).getFirst().preciseFix()).isPresent();
	}

	@Test
	@DisplayName("FR-AR-10 / UC-SY-11: fixes older than 90 days are purged, the result and grid cell are kept")
	void retentionJobPurgesOldFixes() {
		verify(UIT);
		AreaVerificationAttempt attempt = attempts.findByUserIdOrderByCreatedAt(userId).getFirst();
		Instant now = clock.instant();
		backdate(attempt.getId(), now.minus(Duration.ofDays(91)));

		retentionJob.runAt(now);
		retentionJob.runAt(now);

		AreaVerificationAttempt purged = attempts.findById(attempt.getId()).orElseThrow();
		assertThat(purged.preciseFix()).isEmpty();
		assertThat(purged.getLocationPurgedAt()).isNotNull();
		assertThat(purged.isAccepted()).isTrue();
		assertThat(purged.getGridCell()).isEqualTo(attempt.getGridCell());
	}

	@Test
	@DisplayName("FR-AR-10: fixes younger than 90 days are kept")
	void retentionJobKeepsRecentFixes() {
		verify(UIT);
		AreaVerificationAttempt attempt = attempts.findByUserIdOrderByCreatedAt(userId).getFirst();
		Instant now = clock.instant();
		backdate(attempt.getId(), now.minus(Duration.ofDays(89)));

		retentionJob.runAt(now);

		assertThat(attempts.findById(attempt.getId()).orElseThrow().preciseFix()).isPresent();
	}

	@Test
	@DisplayName("SRS 4.5: attempts older than 24 months are deleted with their device signals")
	void retentionJobDeletesOldAttempts() {
		verification.verify(new VerifyAreaCommand(userId, AreaSubmissions.mockFixAt(UIT), null, null));
		AreaVerificationAttempt attempt = attempts.findByUserIdOrderByCreatedAt(userId).getFirst();
		Instant now = clock.instant();
		backdate(attempt.getId(), now.minus(Duration.ofDays(25 * 31)));

		retentionJob.runAt(now);

		assertThat(attempts.findById(attempt.getId())).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM device_signals WHERE area_verification_id = ?",
				Integer.class, attempt.getId()))
			.isZero();
	}

	@Test
	@DisplayName("UC-SY-12: anonymising an account deletes its areas and purges its fixes, twice is the same as once")
	void accountDeletionRemovesAreasAndFixes() {
		verify(UIT);
		AreaAccountDeletionListener listener = new AreaAccountDeletionListener(areas, attempts, clock);
		AccountDeleted event = new AccountDeleted(userId, clock.instant());

		transaction.executeWithoutResult(status -> listener.onAccountDeleted(event));
		transaction.executeWithoutResult(status -> listener.onAccountDeleted(event));

		assertThat(areas.findLiveAreas(userId)).isEmpty();
		assertThat(attempts.findByUserIdOrderByCreatedAt(userId)).singleElement()
			.satisfies(attempt -> assertThat(attempt.preciseFix()).isEmpty());
	}

	private AreaView verify(GeoPoint place) {
		return ((Verified) verification.verify(new VerifyAreaCommand(userId, realFixAt(place), null, null))).area();
	}

	private AreaView verifyFarAway() {
		forgetPreviousFixes();
		return verify(DISTRICT_1);
	}

	private void forgetPreviousFixes() {
		transaction.executeWithoutResult(status -> attempts.purgeLocationsOfUser(userId, Instant.now()));
	}

	private void backdate(UUID attemptId, Instant createdAt) {
		jdbc.update("UPDATE area_verifications SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), attemptId);
	}

}
