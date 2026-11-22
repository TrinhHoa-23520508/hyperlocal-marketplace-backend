package vn.edu.uit.nearby.nearby_backend.shared.web.idempotency;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.nearby.nearby_backend.shared.constant.HeaderNames;
import vn.edu.uit.nearby.nearby_backend.shared.constant.RedisKeyPrefixes;
import vn.edu.uit.nearby.nearby_backend.shared.constant.Roles;
import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
class IdempotencyIntegrationTests {

	private static final String CREATE = "/api/v1/probe/idempotent";

	private static final String REJECTED = "/api/v1/probe/idempotent-rejected";

	private static final String FORBIDDEN = "/api/v1/probe/idempotent-forbidden";

	private static final String FAILING = "/api/v1/probe/idempotent-failing";

	private static final String BODY = "{\"latitude\":10.87}";

	private static final String OTHER_BODY = "{\"latitude\":10.88}";

	@Autowired
	MockMvc mvc;

	@Autowired
	TokenService tokenService;

	@Autowired
	StringRedisTemplate redis;

	private UUID userId;

	private String userToken;

	@BeforeEach
	void issueUserToken() {
		userId = UUID.randomUUID();
		userToken = bearer(userId);
	}

	@Test
	void firstRequestWithKeyIsProcessed() throws Exception {
		send(CREATE, newKey(), BODY).andExpect(status().isCreated())
			.andExpect(header().doesNotExist(HeaderNames.IDEMPOTENT_REPLAYED));
	}

	@Test
	void repeatedKeyWithTheSameBodyReplaysTheOriginalResponseWithoutRunningAgain() throws Exception {
		String key = newKey();
		int firstCall = callNumberOf(send(CREATE, key, BODY));

		send(CREATE, key, BODY).andExpect(status().isCreated())
			.andExpect(header().string(HeaderNames.IDEMPOTENT_REPLAYED, "true"))
			.andExpect(jsonPath("$.call").value(firstCall));
	}

	@Test
	void keyIsMatchedCaseInsensitively() throws Exception {
		String key = newKey();
		int firstCall = callNumberOf(send(CREATE, key, BODY));

		send(CREATE, key.toUpperCase(), BODY).andExpect(jsonPath("$.call").value(firstCall));
	}

	@Test
	void repeatedKeyWithAnotherBodyIsRejectedAsConflict() throws Exception {
		String key = newKey();
		send(CREATE, key, BODY);

		send(CREATE, key, OTHER_BODY).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
	}

	@Test
	void repeatedKeyWhileTheFirstRequestIsRunningIsRejectedAsDuplicate() throws Exception {
		String key = newKey();
		String fingerprint = IdempotencyRecord.fingerprintOf(BODY.getBytes(StandardCharsets.UTF_8));
		redis.opsForValue()
			.set(RedisKeyPrefixes.IDEMPOTENCY + userId + ":POST:" + CREATE + ":" + key,
					IdempotencyRecord.inProgress(fingerprint).encode());

		send(CREATE, key, BODY).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
	}

	@Test
	void sameKeyFromAnotherUserIsIndependent() throws Exception {
		String key = newKey();
		int firstCall = callNumberOf(send(CREATE, key, BODY));

		mvc.perform(post(CREATE).header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))
			.header(HeaderNames.IDEMPOTENCY_KEY, key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isCreated()).andExpect(jsonPath("$.call").value(not(firstCall)));
	}

	@Test
	void businessRejectionIsKeptAndReplayed() throws Exception {
		String key = newKey();
		String firstDetail = JsonPath.read(send(REJECTED, key, BODY).andExpect(status().isUnprocessableContent())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.detail");

		send(REJECTED, key, BODY).andExpect(status().isUnprocessableContent())
			.andExpect(header().string(HeaderNames.IDEMPOTENT_REPLAYED, "true"))
			.andExpect(jsonPath("$.code").value("PROBE_REJECTED"))
			.andExpect(jsonPath("$.detail").value(firstDetail));
	}

	@Test
	void otherClientErrorsReleaseTheKeySoTheClientCanRetry() throws Exception {
		String key = newKey();
		String firstDetail = JsonPath.read(send(FORBIDDEN, key, BODY).andExpect(status().isForbidden())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.detail");

		send(FORBIDDEN, key, BODY).andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HeaderNames.IDEMPOTENT_REPLAYED))
			.andExpect(jsonPath("$.detail").value(not(firstDetail)));
	}

	@Test
	void failedRequestReleasesTheKeySoTheClientCanRetry() throws Exception {
		String key = newKey();
		send(FAILING, key, BODY).andExpect(status().isInternalServerError());

		send(FAILING, key, BODY).andExpect(status().isInternalServerError());
	}

	@Test
	void emptySuccessResponseIsReplayedWithoutBody() throws Exception {
		String key = newKey();
		send("/api/v1/probe/idempotent-empty", key, BODY).andExpect(status().isNoContent());

		send("/api/v1/probe/idempotent-empty", key, BODY).andExpect(status().isNoContent())
			.andExpect(header().string(HeaderNames.IDEMPOTENT_REPLAYED, "true"));
	}

	@Test
	void multipartRequestIsReplayedWithoutBufferingItsBody() throws Exception {
		String key = newKey();
		MockMultipartFile image = new MockMultipartFile("image", "photo.jpg", MediaType.IMAGE_JPEG_VALUE,
				new byte[] { 1, 2, 3 });
		int firstCall = callNumberOf(mvc.perform(multipart(CREATE).file(image)
			.header(HttpHeaders.AUTHORIZATION, userToken)
			.header(HeaderNames.IDEMPOTENCY_KEY, key)));

		mvc.perform(multipart(CREATE).file(image)
			.header(HttpHeaders.AUTHORIZATION, userToken)
			.header(HeaderNames.IDEMPOTENCY_KEY, key))
			.andExpect(header().string(HeaderNames.IDEMPOTENT_REPLAYED, "true"))
			.andExpect(jsonPath("$.call").value(firstCall));
	}

	@Test
	void missingKeyIsRejected() throws Exception {
		mvc.perform(post(CREATE).header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void keyThatIsNotAUuidIsRejected() throws Exception {
		send(CREATE, "retry-1", BODY).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void controllerStillReadsTheBufferedBody() throws Exception {
		mvc.perform(post("/api/v1/probe/validated").header(HttpHeaders.AUTHORIZATION, userToken)
			.header(HeaderNames.IDEMPOTENCY_KEY, newKey())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Nhà\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Nhà"));
	}

	private String bearer(UUID subject) {
		return SecurityConstants.BEARER_PREFIX + tokenService.issueAccessToken(subject, Roles.USER).value();
	}

	private static String newKey() {
		return UUID.randomUUID().toString();
	}

	private static int callNumberOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.call");
	}

	private ResultActions send(String path, String idempotencyKey, String body) throws Exception {
		return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, userToken)
			.header(HeaderNames.IDEMPOTENCY_KEY, idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}
