package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.UIT;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.aContext;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The four hard rules of FR-AR-05, each with the boundary values of SRS appendix D.
 */
class HardSignalsTests {

	@Nested
	class MockLocation {

		private final MockLocationSignal signal = new MockLocationSignal();

		@Test
		void rejectsAFixFlaggedAsMockByTheOs() {
			RiskFinding finding = signal.inspect(aContext().withMockLocation().build()).orElseThrow();

			assertThat(finding.isHard()).isTrue();
			assertThat(finding.reason()).hasValue(RejectionReason.MOCK_LOCATION);
			assertThat(finding.detectedSignal())
				.hasValue(new DetectedSignal(DeviceSignalCode.MOCK_FLAG, SignalSeverity.HARD, "true"));
		}

		@Test
		void passesARealFix() {
			assertThat(signal.inspect(aContext().build())).isEmpty();
		}

	}

	@Nested
	class LowAccuracy {

		private final LowAccuracySignal signal = new LowAccuracySignal();

		@Test
		void passesAccuracyOfExactlyOneHundredMeters() {
			assertThat(signal.inspect(aContext().withAccuracyMeters(100).build())).isEmpty();
		}

		@Test
		void rejectsAccuracyJustOverOneHundredMeters() {
			RiskFinding finding = signal.inspect(aContext().withAccuracyMeters(100.1).build()).orElseThrow();

			assertThat(finding.reason()).hasValue(RejectionReason.LOW_ACCURACY);
			assertThat(finding.detectedSignal()).isEmpty();
		}

		@Test
		void passesAPerfectFix() {
			assertThat(signal.inspect(aContext().withAccuracyMeters(0).build())).isEmpty();
		}

	}

	@Nested
	class StaleFix {

		private final StaleFixSignal signal = new StaleFixSignal();

		@Test
		void passesAFixOfExactlyOneHundredTwentySeconds() {
			assertThat(signal.inspect(aContext().withFixAge(Duration.ofSeconds(120)).build())).isEmpty();
		}

		@Test
		void rejectsAFixOfOneHundredTwentyOneSeconds() {
			RiskFinding finding = signal.inspect(aContext().withFixAge(Duration.ofSeconds(121)).build()).orElseThrow();

			assertThat(finding.reason()).hasValue(RejectionReason.STALE_FIX);
		}

		@Test
		void leavesAFixFromTheFutureToTheTimeDriftSignal() {
			assertThat(signal.inspect(aContext().withFixAhead(Duration.ofHours(1)).build())).isEmpty();
		}

	}

	@Nested
	class ImpossibleSpeed {

		private final ImpossibleSpeedSignal signal = new ImpossibleSpeedSignal();

		@Test
		void passesAFirstVerificationWithNothingToCompare() {
			assertThat(signal.inspect(aContext().build())).isEmpty();
		}

		@Test
		void passesJustUnderThreeHundredKilometersPerHour() {
			VerificationContext context = aContext().at(northOf(UIT, 4999))
				.withPreviousFix(UIT, Duration.ofMinutes(1))
				.build();

			assertThat(signal.inspect(context)).isEmpty();
		}

		@Test
		void rejectsJustOverThreeHundredKilometersPerHour() {
			VerificationContext context = aContext().at(northOf(UIT, 5001))
				.withPreviousFix(UIT, Duration.ofMinutes(1))
				.build();

			RiskFinding finding = signal.inspect(context).orElseThrow();

			assertThat(finding.reason()).hasValue(RejectionReason.IMPOSSIBLE_SPEED);
			assertThat(finding.detectedSignal())
				.hasValue(new DetectedSignal(DeviceSignalCode.IMPOSSIBLE_SPEED, SignalSeverity.HARD, "300"));
		}

		@Test
		void rejectsTwoPlacesAtTheSameInstant() {
			VerificationContext context = aContext().at(northOf(UIT, 50)).withPreviousFix(UIT, Duration.ZERO).build();

			assertThat(signal.inspect(context)).isPresent();
		}

		@Test
		void passesTheSamePlaceAgain() {
			VerificationContext context = aContext().withPreviousFix(UIT, Duration.ZERO).build();

			assertThat(signal.inspect(context)).isEmpty();
		}

	}

}
