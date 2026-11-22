package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

/**
 * A row to store in {@code device_signals} for an attempt.
 *
 * @param rawValue the value that triggered the signal, at most 200 characters
 */
public record DetectedSignal(DeviceSignalCode code, SignalSeverity severity, String rawValue) {
}
