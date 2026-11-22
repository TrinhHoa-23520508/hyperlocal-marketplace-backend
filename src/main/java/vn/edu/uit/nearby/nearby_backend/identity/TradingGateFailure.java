package vn.edu.uit.nearby.nearby_backend.identity;

/**
 * A trading gate condition that is not met (FR-ID-20). The client shows the matching call
 * to action: unlock the account, verify the phone, verify an area, renew an area, or give
 * location consent.
 */
public enum TradingGateFailure {

	ACCOUNT_RESTRICTED, PHONE_NOT_VERIFIED, NO_VALID_AREA, AREA_EXPIRED, CONSENT_MISSING

}
