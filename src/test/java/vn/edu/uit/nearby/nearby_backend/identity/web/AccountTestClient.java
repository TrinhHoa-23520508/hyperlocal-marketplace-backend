package vn.edu.uit.nearby.nearby_backend.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.identity.domain.PhoneNumber;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;

/**
 * Creates accounts and sessions for API tests. Every request gets its own client IP so
 * the per-IP registration limit never couples tests together.
 */
final class AccountTestClient {

	static final String PASSWORD = "Secret123";

	private static final AtomicInteger IP_SUFFIX = new AtomicInteger();

	private static final AtomicInteger PHONE_SUFFIX = new AtomicInteger(
			ThreadLocalRandom.current().nextInt(10_000_000, 90_000_000));

	private final MockMvc mvc;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	AccountTestClient(MockMvc mvc, UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.mvc = mvc;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
	}

	static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	static String uniquePhone() {
		return "+849" + PHONE_SUFFIX.incrementAndGet();
	}

	static String uniqueClientIp() {
		int suffix = IP_SUFFIX.incrementAndGet();
		return "10.%d.%d.%d".formatted((suffix >> 16) & 255, (suffix >> 8) & 255, suffix & 255);
	}

	User userWithPhone(String phone) {
		return users
			.save(User.register(PhoneNumber.of(phone), passwordEncoder.encode(PASSWORD), "Test User", Instant.now()));
	}

	/**
	 * A phone-registered user who also verified {@code email}, so tests can log in with
	 * the email address.
	 */
	User activeUser(String email) {
		User user = User.register(PhoneNumber.of(uniquePhone()), passwordEncoder.encode(PASSWORD), "Test User",
				Instant.now());
		EmailAddress address = EmailAddress.of(email);
		user.changeEmail(address, Instant.now());
		user.verifyEmail(address, Instant.now());
		return users.save(user);
	}

	User admin() {
		return users.save(User.administrator(EmailAddress.of(uniqueEmail()), passwordEncoder.encode(PASSWORD),
				"Test Admin", Instant.now()));
	}

	String bearerFor(User user) {
		return SecurityConstants.BEARER_PREFIX
				+ tokenService.issueAccessToken(user.getId(), user.getRole().name()).value();
	}

	ResultActions postJson(String path, String json) throws Exception {
		return mvc.perform(jsonRequest(post(path), json));
	}

	ResultActions postJson(String path, String json, String bearer) throws Exception {
		return mvc.perform(jsonRequest(post(path), json).header(HttpHeaders.AUTHORIZATION, bearer));
	}

	ResultActions login(String identifier, String password) throws Exception {
		return postJson("/api/v1/auth/login", loginJson(identifier, password));
	}

	ResultActions loginFrom(String clientIp, String identifier, String password) throws Exception {
		return mvc
			.perform(jsonRequest(post("/api/v1/auth/login"), loginJson(identifier, password)).with(servletRequest -> {
				servletRequest.setRemoteAddr(clientIp);
				return servletRequest;
			}));
	}

	private static String loginJson(String identifier, String password) {
		return """
				{"identifier":"%s","password":"%s"}""".formatted(identifier, password);
	}

	Session loginSession(String identifier) throws Exception {
		MvcResult result = login(identifier, PASSWORD).andReturn();
		return new Session(read(result, "$.accessToken"), read(result, "$.refreshToken"));
	}

	static MockHttpServletRequestBuilder jsonRequest(MockHttpServletRequestBuilder request, String json) {
		String clientIp = uniqueClientIp();
		return request.contentType(MediaType.APPLICATION_JSON).content(json).with(servletRequest -> {
			servletRequest.setRemoteAddr(clientIp);
			return servletRequest;
		});
	}

	static String read(MvcResult result, String jsonPath) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
	}

	record Session(String accessToken, String refreshToken) {

		String bearer() {
			return SecurityConstants.BEARER_PREFIX + accessToken;
		}

	}

}
