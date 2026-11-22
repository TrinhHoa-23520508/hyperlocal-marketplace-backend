package vn.edu.uit.nearby.nearby_backend.shared.web.idempotency;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.HeaderNames;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.security.CurrentUser;

/**
 * Enforces {@link Idempotent}. The first request with a key runs; a repeat with the same
 * body gets the stored response again, a repeat with another body gets
 * {@code 409 IDEMPOTENCY_CONFLICT}, and a repeat while the first is still running gets
 * {@code 409 DUPLICATE_REQUEST}.
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

	private static final String RESERVATION_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".reservation";

	private static final String ANONYMOUS_OWNER = "anonymous";

	private static final Pattern CANONICAL_UUID = Pattern
		.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private static final byte[] NO_BODY = new byte[0];

	private final StringRedisTemplate redis;

	private final Duration ttl;

	public IdempotencyInterceptor(StringRedisTemplate redis, IdempotencyProperties properties) {
		this.redis = redis;
		this.ttl = properties.ttl();
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		if (!isIdempotentEndpoint(handler)) {
			return true;
		}
		Reservation reservation = new Reservation(redisKeyFor(request, requireValidKey(request)),
				IdempotencyRecord.fingerprintOf(bodyOf(request)));
		if (reserve(reservation)) {
			request.setAttribute(RESERVATION_ATTRIBUTE, reservation);
			return true;
		}
		replay(storedRecordFor(reservation), response);
		return false;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable Exception ex) {
		if (!(request.getAttribute(RESERVATION_ATTRIBUTE) instanceof Reservation reservation)) {
			return;
		}
		ContentCachingResponseWrapper bufferedResponse = WebUtils.getNativeResponse(response,
				ContentCachingResponseWrapper.class);
		if (ex != null || bufferedResponse == null || !isFinalOutcome(response.getStatus())) {
			redis.delete(reservation.redisKey());
			return;
		}
		IdempotencyRecord completed = IdempotencyRecord.completed(reservation.requestFingerprint(),
				response.getStatus(), response.getContentType(), bufferedResponse.getContentAsByteArray());
		redis.opsForValue().set(reservation.redisKey(), completed.encode(), ttl);
	}

	/**
	 * Only outcomes the client must not repeat are kept: success, and 422 which this API
	 * uses for a business decision that was recorded (for example a rejected area
	 * verification). Other errors mean nothing happened, so the key is released and the
	 * client may retry with it.
	 */
	private static boolean isFinalOutcome(int status) {
		return HttpStatus.valueOf(status).is2xxSuccessful() || status == HttpStatus.UNPROCESSABLE_CONTENT.value();
	}

	private boolean reserve(Reservation reservation) {
		String inProgress = IdempotencyRecord.inProgress(reservation.requestFingerprint()).encode();
		return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(reservation.redisKey(), inProgress, ttl));
	}

	private IdempotencyRecord storedRecordFor(Reservation reservation) {
		String stored = redis.opsForValue().get(reservation.redisKey());
		if (stored == null) {
			throw duplicateRequest();
		}
		IdempotencyRecord existing = IdempotencyRecord.decode(stored);
		if (!existing.matches(reservation.requestFingerprint())) {
			throw new ConflictException(ErrorCodes.IDEMPOTENCY_CONFLICT,
					"This " + HeaderNames.IDEMPOTENCY_KEY + " was already used with a different request body.");
		}
		if (!existing.isCompleted()) {
			throw duplicateRequest();
		}
		return existing;
	}

	private static void replay(IdempotencyRecord completed, HttpServletResponse response) throws IOException {
		response.setStatus(completed.status());
		if (!completed.contentType().isEmpty()) {
			response.setContentType(completed.contentType());
		}
		response.setHeader(HeaderNames.IDEMPOTENT_REPLAYED, Boolean.TRUE.toString());
		response.getOutputStream().write(completed.body());
	}

	private static ConflictException duplicateRequest() {
		return new ConflictException(ErrorCodes.DUPLICATE_REQUEST, "This request is still being processed.");
	}

	private static boolean isIdempotentEndpoint(Object handler) {
		return handler instanceof HandlerMethod method && method.hasMethodAnnotation(Idempotent.class);
	}

	private static String requireValidKey(HttpServletRequest request) {
		String key = request.getHeader(HeaderNames.IDEMPOTENCY_KEY);
		if (key == null || !CANONICAL_UUID.matcher(key).matches()) {
			throw new InvalidRequestException(ErrorCodes.IDEMPOTENCY_KEY_REQUIRED,
					"An " + HeaderNames.IDEMPOTENCY_KEY + " header holding a UUID is required.");
		}
		return key.toLowerCase(Locale.ROOT);
	}

	private static byte[] bodyOf(HttpServletRequest request) {
		CachedBodyRequest cached = WebUtils.getNativeRequest(request, CachedBodyRequest.class);
		return cached == null ? NO_BODY : cached.body();
	}

	private static String redisKeyFor(HttpServletRequest request, String idempotencyKey) {
		String owner = CurrentUser.id().map(Object::toString).orElse(ANONYMOUS_OWNER);
		return RedisKeyPrefixes.IDEMPOTENCY + owner + ":" + request.getMethod() + ":" + request.getRequestURI() + ":"
				+ idempotencyKey;
	}

	private record Reservation(String redisKey, String requestFingerprint) {
	}

}
