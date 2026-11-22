package vn.edu.uit.nearby.nearby_backend.areaverification.domain;

import java.util.EnumSet;
import java.util.Set;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

/**
 * Area state machine, exactly as SRS table {@code tab:sm-area}. Every move not listed
 * there is a {@code 409 AREA_STATUS_CONFLICT}.
 */
public enum AreaStatus {

	VERIFIED, EXPIRING, EXPIRED, REMOVED;

	private static final Set<AreaStatus> LIVE = EnumSet.of(VERIFIED, EXPIRING, EXPIRED);

	public AreaStatus afterRenewal() {
		return moveTo(VERIFIED, LIVE);
	}

	public AreaStatus afterRenewalReminder() {
		return moveTo(EXPIRING, EnumSet.of(VERIFIED));
	}

	public AreaStatus afterExpiry() {
		return moveTo(EXPIRED, EnumSet.of(EXPIRING));
	}

	public AreaStatus afterRemoval() {
		return moveTo(REMOVED, LIVE);
	}

	/**
	 * VERIFIED and EXPIRING areas unlock trading (FR-ID-20) and count for the interaction
	 * range (FR-AR-09); an EXPIRED area does not (FR-AR-03).
	 */
	public boolean grantsTrading() {
		return this == VERIFIED || this == EXPIRING;
	}

	public boolean isLive() {
		return this != REMOVED;
	}

	private AreaStatus moveTo(AreaStatus target, Set<AreaStatus> allowedSources) {
		if (!allowedSources.contains(this)) {
			throw new ConflictException(ErrorCodes.AREA_STATUS_CONFLICT,
					"An area that is " + this + " cannot become " + target + ".");
		}
		return target;
	}

}
