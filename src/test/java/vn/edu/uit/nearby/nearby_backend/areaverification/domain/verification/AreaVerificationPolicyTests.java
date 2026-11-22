package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.UIT;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.aContext;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;

/**
 * FR-AR-05 acceptance: each hard rule has a rejected fixture, and a soft signal on its
 * own never changes the result (appendix D steps 3–4).
 */
class AreaVerificationPolicyTests {

	private final AreaVerificationPolicy policy = AreaVerificationPolicy.withSrsSignals();

	@Test
	void acceptsAFreshAccurateRealFix() {
		Verdict verdict = policy.evaluate(aContext().build());

		assertThat(verdict.isAccepted()).isTrue();
		assertThat(verdict.rejectionReasons()).isEmpty();
		assertThat(verdict.signals()).isEmpty();
		assertThat(verdict.isMockSuspected()).isFalse();
	}

	@Test
	void rejectsAMockFixAndSuspectsSpoofing() {
		Verdict verdict = policy.evaluate(aContext().withMockLocation().build());

		assertThat(verdict.isAccepted()).isFalse();
		assertThat(verdict.rejectionReasons()).containsExactly(RejectionReason.MOCK_LOCATION);
		assertThat(verdict.signals()).extracting(DetectedSignal::code).containsExactly(DeviceSignalCode.MOCK_FLAG);
		assertThat(verdict.isMockSuspected()).isTrue();
	}

	@Test
	void rejectsALowAccuracyFixWithoutSuspectingSpoofing() {
		Verdict verdict = policy.evaluate(aContext().withAccuracyMeters(150).build());

		assertThat(verdict.rejectionReasons()).containsExactly(RejectionReason.LOW_ACCURACY);
		assertThat(verdict.isMockSuspected()).isFalse();
	}

	@Test
	void rejectsAStaleFix() {
		Verdict verdict = policy.evaluate(aContext().withFixAge(Duration.ofMinutes(5)).build());

		assertThat(verdict.rejectionReasons()).containsExactly(RejectionReason.STALE_FIX);
	}

	@Test
	void rejectsAnImpossibleJumpAndSuspectsSpoofing() {
		Verdict verdict = policy
			.evaluate(aContext().at(northOf(UIT, 100_000)).withPreviousFix(UIT, Duration.ofMinutes(10)).build());

		assertThat(verdict.rejectionReasons()).containsExactly(RejectionReason.IMPOSSIBLE_SPEED);
		assertThat(verdict.isMockSuspected()).isTrue();
	}

	@Test
	void reportsEveryBrokenHardRuleInTheSrsOrder() {
		Verdict verdict = policy.evaluate(aContext().withMockLocation()
			.withAccuracyMeters(500)
			.withFixAge(Duration.ofMinutes(10))
			.at(northOf(UIT, 900_000))
			.withPreviousFix(UIT, Duration.ofMinutes(1))
			.build());

		assertThat(verdict.rejectionReasons()).containsExactly(RejectionReason.MOCK_LOCATION,
				RejectionReason.LOW_ACCURACY, RejectionReason.STALE_FIX, RejectionReason.IMPOSSIBLE_SPEED);
	}

	@Test
	void softSignalsAloneNeverReject() {
		Verdict verdict = policy.evaluate(aContext().withDevice(new DeviceSignalReport(true, true, true, false))
			.withFixAhead(Duration.ofMinutes(10))
			.build());

		assertThat(verdict.isAccepted()).isTrue();
		assertThat(verdict.signals()).extracting(DetectedSignal::code)
			.containsExactly(DeviceSignalCode.EMULATOR, DeviceSignalCode.ROOT, DeviceSignalCode.DEV_OPTIONS,
					DeviceSignalCode.TIME_DRIFT);
		assertThat(verdict.signals()).extracting(DetectedSignal::severity).containsOnly(SignalSeverity.SOFT);
		assertThat(verdict.isMockSuspected()).isFalse();
	}

	@Test
	void aMockLocationAppIsAcceptedButSuspected() {
		Verdict verdict = policy
			.evaluate(aContext().withDevice(new DeviceSignalReport(null, null, null, true)).build());

		assertThat(verdict.isAccepted()).isTrue();
		assertThat(verdict.isMockSuspected()).isTrue();
	}

	@Test
	void softSignalsAreKeptAlongsideARejection() {
		Verdict verdict = policy
			.evaluate(aContext().withMockLocation().withDevice(new DeviceSignalReport(true, null, null, null)).build());

		assertThat(verdict.signals()).extracting(DetectedSignal::code)
			.containsExactly(DeviceSignalCode.MOCK_FLAG, DeviceSignalCode.EMULATOR);
	}

	@Test
	void aNewSignalIsUsedWithoutChangingThePolicy() {
		LocationRiskSignal alwaysPlayIntegrityUnknown = context -> Optional
			.of(RiskFinding.soft(DeviceSignalCode.PLAY_INTEGRITY, "UNKNOWN"));

		Verdict verdict = new AreaVerificationPolicy(List.of(alwaysPlayIntegrityUnknown)).evaluate(aContext().build());

		assertThat(verdict.isAccepted()).isTrue();
		assertThat(verdict.signals()).extracting(DetectedSignal::code).containsExactly(DeviceSignalCode.PLAY_INTEGRITY);
	}

	@Test
	void impliedSpeedIsAvailableForTheAttemptRecord() {
		assertThat(aContext().build().impliedSpeedKmh()).isEmpty();
		assertThat(
				aContext().at(northOf(UIT, 1000)).withPreviousFix(UIT, Duration.ofHours(1)).build().impliedSpeedKmh())
			.isPresent();
	}

}
