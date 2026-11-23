package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway;

/**
 * The FCM HTTP v1 API: {@code POST /v1/projects/{project}/messages:send}. A 404, or a 400
 * naming an unregistered or invalid token, means the token is dead; any other failure,
 * including a timeout, is logged and never thrown (FR-NT-02).
 */
public class FcmPushGateway implements PushGateway {

	private static final Logger log = LoggerFactory.getLogger(FcmPushGateway.class);

	private final RestClient client;

	private final String projectId;

	private final Supplier<String> accessToken;

	public FcmPushGateway(RestClient client, String projectId, Supplier<String> accessToken) {
		this.client = client;
		this.projectId = projectId;
		this.accessToken = accessToken;
	}

	@Override
	public PushResult send(String deviceToken, PushMessage message) {
		Map<String, Object> body = Map.of("message",
				Map.of("token", deviceToken, "notification", Map.of("title", message.title(), "body", message.body()),
						"data", Map.of("deepLink", message.deepLink()), "android", Map.of("priority", "HIGH")));
		try {
			return client.post()
				.uri("/v1/projects/{project}/messages:send", projectId)
				.header("Authorization", "Bearer " + accessToken.get())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange((request, response) -> resultOf(response.getStatusCode(), response.bodyTo(String.class)));
		}
		catch (RuntimeException ex) {
			log.warn("FCM push failed: {}", ex.toString());
			return PushResult.FAILED;
		}
	}

	private static PushResult resultOf(HttpStatusCode status, String responseBody) {
		if (status.is2xxSuccessful()) {
			return PushResult.SENT;
		}
		if (status.value() == 404 || (status.value() == 400 && mentionsDeadToken(responseBody))) {
			return PushResult.INVALID_TOKEN;
		}
		log.warn("FCM refused the push with status {}", status.value());
		return PushResult.FAILED;
	}

	private static boolean mentionsDeadToken(String responseBody) {
		return responseBody != null
				&& (responseBody.contains("UNREGISTERED") || responseBody.contains("INVALID_ARGUMENT"));
	}

}
