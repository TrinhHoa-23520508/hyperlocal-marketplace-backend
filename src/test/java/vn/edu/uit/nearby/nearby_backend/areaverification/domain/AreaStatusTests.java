package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.UnaryOperator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

/**
 * Every edge of SRS table {@code tab:sm-area} is allowed; every other move is refused.
 */
class AreaStatusTests {

	@ParameterizedTest(name = "{0} --{1}--> {2}")
	@CsvSource({ "VERIFIED, RENEW, VERIFIED", "EXPIRING, RENEW, VERIFIED", "EXPIRED, RENEW, VERIFIED",
			"VERIFIED, REMIND, EXPIRING", "EXPIRING, EXPIRE, EXPIRED", "VERIFIED, REMOVE, REMOVED",
			"EXPIRING, REMOVE, REMOVED", "EXPIRED, REMOVE, REMOVED" })
	void allowsEveryTransitionOfTheSrsTable(AreaStatus from, Action action, AreaStatus expected) {
		assertThat(action.apply(from)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} --{1}--> refused")
	@CsvSource({ "REMOVED, RENEW", "REMOVED, REMIND", "REMOVED, EXPIRE", "REMOVED, REMOVE", "EXPIRING, REMIND",
			"EXPIRED, REMIND", "VERIFIED, EXPIRE", "EXPIRED, EXPIRE" })
	void refusesEveryOtherTransition(AreaStatus from, Action action) {
		assertThatThrownBy(() -> action.apply(from)).isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "AREA_STATUS_CONFLICT");
	}

	@ParameterizedTest
	@CsvSource({ "VERIFIED, true", "EXPIRING, true", "EXPIRED, false", "REMOVED, false" })
	void onlyVerifiedAndExpiringAreasGrantTrading(AreaStatus status, boolean expected) {
		assertThat(status.grantsTrading()).isEqualTo(expected);
	}

	@ParameterizedTest
	@EnumSource(value = AreaStatus.class, names = "REMOVED", mode = EnumSource.Mode.EXCLUDE)
	void everyStatusButRemovedIsLive(AreaStatus status) {
		assertThat(status.isLive()).isTrue();
		assertThat(AreaStatus.REMOVED.isLive()).isFalse();
	}

	enum Action implements UnaryOperator<AreaStatus> {

		RENEW(AreaStatus::afterRenewal), REMIND(AreaStatus::afterRenewalReminder), EXPIRE(AreaStatus::afterExpiry),
		REMOVE(AreaStatus::afterRemoval);

		private final UnaryOperator<AreaStatus> move;

		Action(UnaryOperator<AreaStatus> move) {
			this.move = move;
		}

		@Override
		public AreaStatus apply(AreaStatus status) {
			return move.apply(status);
		}

	}

}
