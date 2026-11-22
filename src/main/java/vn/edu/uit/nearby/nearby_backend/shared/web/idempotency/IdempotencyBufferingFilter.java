package vn.edu.uit.nearby.nearby_backend.shared.web.idempotency;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import vn.edu.uit.nearby.nearby_backend.shared.constant.HeaderNames;

/**
 * Buffers the request and response of every call that carries an {@code Idempotency-Key}
 * so {@link IdempotencyInterceptor} can fingerprint the body and store the response for
 * replay. Multipart bodies are left to the container (they are fingerprinted as empty).
 */
@Component
class IdempotencyBufferingFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getHeader(HeaderNames.IDEMPOTENCY_KEY) == null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		ContentCachingResponseWrapper bufferedResponse = new ContentCachingResponseWrapper(response);
		try {
			chain.doFilter(isMultipart(request) ? request : new CachedBodyRequest(request), bufferedResponse);
		}
		finally {
			bufferedResponse.copyBodyToResponse();
		}
	}

	private static boolean isMultipart(HttpServletRequest request) {
		String contentType = request.getContentType();
		return contentType != null
				&& contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
	}

}
