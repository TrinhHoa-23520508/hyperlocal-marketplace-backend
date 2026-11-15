package vn.edu.uit.nearby.nearby_backend.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;

@Component
public class TokenService {

	private static final String HMAC_SHA256 = "HmacSHA256";

	private final JwtEncoder jwtEncoder;

	private final JwtProperties properties;

	private final Clock clock;

	private final SecureRandom secureRandom = new SecureRandom();

	public TokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public AccessToken issueAccessToken(UUID userId, String role) {
		return encode(claimsFor(userId, role).build());
	}

	/**
	 * @param sessionId the refresh token family of the session, read back with
	 * {@link CurrentUser#sessionId()}
	 */
	public AccessToken issueAccessToken(UUID userId, String role, UUID sessionId) {
		return encode(claimsFor(userId, role).claim(SecurityConstants.SESSION_CLAIM, sessionId.toString()).build());
	}

	private JwtClaimsSet.Builder claimsFor(UUID userId, String role) {
		Instant issuedAt = clock.instant();
		return JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(userId.toString())
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plus(properties.accessTokenTtl()))
			.id(UUID.randomUUID().toString())
			.claim(SecurityConstants.ROLES_CLAIM, List.of(role));
	}

	private AccessToken encode(JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(value, claims.getExpiresAt());
	}

	public RefreshToken issueRefreshToken() {
		byte[] bytes = new byte[SecurityConstants.REFRESH_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new RefreshToken(value, sha256Hex(value), clock.instant().plus(properties.refreshTokenTtl()));
	}

	public static String sha256Hex(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every JVM", ex);
		}
	}

	public static String hmacSha256Hex(String secret, String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HmacSHA256 is required by every JVM", ex);
		}
	}

	public record AccessToken(String value, Instant expiresAt) {
	}

	public record RefreshToken(String value, String hash, Instant expiresAt) {
	}

}
