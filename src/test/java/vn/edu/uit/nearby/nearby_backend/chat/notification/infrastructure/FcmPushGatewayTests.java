package vn.edu.uit.nearby.nearby_backend.chat.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway.PushMessage;
import vn.edu.uit.nearby.nearby_backend.chat.notification.application.PushGateway.PushResult;

/**
 * FR-NT-02 against a stub FCM server: success, a dead token, an FCM error and a timeout.
 */
class FcmPushGatewayTests {

	private static final PushMessage MESSAGE = new PushMessage("Có đề nghị mới", "An muốn mua ghế gỗ",
			"app://transactions/t-1");

	private HttpServer server;

	private final AtomicReference<String> requestBody = new AtomicReference<>();

	private final AtomicReference<String> authorization = new AtomicReference<>();

	private final AtomicReference<String> path = new AtomicReference<>();

	private int status = 200;

	private String responseBody = "{\"name\":\"projects/p/messages/1\"}";

	private long delayMillis;

	@BeforeEach
	void startStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			path.set(exchange.getRequestURI().getPath());
			if (delayMillis > 0) {
				try {
					Thread.sleep(delayMillis);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopStub() {
		server.stop(0);
	}

	private FcmPushGateway gateway(Duration timeout) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(timeout);
		factory.setReadTimeout(timeout);
		RestClient client = RestClient.builder()
			.baseUrl("http://localhost:" + server.getAddress().getPort())
			.requestFactory(factory)
			.build();
		return new FcmPushGateway(client, "nearby-project", () -> "access-token-1");
	}

	@Test
	@DisplayName("a 200 is SENT, posted to the project's messages:send with the bearer token and the deep link")
	void sendsTheMessage() {
		PushResult result = gateway(Duration.ofSeconds(2)).send("device-token-1", MESSAGE);

		assertThat(result).isEqualTo(PushResult.SENT);
		assertThat(path.get()).isEqualTo("/v1/projects/nearby-project/messages:send");
		assertThat(authorization.get()).isEqualTo("Bearer access-token-1");
		assertThat(requestBody.get()).contains("\"token\":\"device-token-1\"")
			.contains("\"title\":\"Có đề nghị mới\"")
			.contains("\"deepLink\":\"app://transactions/t-1\"");
	}

	@Test
	@DisplayName("FR-NT-02: a 404 means the token is dead")
	void aNotFoundIsAnInvalidToken() {
		status = 404;
		responseBody = "{\"error\":{\"status\":\"NOT_FOUND\"}}";

		assertThat(gateway(Duration.ofSeconds(2)).send("t", MESSAGE)).isEqualTo(PushResult.INVALID_TOKEN);
	}

	@Test
	void aBadRequestNamingAnUnregisteredTokenIsAnInvalidToken() {
		status = 400;
		responseBody = "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"The registration token is not valid\"}}";

		assertThat(gateway(Duration.ofSeconds(2)).send("t", MESSAGE)).isEqualTo(PushResult.INVALID_TOKEN);
	}

	@Test
	void anotherBadRequestIsAFailureNotADeadToken() {
		status = 400;
		responseBody = "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}";

		assertThat(gateway(Duration.ofSeconds(2)).send("t", MESSAGE)).isEqualTo(PushResult.FAILED);
	}

	@Test
	void aServerErrorIsAFailureAndNeverThrows() {
		status = 503;
		responseBody = "unavailable";

		assertThat(gateway(Duration.ofSeconds(2)).send("t", MESSAGE)).isEqualTo(PushResult.FAILED);
	}

	@Test
	@DisplayName("FR-NT-02: a slow FCM times out and the push is dropped")
	void aTimeoutIsAFailure() {
		delayMillis = 1_500;

		assertThat(gateway(Duration.ofMillis(300)).send("t", MESSAGE)).isEqualTo(PushResult.FAILED);
	}

	@Test
	void anUnreachableFcmIsAFailure() {
		server.stop(0);

		assertThat(gateway(Duration.ofMillis(300)).send("t", MESSAGE)).isEqualTo(PushResult.FAILED);
	}

}
