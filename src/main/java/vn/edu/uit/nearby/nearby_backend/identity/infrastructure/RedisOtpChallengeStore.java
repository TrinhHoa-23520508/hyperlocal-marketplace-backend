package vn.edu.uit.nearby.nearby_backend.identity.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.application.port.OtpChallengeStore;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpChallenge;
import vn.edu.uit.nearby.nearby_backend.identity.domain.otp.OtpPurpose;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;

/**
 * One Redis hash per challenge, expiring with the challenge. The attempt counter is
 * decremented inside a Lua script so parallel guesses are all counted.
 */
@Component
public class RedisOtpChallengeStore implements OtpChallengeStore {

	private static final String PHONE = "phone";

	private static final String PURPOSE = "purpose";

	private static final String CODE_HASH = "codeHash";

	private static final String EXPIRES_AT = "expiresAt";

	private static final String ATTEMPTS_LEFT = "attemptsLeft";

	@SuppressWarnings("rawtypes")
	private static final RedisScript<List> RECORD_ATTEMPT = RedisScript.of("""
			if redis.call('EXISTS', KEYS[1]) == 0 then
			  return {}
			end
			redis.call('HINCRBY', KEYS[1], ARGV[1], -1)
			return redis.call('HGETALL', KEYS[1])
			""", List.class);

	private final StringRedisTemplate redis;

	private final Clock clock;

	public RedisOtpChallengeStore(StringRedisTemplate redis, Clock clock) {
		this.redis = redis;
		this.clock = clock;
	}

	@Override
	public void save(OtpChallenge challenge) {
		String key = keyOf(challenge.id());
		redis.opsForHash()
			.putAll(key,
					Map.of(PHONE, challenge.phone().value(), PURPOSE, challenge.purpose().name(), CODE_HASH,
							challenge.codeHash(), EXPIRES_AT, challenge.expiresAt().toString(), ATTEMPTS_LEFT,
							String.valueOf(challenge.attemptsLeft())));
		Duration timeToLive = challenge.timeToLive(clock.instant());
		redis.expire(key, timeToLive.isNegative() ? Duration.ZERO : timeToLive);
	}

	@Override
	public Optional<OtpChallenge> recordAttempt(UUID challengeId) {
		List<?> fields = redis.execute(RECORD_ATTEMPT, List.of(keyOf(challengeId)), ATTEMPTS_LEFT);
		Map<String, String> challenge = fields == null ? Map.of() : toMap(fields);
		if (!challenge.containsKey(PHONE)) {
			return Optional.empty();
		}
		return Optional.of(toChallenge(challengeId, challenge));
	}

	@Override
	public void delete(UUID challengeId) {
		redis.delete(keyOf(challengeId));
	}

	private static Map<String, String> toMap(List<?> flatFields) {
		Map<String, String> fields = new HashMap<>();
		for (int i = 0; i + 1 < flatFields.size(); i += 2) {
			fields.put(String.valueOf(flatFields.get(i)), String.valueOf(flatFields.get(i + 1)));
		}
		return fields;
	}

	private static OtpChallenge toChallenge(UUID challengeId, Map<String, String> fields) {
		return new OtpChallenge(challengeId, PhoneNumber.of(fields.get(PHONE)), OtpPurpose.valueOf(fields.get(PURPOSE)),
				fields.get(CODE_HASH), Instant.parse(fields.get(EXPIRES_AT)),
				Integer.parseInt(fields.get(ATTEMPTS_LEFT)));
	}

	private static String keyOf(UUID challengeId) {
		return RedisKeyPrefixes.OTP_CHALLENGE + challengeId;
	}

}
