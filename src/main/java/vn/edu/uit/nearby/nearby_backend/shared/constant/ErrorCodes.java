package vn.edu.uit.nearby.nearby_backend.shared.constant;

public final class ErrorCodes {

	public static final String PROBLEM_TYPE_BASE = "https://nearby.uit.edu.vn/problems/";

	public static final String UNAUTHENTICATED = "UNAUTHENTICATED";

	public static final String ACCESS_DENIED = "ACCESS_DENIED";

	public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

	public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

	public static final String DATA_CONFLICT = "DATA_CONFLICT";

	public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

	public static final String RATE_LIMITED = "RATE_LIMITED";

	public static final String INVALID_CURSOR = "INVALID_CURSOR";

	public static final String IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";

	public static final String DUPLICATE_REQUEST = "DUPLICATE_REQUEST";

	public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";

	public static final String INVALID_EMAIL = "INVALID_EMAIL";

	public static final String INVALID_PHONE = "INVALID_PHONE";

	public static final String OTP_INVALID = "OTP_INVALID";

	public static final String OTP_EXPIRED = "OTP_EXPIRED";

	public static final String OTP_ATTEMPTS_EXCEEDED = "OTP_ATTEMPTS_EXCEEDED";

	public static final String OTP_RATE_LIMITED = "OTP_RATE_LIMITED";

	public static final String SMS_UNAVAILABLE = "SMS_UNAVAILABLE";

	public static final String PHONE_ALREADY_REGISTERED = "PHONE_ALREADY_REGISTERED";

	public static final String CONSENT_REQUIRED = "CONSENT_REQUIRED";

	public static final String POLICY_VERSION_STALE = "POLICY_VERSION_STALE";

	public static final String LOGIN_LOCKED = "LOGIN_LOCKED";

	public static final String RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID";

	public static final String CURRENT_PASSWORD_INVALID = "CURRENT_PASSWORD_INVALID";

	public static final String PASSWORD_REUSED = "PASSWORD_REUSED";

	public static final String EMAIL_UNAVAILABLE = "EMAIL_UNAVAILABLE";

	public static final String EMAIL_NOT_SET = "EMAIL_NOT_SET";

	public static final String WEAK_PASSWORD = "WEAK_PASSWORD";

	public static final String INVALID_VERIFICATION_TOKEN = "INVALID_VERIFICATION_TOKEN";

	public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

	public static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";

	public static final String ACCOUNT_BANNED = "ACCOUNT_BANNED";

	public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

	public static final String REFRESH_TOKEN_REUSED = "REFRESH_TOKEN_REUSED";

	public static final String USER_NOT_FOUND = "USER_NOT_FOUND";

	public static final String INVALID_IMAGE = "INVALID_IMAGE";

	public static final String INVALID_AVATAR_KEY = "INVALID_AVATAR_KEY";

	public static final String AVATAR_NOT_UPLOADED = "AVATAR_NOT_UPLOADED";

	public static final String OUTDATED_TERMS_VERSION = "OUTDATED_TERMS_VERSION";

	public static final String LOCATION_CONSENT_REQUIRED = "LOCATION_CONSENT_REQUIRED";

	public static final String CANNOT_LOCK_OWN_ACCOUNT = "CANNOT_LOCK_OWN_ACCOUNT";

	public static final String CANNOT_LOCK_ADMIN = "CANNOT_LOCK_ADMIN";

	public static final String ACCOUNT_STATUS_CONFLICT = "ACCOUNT_STATUS_CONFLICT";

	public static final String INVALID_SUSPENSION_PERIOD = "INVALID_SUSPENSION_PERIOD";

	public static final String ACCOUNT_DELETION_BLOCKED = "ACCOUNT_DELETION_BLOCKED";

	public static final String CANNOT_DELETE_ADMIN = "CANNOT_DELETE_ADMIN";

	public static final String NAME_CHANGE_TOO_SOON = "NAME_CHANGE_TOO_SOON";

	public static final String CANNOT_BLOCK_SELF = "CANNOT_BLOCK_SELF";

	public static final String CONTACT_NOT_ALLOWED = "CONTACT_NOT_ALLOWED";

	public static final String AREA_VERIFICATION_REJECTED = "AREA_VERIFICATION_REJECTED";

	public static final String AREA_RATE_LIMITED = "AREA_RATE_LIMITED";

	public static final String AREA_LIMIT_REACHED = "AREA_LIMIT_REACHED";

	public static final String AREA_NOT_FOUND = "AREA_NOT_FOUND";

	public static final String AREA_STATUS_CONFLICT = "AREA_STATUS_CONFLICT";

	public static final String OUT_OF_RENEW_RADIUS = "OUT_OF_RENEW_RADIUS";

	public static final String AREA_HAS_OPEN_TRANSACTIONS = "AREA_HAS_OPEN_TRANSACTIONS";

	public static final String INVALID_RANGE = "INVALID_RANGE";

	public static final String OUT_OF_RANGE = "OUT_OF_RANGE";

	public static final String PIN_OUTSIDE_AREA = "PIN_OUTSIDE_AREA";

	public static final String TRADING_GATE_FAILED = "TRADING_GATE_FAILED";

	private ErrorCodes() {
	}

}
