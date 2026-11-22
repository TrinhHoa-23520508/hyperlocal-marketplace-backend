package vn.edu.uit.nearby.nearby_backend.areaverification.domain.verification;

/**
 * Why an attempt was rejected, stored in {@code area_verifications.reason_codes} and
 * returned to the client (FR-AR-05, FR-AR-04).
 */
public enum RejectionReason {

	MOCK_LOCATION, LOW_ACCURACY, STALE_FIX, IMPOSSIBLE_SPEED, OUT_OF_RENEW_RADIUS

}
