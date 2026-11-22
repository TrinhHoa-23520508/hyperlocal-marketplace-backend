package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.aContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;

/**
 * Soft signals of FR-AR-05 are recorded and never reject; a value the client could not
 * read is unknown and records nothing.
 */
class SoftSignalsTests {

	static Stream<Arguments> deviceSignals() {
		return Stream
			.of(Arguments.of(DeviceSignalCode.EMULATOR, (Supplier<DeviceReportSignal>) DeviceReportSignal::emulator,
					new DeviceSignalReport(true, null, null, null), new DeviceSignalReport(false, null, null, null)),
					Arguments.of(DeviceSignalCode.ROOT, (Supplier<DeviceReportSignal>) DeviceReportSignal::root,
							new DeviceSignalReport(null, true, null, null),
							new DeviceSignalReport(null, false, null, null)),
					Arguments.of(DeviceSignalCode.DEV_OPTIONS,
							(Supplier<DeviceReportSignal>) DeviceReportSignal::developerOptions,
							new DeviceSignalReport(null, null, true, null),
							new DeviceSignalReport(null, null, false, null)),
					Arguments.of(DeviceSignalCode.MOCK_APP,
							(Supplier<DeviceReportSignal>) DeviceReportSignal::mockLocationApp,
							new DeviceSignalReport(null, null, null, true),
							new DeviceSignalReport(null, null, null, false)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("deviceSignals")
	void recordsAReportedDeviceSignalAsSoft(DeviceSignalCode code, Supplier<DeviceReportSignal> signal,
			DeviceSignalReport reported, DeviceSignalReport notReported) {
		RiskFinding finding = signal.get().inspect(aContext().withDevice(reported).build()).orElseThrow();

		assertThat(finding.isHard()).isFalse();
		assertThat(finding.reason()).isEmpty();
		assertThat(finding.detectedSignal()).hasValue(new DetectedSignal(code, SignalSeverity.SOFT, "true"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("deviceSignals")
	void recordsNothingWhenTheDeviceSaysNo(DeviceSignalCode code, Supplier<DeviceReportSignal> signal,
			DeviceSignalReport reported, DeviceSignalReport notReported) {
		assertThat(signal.get().inspect(aContext().withDevice(notReported).build())).isEmpty();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("deviceSignals")
	void treatsAnUnreadableSignalAsUnknown(DeviceSignalCode code, Supplier<DeviceReportSignal> signal,
			DeviceSignalReport reported, DeviceSignalReport notReported) {
		assertThat(signal.get().inspect(aContext().withDevice(DeviceSignalReport.UNKNOWN).build())).isEmpty();
	}

	@Test
	void timeDriftIsRecordedForAFixFromTheFuture() {
		TimeDriftSignal signal = new TimeDriftSignal();

		RiskFinding finding = signal.inspect(aContext().withFixAhead(Duration.ofSeconds(121)).build()).orElseThrow();

		assertThat(finding.isHard()).isFalse();
		assertThat(finding.detectedSignal())
			.hasValue(new DetectedSignal(DeviceSignalCode.TIME_DRIFT, SignalSeverity.SOFT, "121"));
	}

	@Test
	void timeDriftToleratesTheSameWindowAsTheFixAge() {
		TimeDriftSignal signal = new TimeDriftSignal();

		assertThat(signal.inspect(aContext().withFixAhead(Duration.ofSeconds(120)).build())).isEmpty();
		assertThat(signal.inspect(aContext().withFixAge(Duration.ofSeconds(30)).build())).isEmpty();
	}

	@Test
	void signalOnlyFindingsCannotCarryARejectionReason() {
		assertThat(RiskFinding.soft(DeviceSignalCode.ROOT, "true").reason()).isEmpty();
		assertThat(RiskFinding.hard(RejectionReason.STALE_FIX, "130").detectedSignal()).isEmpty();
	}

	@Test
	void deviceSignalsThatPointToAFakedLocation() {
		assertThat(Stream.of(DeviceSignalCode.values()).filter(DeviceSignalCode::indicatesSpoofing))
			.containsExactly(DeviceSignalCode.MOCK_FLAG, DeviceSignalCode.MOCK_APP, DeviceSignalCode.IMPOSSIBLE_SPEED);
	}

	@Test
	void aLaterRuleCanAddAReasonToTheVerdict() {
		Verdict accepted = new Verdict(List.of(), List.of(), false);

		Verdict refused = accepted.rejectedAlsoFor(RejectionReason.OUT_OF_RENEW_RADIUS);

		assertThat(accepted.isAccepted()).isTrue();
		assertThat(refused.isAccepted()).isFalse();
		assertThat(refused.rejectionReasons()).containsExactly(RejectionReason.OUT_OF_RENEW_RADIUS);
	}

	@Test
	void verdictListsCannotBeChangedAfterwards() {
		Verdict verdict = new Verdict(new ArrayList<>(), new ArrayList<>(), false);

		assertThatThrownBy(() -> verdict.rejectionReasons().add(RejectionReason.STALE_FIX))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
