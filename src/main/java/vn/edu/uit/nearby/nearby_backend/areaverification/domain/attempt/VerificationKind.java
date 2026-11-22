package vn.edu.uit.nearby.nearby_backend.areaverification.domain.attempt;

/**
 * Purpose of an attempt (data dictionary {@code area_verifications.kind}): a new area
 * (UC-AR-01), a renewal (UC-AR-02) or a replacement of an existing area (UC-AR-03).
 */
public enum VerificationKind {

	INITIAL, RENEWAL, REPLACEMENT

}
