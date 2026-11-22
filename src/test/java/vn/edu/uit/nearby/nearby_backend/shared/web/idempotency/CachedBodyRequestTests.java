package vn.edu.uit.nearby.nearby_backend.shared.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CachedBodyRequestTests {

	private static final String BODY = "{\"label\":\"Nhà\"}";

	@Test
	void servesTheSameBodyEveryTimeItIsRead() throws IOException {
		CachedBodyRequest request = new CachedBodyRequest(requestWith(BODY, "UTF-8"));

		assertThat(request.getInputStream().readAllBytes()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
		assertThat(request.getInputStream().readAllBytes()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
		assertThat(request.body()).isEqualTo(BODY.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void readsTextWithTheDeclaredCharset() throws IOException {
		CachedBodyRequest request = new CachedBodyRequest(requestWith(BODY, "UTF-8"));

		assertThat(request.getReader().readLine()).isEqualTo(BODY);
	}

	@Test
	void readsTextAsUtf8WhenNoCharsetIsDeclared() throws IOException {
		CachedBodyRequest request = new CachedBodyRequest(requestWith(BODY, null));

		assertThat(request.getReader().readLine()).isEqualTo(BODY);
	}

	@Test
	void reportsWhenTheStreamIsFinished() throws IOException {
		ServletInputStream stream = new CachedBodyRequest(requestWith("{}", "UTF-8")).getInputStream();

		assertThat(stream.isReady()).isTrue();
		assertThat(stream.isFinished()).isFalse();
		stream.readAllBytes();
		assertThat(stream.isFinished()).isTrue();
	}

	@Test
	void readsOneByteAtATime() throws IOException {
		ServletInputStream stream = new CachedBodyRequest(requestWith("{}", "UTF-8")).getInputStream();

		assertThat(stream.read()).isEqualTo('{');
	}

	@Test
	void doesNotSupportNonBlockingReads() throws IOException {
		ServletInputStream stream = new CachedBodyRequest(requestWith("{}", "UTF-8")).getInputStream();

		assertThatThrownBy(() -> stream.setReadListener(null)).isInstanceOf(UnsupportedOperationException.class);
	}

	private static MockHttpServletRequest requestWith(String body, String charset) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/areas/verifications");
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		if (charset != null) {
			request.setCharacterEncoding(charset);
		}
		return request;
	}

}
