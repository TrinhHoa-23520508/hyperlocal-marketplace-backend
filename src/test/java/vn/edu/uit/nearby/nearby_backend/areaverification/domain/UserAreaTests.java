package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.GRID;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.NOW;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.REMINDER_WINDOW;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.RENEWAL_RADIUS;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.UIT;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.VALIDITY;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.aVerifiedArea;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea.NewArea;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridCell;

class UserAreaTests {

	@Test
	void newAreaIsVerifiedForThirtyDaysWithMediumRangeAndNotYetActive() {
		UUID userId = UUID.randomUUID();
		UUID verificationId = UUID.randomUUID();
		GridCell cell = GRID.snap(UIT);

		UserArea area = UserArea.verified(new NewArea(userId, 2, new AreaLabel("Công ty"), cell, verificationId), NOW,
				VALIDITY);

		assertThat(area.getStatus()).isEqualTo(AreaStatus.VERIFIED);
		assertThat(area.getUserId()).isEqualTo(userId);
		assertThat(area.getSlot()).isEqualTo((short) 2);
		assertThat(area.getLabel()).isEqualTo("Công ty");
		assertThat(area.getVerifiedAt()).isEqualTo(NOW);
		assertThat(area.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
		assertThat(area.getRangeLevel()).isEqualTo(RangeLevel.MEDIUM);
		assertThat(area.isActive()).isFalse();
		assertThat(area.getLastVerificationId()).isEqualTo(verificationId);
		assertThat(area.grantsTrading()).isTrue();
	}

	@Test
	void centerIsTheGridCellCenterNotTheRawFix() {
		GridCell cell = GRID.snap(UIT);

		UserArea area = AreaFixtures.aVerifiedAreaAt(UIT);

		assertThat(area.center()).isEqualTo(cell.center()).isNotEqualTo(UIT);
		assertThat(area.getGridCell()).isEqualTo(cell.code());
	}

	@Test
	void renewalRestartsTheValidityFromNow() {
		UserArea area = aVerifiedArea();
		Instant later = NOW.plus(Duration.ofDays(28));
		area.markExpiring(later);
		UUID renewalId = UUID.randomUUID();

		area.renew(renewalId, later, VALIDITY);

		assertThat(area.getStatus()).isEqualTo(AreaStatus.VERIFIED);
		assertThat(area.getExpiresAt()).isEqualTo(later.plus(VALIDITY));
		assertThat(area.getReminderSentAt()).isNull();
		assertThat(area.getLastVerificationId()).isEqualTo(renewalId);
	}

	@Test
	void anExpiredAreaCanBeRenewed() {
		UserArea area = expiredArea();

		area.renew(UUID.randomUUID(), NOW.plus(Duration.ofDays(40)), VALIDITY);

		assertThat(area.getStatus()).isEqualTo(AreaStatus.VERIFIED);
		assertThat(area.grantsTrading()).isTrue();
	}

	@Test
	void reminderIsDueWhenAtMostThreeDaysAreLeft() {
		UserArea area = aVerifiedArea();
		Instant exactlyThreeDaysBefore = area.getExpiresAt().minus(REMINDER_WINDOW);

		assertThat(area.isDueForRenewalReminder(exactlyThreeDaysBefore, REMINDER_WINDOW)).isTrue();
		assertThat(area.isDueForRenewalReminder(exactlyThreeDaysBefore.minusSeconds(1), REMINDER_WINDOW)).isFalse();
	}

	@Test
	void reminderIsNotDueTwiceInOneCycle() {
		UserArea area = aVerifiedArea();
		Instant reminderTime = area.getExpiresAt().minus(Duration.ofDays(2));

		area.markExpiring(reminderTime);

		assertThat(area.getStatus()).isEqualTo(AreaStatus.EXPIRING);
		assertThat(area.getReminderSentAt()).isEqualTo(reminderTime);
		assertThat(area.isDueForRenewalReminder(reminderTime, REMINDER_WINDOW)).isFalse();
		assertThat(area.grantsTrading()).isTrue();
	}

	@Test
	void expiryIsReachedExactlyAtExpiresAt() {
		UserArea area = aVerifiedArea();

		assertThat(area.hasReachedExpiry(area.getExpiresAt().minusMillis(1))).isFalse();
		assertThat(area.hasReachedExpiry(area.getExpiresAt())).isTrue();
	}

	@Test
	void anExpiringAreaReachesExpiryAtExpiresAt() {
		UserArea area = aVerifiedArea();
		area.markExpiring(area.getExpiresAt().minus(Duration.ofDays(2)));

		assertThat(area.hasReachedExpiry(area.getExpiresAt().minusMillis(1))).isFalse();
		assertThat(area.hasReachedExpiry(area.getExpiresAt())).isTrue();
	}

	@Test
	void expiredAreaNoLongerGrantsTradingAndIsNotDueAgain() {
		UserArea area = expiredArea();

		assertThat(area.getStatus()).isEqualTo(AreaStatus.EXPIRED);
		assertThat(area.grantsTrading()).isFalse();
		assertThat(area.hasReachedExpiry(area.getExpiresAt().plus(Duration.ofDays(1)))).isFalse();
	}

	@Test
	void anOverdueVerifiedAreaPassesThroughExpiringToExpired() {
		UserArea area = aVerifiedArea();

		area.expire(area.getExpiresAt());

		assertThat(area.getStatus()).isEqualTo(AreaStatus.EXPIRED);
		assertThat(area.getReminderSentAt()).isNull();
	}

	@Test
	void anAreaCannotExpireBeforeItsExpiry() {
		UserArea area = aVerifiedArea();

		assertThatThrownBy(() -> area.expire(area.getExpiresAt().minusMillis(1))).isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_STATUS_CONFLICT");
	}

	@Test
	void removalEndsTheAreaAndStopsItDrivingTheFeed() {
		UserArea area = aVerifiedArea();
		area.activate(RangeLevel.NEAR, NOW);

		area.remove(NOW.plusSeconds(60));

		assertThat(area.getStatus()).isEqualTo(AreaStatus.REMOVED);
		assertThat(area.isActive()).isFalse();
		assertThat(area.isLive()).isFalse();
		assertThat(area.getRemovedAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void aValidAreaCanBecomeTheActiveAreaWithItsRange() {
		UserArea area = aVerifiedArea();

		area.activate(RangeLevel.FAR, NOW);

		assertThat(area.isActive()).isTrue();
		assertThat(area.getRangeLevel()).isEqualTo(RangeLevel.FAR);
	}

	@Test
	void anExpiredAreaCannotBecomeTheActiveArea() {
		UserArea area = expiredArea();

		assertThatThrownBy(() -> area.activate(RangeLevel.MEDIUM, NOW)).isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_STATUS_CONFLICT");
	}

	@Test
	void deactivationKeepsTheArea() {
		UserArea area = aVerifiedArea();
		area.activate(RangeLevel.MEDIUM, NOW);

		area.deactivate(NOW);

		assertThat(area.isActive()).isFalse();
		assertThat(area.getStatus()).isEqualTo(AreaStatus.VERIFIED);
	}

	@Test
	void rangeAndLabelCanChangeWhileTheAreaIsLive() {
		UserArea area = expiredArea();

		area.changeRange(RangeLevel.NEAR, NOW);
		area.rename(new AreaLabel("Nhà bố mẹ"), NOW);

		assertThat(area.getRangeLevel()).isEqualTo(RangeLevel.NEAR);
		assertThat(area.getLabel()).isEqualTo("Nhà bố mẹ");
	}

	@Test
	void aRemovedAreaCannotBeChanged() {
		UserArea area = aVerifiedArea();
		area.remove(NOW);

		assertThatThrownBy(() -> area.changeRange(RangeLevel.FAR, NOW)).isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> area.rename(new AreaLabel("Mới"), NOW)).isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> area.renew(UUID.randomUUID(), NOW, VALIDITY)).isInstanceOf(ConflictException.class);
	}

	@Test
	void renewalIsCheckedAgainstTheAreaCenter() {
		UserArea area = aVerifiedArea();

		assertThat(area.checkRenewal(northOf(area.center(), 1499), RENEWAL_RADIUS).isWithinRadius()).isTrue();
		assertThat(area.checkRenewal(northOf(area.center(), 1501), RENEWAL_RADIUS).isWithinRadius()).isFalse();
	}

	private static UserArea expiredArea() {
		UserArea area = aVerifiedArea();
		area.markExpiring(area.getExpiresAt().minus(Duration.ofDays(1)));
		area.expire(area.getExpiresAt());
		return area;
	}

}
