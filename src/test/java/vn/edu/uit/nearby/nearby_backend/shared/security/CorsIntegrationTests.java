package vn.edu.uit.nearby.nearby_backend.shared.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
class CorsIntegrationTests {

	private static final String ADMIN_CONSOLE = "http://localhost:5173";

	@Autowired
	MockMvc mvc;

	@Test
	void preflightFromTheAdminConsoleIsAllowedWithoutCredentials() throws Exception {
		mvc.perform(options("/api/v1/admin/users").header(HttpHeaders.ORIGIN, ADMIN_CONSOLE)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ADMIN_CONSOLE))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	void actualResponseExposesRetryAfterToTheBrowser() throws Exception {
		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.ORIGIN, ADMIN_CONSOLE))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ADMIN_CONSOLE))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
					containsString(HttpHeaders.RETRY_AFTER)));
	}

	@Test
	void otherOriginsAreRejected() throws Exception {
		mvc.perform(options("/api/v1/auth/login").header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

}
