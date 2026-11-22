package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

import java.util.Optional;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;

/**
 * Soft signal read from the client's device report: emulator, root, developer options or
 * a mock-location app (FR-AR-05). Only recorded, never rejects; unknown is not a finding.
 */
public final class DeviceReportSignal implements LocationRiskSignal {

	private final DeviceSignalCode code;

	private final Function<DeviceSignalReport, @Nullable Boolean> reportedValue;

	private DeviceReportSignal(DeviceSignalCode code, Function<DeviceSignalReport, @Nullable Boolean> reportedValue) {
		this.code = code;
		this.reportedValue = reportedValue;
	}

	public static DeviceReportSignal emulator() {
		return new DeviceReportSignal(DeviceSignalCode.EMULATOR, DeviceSignalReport::isEmulator);
	}

	public static DeviceReportSignal root() {
		return new DeviceReportSignal(DeviceSignalCode.ROOT, DeviceSignalReport::isRooted);
	}

	public static DeviceReportSignal developerOptions() {
		return new DeviceReportSignal(DeviceSignalCode.DEV_OPTIONS, DeviceSignalReport::hasDeveloperOptions);
	}

	public static DeviceReportSignal mockLocationApp() {
		return new DeviceReportSignal(DeviceSignalCode.MOCK_APP, DeviceSignalReport::hasMockLocationApp);
	}

	@Override
	public Optional<RiskFinding> inspect(VerificationContext context) {
		if (!Boolean.TRUE.equals(reportedValue.apply(context.device()))) {
			return Optional.empty();
		}
		return Optional.of(RiskFinding.soft(code, "true"));
	}

}
