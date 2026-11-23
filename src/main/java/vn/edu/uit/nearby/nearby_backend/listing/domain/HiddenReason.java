package vn.edu.uit.nearby.nearby_backend.listing.domain;

/**
 * Why a listing is HIDDEN ({@code listings.hidden_reason}): its owner hid it (FR-LS-07),
 * its area was removed (FR-AR-07) or its seller was sanctioned (FR-MD-05).
 */
public enum HiddenReason {

	OWNER, AREA_REMOVED, SANCTION

}
