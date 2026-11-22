package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

/**
 * {@code device_signals.signal_code} values (SRS data dictionary, module area).
 */
public enum DeviceSignalCode {

	MOCK_FLAG, MOCK_APP, EMULATOR, ROOT, DEV_OPTIONS, TIME_DRIFT, IMPOSSIBLE_SPEED, IP_MISMATCH, PLAY_INTEGRITY;

	/**
	 * Signals that point to a faked location rather than a weak one. They set
	 * {@code is_mock_suspected}, which the data dictionary defines as the mock flag and
	 * the hard signals combined, plus a mock-location app (appendix D step 4).
	 */
	public boolean indicatesSpoofing() {
		return this == MOCK_FLAG || this == IMPOSSIBLE_SPEED || this == MOCK_APP;
	}

}
