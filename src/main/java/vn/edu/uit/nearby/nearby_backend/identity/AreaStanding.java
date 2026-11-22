package vn.edu.uit.nearby.nearby_backend.identity;

/**
 * What a user's areas contribute to the trading gate (FR-ID-20).
 */
public enum AreaStanding {

	/** At least one area is VERIFIED or EXPIRING. */
	VALID,

	/** The user has areas, but all are EXPIRED: renewing one opens the gate. */
	ONLY_EXPIRED,

	/** The user has no area: verifying one opens the gate. */
	NONE

}
