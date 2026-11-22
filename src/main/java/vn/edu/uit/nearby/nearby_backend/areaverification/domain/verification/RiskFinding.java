package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * What one {@link LocationRiskSignal} found. A hard finding always carries a rejection
 * reason and may also be recorded as a device signal; a soft finding is only a device
 * signal.
 */
public final class RiskFinding {

	private final SignalSeverity severity;

	private final @Nullable RejectionReason reason;

	private final @Nullable DeviceSignalCode signalCode;

	private final String rawValue;

	private RiskFinding(SignalSeverity severity, @Nullable RejectionReason reason,
			@Nullable DeviceSignalCode signalCode, String rawValue) {
		this.severity = severity;
		this.reason = reason;
		this.signalCode = signalCode;
		this.rawValue = rawValue;
	}

	public static RiskFinding hard(RejectionReason reason, String rawValue) {
		return new RiskFinding(SignalSeverity.HARD, reason, null, rawValue);
	}

	public static RiskFinding hard(RejectionReason reason, DeviceSignalCode signalCode, String rawValue) {
		return new RiskFinding(SignalSeverity.HARD, reason, signalCode, rawValue);
	}

	public static RiskFinding soft(DeviceSignalCode signalCode, String rawValue) {
		return new RiskFinding(SignalSeverity.SOFT, null, signalCode, rawValue);
	}

	public boolean isHard() {
		return severity == SignalSeverity.HARD;
	}

	public Optional<RejectionReason> reason() {
		return Optional.ofNullable(reason);
	}

	public Optional<DetectedSignal> detectedSignal() {
		return Optional.ofNullable(signalCode).map(code -> new DetectedSignal(code, severity, rawValue));
	}

}
