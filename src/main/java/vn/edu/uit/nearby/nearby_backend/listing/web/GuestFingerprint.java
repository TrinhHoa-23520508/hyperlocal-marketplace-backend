package vn.edu.uit.nearby.nearby_backend.listing.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tells guests apart for the daily view count (FR-LS-14, DEC-LS-8) without storing their
 * IP address: a hash of the client address and User-Agent.
 */
final class GuestFingerprint {

	private static final int HEX_CHARACTERS = 32;

	private GuestFingerprint() {
	}

	static String of(HttpServletRequest request) {
		String userAgent = String.valueOf(request.getHeader(HttpHeaders.USER_AGENT));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest((request.getRemoteAddr() + "|" + userAgent).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest).substring(0, HEX_CHARACTERS);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available", ex);
		}
	}

}
