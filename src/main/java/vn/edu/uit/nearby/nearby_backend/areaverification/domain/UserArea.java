package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RenewalRadius.RenewalCheck;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GridCell;

/**
 * An activity area the user proved to be in with GPS (FR-AR-01). Its center is the
 * snapped grid-cell center, never the raw fix. Status moves only as SRS table
 * {@code tab:sm-area} allows, through {@link AreaStatus}.
 */
@Getter
@Entity
@Table(name = "user_areas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserArea {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false)
	private short slot;

	@Column(nullable = false)
	private String label;

	@Column(name = "locality_name")
	private @Nullable String localityName;

	@Getter(AccessLevel.NONE)
	@Column(nullable = false, columnDefinition = "geography(Point,4326)")
	private Point center;

	@Column(name = "grid_cell", nullable = false)
	private String gridCell;

	@Enumerated(EnumType.STRING)
	@Column(name = "range_level", nullable = false)
	private RangeLevel rangeLevel;

	@Getter(AccessLevel.NONE)
	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AreaStatus status;

	@Column(name = "verified_at", nullable = false)
	private Instant verifiedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "reminder_sent_at")
	private @Nullable Instant reminderSentAt;

	@Column(name = "last_verification_id")
	private @Nullable UUID lastVerificationId;

	@Column(name = "removed_at")
	private @Nullable Instant removedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	private UserArea(NewArea area, Instant now) {
		this.id = UUID.randomUUID();
		this.userId = area.userId();
		this.slot = (short) area.slot();
		this.label = area.label().value();
		this.center = area.cell().center().toPoint();
		this.gridCell = area.cell().code();
		this.rangeLevel = RangeLevel.DEFAULT;
		this.status = AreaStatus.VERIFIED;
		this.createdAt = truncated(now);
	}

	/**
	 * A new VERIFIED area valid for {@code validity} from now (FR-AR-01, FR-AR-03), with
	 * range MEDIUM and not yet active.
	 */
	public static UserArea verified(NewArea area, Instant now, Duration validity) {
		UserArea userArea = new UserArea(area, now);
		userArea.startValidity(area.verificationId(), now, validity);
		return userArea;
	}

	public GeoPoint center() {
		return GeoPoint.from(center);
	}

	public boolean isActive() {
		return active;
	}

	public boolean grantsTrading() {
		return status.grantsTrading();
	}

	public boolean isLive() {
		return status.isLive();
	}

	public RenewalCheck checkRenewal(GeoPoint fixLocation, RenewalRadius radius) {
		return radius.check(center(), fixLocation);
	}

	/**
	 * VERIFIED again with a fresh validity; a new reminder can be sent for the new period
	 * (FR-AR-04).
	 */
	public void renew(UUID verificationId, Instant now, Duration validity) {
		status = status.afterRenewal();
		startValidity(verificationId, now, validity);
	}

	/**
	 * True when a VERIFIED area has at most {@code reminderWindow} left (FR-AR-03, "còn ≤
	 * 3 ngày").
	 */
	public boolean isDueForRenewalReminder(Instant now, Duration reminderWindow) {
		return status == AreaStatus.VERIFIED && !expiresAt.isAfter(now.plus(reminderWindow));
	}

	public void markExpiring(Instant now) {
		status = status.afterRenewalReminder();
		reminderSentAt = truncated(now);
		touch(now);
	}

	/**
	 * True once {@code expires_at} is reached ("tới hạn"), whether or not the reminder
	 * already moved the area to EXPIRING.
	 */
	public boolean hasReachedExpiry(Instant now) {
		return (status == AreaStatus.VERIFIED || status == AreaStatus.EXPIRING) && !now.isBefore(expiresAt);
	}

	/**
	 * EXPIRED once {@code expires_at} is reached (FR-AR-03). A VERIFIED area whose
	 * reminder never ran goes through EXPIRING first, so every move stays on SRS table
	 * {@code tab:sm-area}.
	 */
	public void expire(Instant now) {
		if (!hasReachedExpiry(now)) {
			throw new ConflictException(ErrorCodes.AREA_STATUS_CONFLICT,
					"The area is valid until " + expiresAt + " and cannot expire yet.");
		}
		if (status == AreaStatus.VERIFIED) {
			status = status.afterRenewalReminder();
		}
		status = status.afterExpiry();
		touch(now);
	}

	/**
	 * REMOVED for good (FR-AR-07); it no longer drives the feed and frees its slot.
	 */
	public void remove(Instant now) {
		status = status.afterRemoval();
		active = false;
		removedAt = truncated(now);
		touch(now);
	}

	/**
	 * Makes this area drive the feed with the given range (FR-AR-08). Only a valid area
	 * (VERIFIED or EXPIRING) can be chosen.
	 */
	public void activate(RangeLevel range, Instant now) {
		if (!status.grantsTrading()) {
			throw new ConflictException(ErrorCodes.AREA_STATUS_CONFLICT,
					"Only a valid area can be the active area; this one is " + status + ".");
		}
		active = true;
		rangeLevel = range;
		touch(now);
	}

	public void deactivate(Instant now) {
		active = false;
		touch(now);
	}

	public void changeRange(RangeLevel range, Instant now) {
		requireLive();
		rangeLevel = range;
		touch(now);
	}

	public void rename(AreaLabel newLabel, Instant now) {
		requireLive();
		label = newLabel.value();
		touch(now);
	}

	private void requireLive() {
		if (!status.isLive()) {
			throw new ConflictException(ErrorCodes.AREA_STATUS_CONFLICT, "A removed area cannot be changed.");
		}
	}

	private void startValidity(UUID verificationId, Instant now, Duration validity) {
		verifiedAt = truncated(now);
		expiresAt = verifiedAt.plus(validity);
		reminderSentAt = null;
		lastVerificationId = verificationId;
		touch(now);
	}

	private void touch(Instant now) {
		updatedAt = truncated(now);
	}

	private static Instant truncated(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MILLIS);
	}

	/**
	 * What a newly verified area is made of.
	 *
	 * @param cell the grid cell of the accepted fix; its center becomes the area center
	 * @param verificationId the accepted attempt in {@code area_verifications}
	 */
	public record NewArea(UUID userId, int slot, AreaLabel label, GridCell cell, UUID verificationId) {
	}

}
