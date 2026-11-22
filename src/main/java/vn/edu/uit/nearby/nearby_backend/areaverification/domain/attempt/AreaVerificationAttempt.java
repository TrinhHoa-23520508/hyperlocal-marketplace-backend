package vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Persistable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.GpsFix;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.RejectionReason;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification.Verdict;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * One verification or renewal attempt, accepted or rejected, with its reason codes
 * (FR-AR-10). The application never updates a row; only the retention job clears the
 * precise location after {@code location.retention_days} (UC-SY-11).
 */
@Getter
@Entity
@Immutable
@Table(name = "area_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AreaVerificationAttempt implements Persistable<UUID> {

	private static final int APP_VERSION_MAX_LENGTH = 20;

	@Id
	private UUID id;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "user_area_id")
	private @Nullable UUID userAreaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VerificationKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AttemptResult result;

	@Getter(AccessLevel.NONE)
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "reason_codes", nullable = false)
	private String[] reasonCodes;

	@Getter(AccessLevel.NONE)
	@Column(name = "reported_location", columnDefinition = "geography(Point,4326)")
	private @Nullable Point reportedLocation;

	@Column(name = "grid_cell", nullable = false)
	private String gridCell;

	@Column(name = "accuracy_m", nullable = false)
	private float accuracyMeters;

	@Column(name = "fix_time", nullable = false)
	private Instant fixTime;

	@Column(name = "distance_m")
	private @Nullable Float distanceMeters;

	@Column(name = "implied_speed_kmh")
	private @Nullable Float impliedSpeedKmh;

	@Column(name = "mock_flag", nullable = false)
	private boolean mockFlag;

	@Column(name = "is_mock_suspected", nullable = false)
	private boolean mockSuspected;

	@Column(name = "idempotency_key", nullable = false)
	private UUID idempotencyKey;

	@Column(name = "app_version")
	private @Nullable String appVersion;

	@Column(name = "location_purged_at")
	private @Nullable Instant locationPurgedAt;

	@Getter(AccessLevel.NONE)
	@Transient
	private boolean newRecord;

	@Getter(AccessLevel.NONE)
	@OneToMany(cascade = CascadeType.PERSIST)
	@JoinColumn(name = "area_verification_id", nullable = false, updatable = false)
	private List<RecordedDeviceSignal> signals;

	/**
	 * Records the attempt with every reason of the verdict and its device signals.
	 */
	public static AreaVerificationAttempt record(AttemptSubject subject, AttemptEvidence evidence, Verdict verdict) {
		AreaVerificationAttempt attempt = new AreaVerificationAttempt();
		attempt.newRecord = true;
		attempt.id = subject.attemptId();
		attempt.createdAt = evidence.receivedAt().truncatedTo(ChronoUnit.MILLIS);
		attempt.userId = subject.userId();
		attempt.userAreaId = subject.userAreaId();
		attempt.kind = subject.kind();
		attempt.result = verdict.isAccepted() ? AttemptResult.ACCEPTED : AttemptResult.REJECTED;
		attempt.reasonCodes = verdict.rejectionReasons().stream().map(Enum::name).toArray(String[]::new);
		attempt.reportedLocation = evidence.reading().fix().location().toPoint();
		attempt.gridCell = evidence.fixCell().code();
		attempt.accuracyMeters = (float) evidence.reading().accuracyMeters();
		attempt.fixTime = evidence.reading().fix().fixTime().truncatedTo(ChronoUnit.MILLIS);
		attempt.distanceMeters = evidence.distanceMeters() == null ? null : evidence.distanceMeters().floatValue();
		attempt.impliedSpeedKmh = evidence.impliedSpeedKmh().isPresent()
				? (float) Math.min(evidence.impliedSpeedKmh().getAsDouble(), Float.MAX_VALUE) : null;
		attempt.mockFlag = evidence.reading().isMockLocation();
		attempt.mockSuspected = verdict.isMockSuspected();
		attempt.idempotencyKey = subject.idempotencyKey();
		attempt.appVersion = truncatedAppVersion(subject.appVersion());
		attempt.signals = verdict.signals()
			.stream()
			.map(signal -> new RecordedDeviceSignal(subject.userId(), signal, attempt.createdAt))
			.toList();
		return attempt;
	}

	/**
	 * Attempts are only ever inserted, so a freshly recorded one is persisted, never
	 * merged.
	 */
	@Override
	public boolean isNew() {
		return newRecord;
	}

	@PostPersist
	@PostLoad
	void markStored() {
		newRecord = false;
	}

	public boolean isAccepted() {
		return result == AttemptResult.ACCEPTED;
	}

	public List<RejectionReason> rejectionReasons() {
		return Arrays.stream(reasonCodes).map(RejectionReason::valueOf).toList();
	}

	public List<RecordedDeviceSignal> deviceSignals() {
		return List.copyOf(signals);
	}

	/**
	 * The precise fix, until the retention job purges it.
	 */
	public Optional<GpsFix> preciseFix() {
		return Optional.ofNullable(reportedLocation).map(point -> new GpsFix(GeoPoint.from(point), fixTime));
	}

	private static @Nullable String truncatedAppVersion(@Nullable String appVersion) {
		if (appVersion == null || appVersion.length() <= APP_VERSION_MAX_LENGTH) {
			return appVersion;
		}
		return appVersion.substring(0, APP_VERSION_MAX_LENGTH);
	}

}
