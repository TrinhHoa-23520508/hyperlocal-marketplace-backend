package vn.edu.uit.nearby.nearby_backend.shared.web.idempotency;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Reads the whole body up front so it can be fingerprinted before the controller reads
 * it, then serves the same bytes to the controller.
 */
class CachedBodyRequest extends HttpServletRequestWrapper {

	private final byte[] body;

	CachedBodyRequest(HttpServletRequest request) throws IOException {
		super(request);
		this.body = request.getInputStream().readAllBytes();
	}

	byte[] body() {
		return body.clone();
	}

	@Override
	public ServletInputStream getInputStream() {
		ByteArrayInputStream source = new ByteArrayInputStream(body);
		return new ServletInputStream() {

			@Override
			public int read() {
				return source.read();
			}

			@Override
			public int read(byte[] buffer, int offset, int length) {
				return source.read(buffer, offset, length);
			}

			@Override
			public boolean isFinished() {
				return source.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener listener) {
				throw new UnsupportedOperationException("The body is already buffered");
			}

		};
	}

	@Override
	public BufferedReader getReader() {
		return new BufferedReader(new InputStreamReader(getInputStream(), bodyCharset()));
	}

	private Charset bodyCharset() {
		String encoding = getCharacterEncoding();
		return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
	}

}
