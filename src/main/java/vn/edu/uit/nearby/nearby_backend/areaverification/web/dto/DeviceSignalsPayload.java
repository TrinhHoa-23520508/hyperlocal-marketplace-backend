package vn.edu.uit.nearby.nearby_backend.areaverification.web.dto;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.DeviceSignalReport;

@Schema(description = "Device signals read by the app. Send null for a signal that could not be read; "
		+ "signals are stored for moderators and never reject the verification (FR-AR-05)")
public record DeviceSignalsPayload(@Schema(description = "Running on an emulator") @Nullable Boolean emulator,
		@Schema(description = "The device is rooted") @Nullable Boolean rooted,
		@Schema(description = "Developer options are on") @Nullable Boolean developerOptions,
		@Schema(description = "A mock-location app is installed") @Nullable Boolean mockLocationApp) {

	DeviceSignalReport toReport() {
		return new DeviceSignalReport(emulator, rooted, developerOptions, mockLocationApp);
	}

}
