package vn.edu.uit.nearby.nearby_backend.shared.web.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import org.jspecify.annotations.Nullable;

/**
 * What Redis keeps for one Idempotency-Key: the fingerprint of the first request body
 * and, once it finished, the response to replay. Encoded as one delimited string so a key
 * is reserved and stored with single atomic Redis commands.
 */
record IdempotencyRecord(boolean isCompleted, String requestFingerprint, int status, String contentType, byte[] body) {

	private static final String SEPARATOR = "|";

	private static final String IN_PROGRESS_MARKER = "P";

	private static final String COMPLETED_MARKER = "C";

	private static final int COMPLETED_PARTS = 5;

	static IdempotencyRecord inProgress(String requestFingerprint) {
		return new IdempotencyRecord(false, requestFingerprint, 0, "", new byte[0]);
	}

	static IdempotencyRecord completed(String requestFingerprint, int status, @Nullable String contentType,
			byte[] body) {
		return new IdempotencyRecord(true, requestFingerprint, status, contentType == null ? "" : contentType, body);
	}

	static String fingerprintOf(byte[] requestBody) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(requestBody));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
		}
	}

	static IdempotencyRecord decode(String encoded) {
		String[] parts = encoded.split("\\" + SEPARATOR, COMPLETED_PARTS);
		if (IN_PROGRESS_MARKER.equals(parts[0])) {
			return inProgress(parts[1]);
		}
		return completed(parts[1], Integer.parseInt(parts[2]), parts[3], Base64.getDecoder().decode(parts[4]));
	}

	String encode() {
		if (!isCompleted) {
			return IN_PROGRESS_MARKER + SEPARATOR + requestFingerprint;
		}
		return String.join(SEPARATOR, COMPLETED_MARKER, requestFingerprint, Integer.toString(status), contentType,
				Base64.getEncoder().encodeToString(body));
	}

	boolean matches(String otherFingerprint) {
		return requestFingerprint.equals(otherFingerprint);
	}

}
