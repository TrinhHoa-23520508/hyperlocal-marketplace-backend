package vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.GRID;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.NOW;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.UIT;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsFix;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsReading;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.DetectedSignal;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.DeviceSignalCode;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.RejectionReason;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.SignalSeverity;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.Verdict;

class AreaVerificationAttemptTests {

	private static final GpsReading READING = new GpsReading(new GpsFix(UIT, NOW.minusSeconds(5)), 15, false);

	@Test
	void recordsARejectionWithItsReasonsAndNoArea() {
		Verdict verdict = new Verdict(List.of(RejectionReason.LOW_ACCURACY, RejectionReason.STALE_FIX), List.of(),
				false);

		AreaVerificationAttempt attempt = AreaVerificationAttempt.record(subject(null, "1.0.0"), evidence(), verdict);

		assertThat(attempt.isAccepted()).isFalse();
		assertThat(attempt.getResult()).isEqualTo(AttemptResult.REJECTED);
		assertThat(attempt.rejectionReasons()).containsExactly(RejectionReason.LOW_ACCURACY, RejectionReason.STALE_FIX);
		assertThat(attempt.getUserAreaId()).isNull();
		assertThat(attempt.isNew()).isTrue();
	}

	@Test
	void keepsThePreciseFixAndItsGridCell() {
		AreaVerificationAttempt attempt = AreaVerificationAttempt.record(subject(UUID.randomUUID(), null), evidence(),
				new Verdict(List.of(), List.of(), false));

		assertThat(attempt.preciseFix()).hasValueSatisfying(fix -> assertThat(fix.location()).isEqualTo(UIT));
		assertThat(attempt.getGridCell()).isEqualTo(GRID.snap(UIT).code());
		assertThat(attempt.getAppVersion()).isNull();
		assertThat(attempt.getImpliedSpeedKmh()).isEqualTo(42f);
	}

	@Test
	void cutsTheAppVersionToTwentyCharacters() {
		AreaVerificationAttempt attempt = AreaVerificationAttempt.record(subject(null, "1.0.0-build-1234567890"),
				evidence(), new Verdict(List.of(), List.of(), false));

		assertThat(attempt.getAppVersion()).hasSize(20).isEqualTo("1.0.0-build-12345678");
	}

	@Test
	void cutsRawSignalValuesToTwoHundredCharacters() {
		Verdict verdict = new Verdict(List.of(),
				List.of(new DetectedSignal(DeviceSignalCode.PLAY_INTEGRITY, SignalSeverity.SOFT, "x".repeat(250))),
				false);

		AreaVerificationAttempt attempt = AreaVerificationAttempt.record(subject(null, null), evidence(), verdict);

		assertThat(attempt.deviceSignals()).singleElement()
			.satisfies(signal -> assertThat(signal.getRawValue()).hasSize(200));
	}

	@Test
	void isNoLongerNewOnceStored() {
		AreaVerificationAttempt attempt = AreaVerificationAttempt.record(subject(null, null), evidence(),
				new Verdict(List.of(), List.of(), false));

		attempt.markStored();

		assertThat(attempt.isNew()).isFalse();
	}

	private static AttemptSubject subject(UUID areaId, String appVersion) {
		return new AttemptSubject(UUID.randomUUID(), UUID.randomUUID(), areaId, VerificationKind.INITIAL,
				UUID.randomUUID(), appVersion);
	}

	private static AttemptEvidence evidence() {
		return new AttemptEvidence(READING, GRID.snap(UIT), OptionalDouble.of(42), null, NOW);
	}

}
