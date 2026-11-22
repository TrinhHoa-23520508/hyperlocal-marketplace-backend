package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.util.List;
import java.util.Optional;

/**
 * Decides an attempt on the server (ADR-04) by running every {@link LocationRiskSignal}.
 * Any hard finding rejects it with all hard reasons found; soft findings are kept for
 * moderators and never change the result (FR-AR-05).
 */
public class AreaVerificationPolicy {

	private final List<LocationRiskSignal> signals;

	public AreaVerificationPolicy(List<LocationRiskSignal> signals) {
		this.signals = List.copyOf(signals);
	}

	/**
	 * The checks of FR-AR-05 in the order the SRS lists them: mock flag, accuracy, fix
	 * age, speed, then the soft signals.
	 */
	public static AreaVerificationPolicy withSrsSignals() {
		return new AreaVerificationPolicy(List.of(new MockLocationSignal(), new LowAccuracySignal(),
				new StaleFixSignal(), new ImpossibleSpeedSignal(), DeviceReportSignal.mockLocationApp(),
				DeviceReportSignal.emulator(), DeviceReportSignal.root(), DeviceReportSignal.developerOptions(),
				new TimeDriftSignal()));
	}

	public Verdict evaluate(VerificationContext context) {
		List<RiskFinding> findings = signals.stream()
			.map(signal -> signal.inspect(context))
			.flatMap(Optional::stream)
			.toList();
		List<RejectionReason> reasons = findings.stream()
			.filter(RiskFinding::isHard)
			.map(RiskFinding::reason)
			.flatMap(Optional::stream)
			.toList();
		List<DetectedSignal> detected = findings.stream()
			.map(RiskFinding::detectedSignal)
			.flatMap(Optional::stream)
			.toList();
		boolean isMockSuspected = detected.stream().anyMatch(signal -> signal.code().indicatesSpoofing());
		return new Verdict(reasons, detected, isMockSuspected);
	}

}
