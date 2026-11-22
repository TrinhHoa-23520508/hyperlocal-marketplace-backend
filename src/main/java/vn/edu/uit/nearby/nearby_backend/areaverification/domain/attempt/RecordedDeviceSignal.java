package vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.DetectedSignal;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.DeviceSignalCode;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.SignalSeverity;

/**
 * One {@code device_signals} row of an attempt (FR-AR-05), shown to moderators in User
 * 360 (FR-MD-09).
 */
@Getter
@Entity
@Immutable
@Table(name = "device_signals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordedDeviceSignal {

	private static final int RAW_VALUE_MAX_LENGTH = 200;

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "signal_code", nullable = false)
	private DeviceSignalCode signalCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SignalSeverity severity;

	@Column(name = "raw_value")
	private String rawValue;

	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;

	RecordedDeviceSignal(UUID userId, DetectedSignal signal, Instant detectedAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.signalCode = signal.code();
		this.severity = signal.severity();
		this.rawValue = signal.rawValue().length() > RAW_VALUE_MAX_LENGTH
				? signal.rawValue().substring(0, RAW_VALUE_MAX_LENGTH) : signal.rawValue();
		this.detectedAt = detectedAt;
	}

}
