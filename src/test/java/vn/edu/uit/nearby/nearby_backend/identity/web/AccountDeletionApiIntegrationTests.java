package vn.edu.uit.nearby.nearby_backend.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import vn.edu.uit.nearby.nearby_backend.identity.application.account.AccountAnonymisationJob;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AccountStatus;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.identity.web.AccountTestClient.Session;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
class AccountDeletionApiIntegrationTests {

	private static final String ME = "/api/v1/users/me";

	@Autowired
	MockMvc mvc;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TokenService tokenService;

	@Autowired
	AccountAnonymisationJob anonymisationJob;

	@Autowired
	JdbcClient jdbc;

	private AccountTestClient client;

	private String phone;

	private User user;

	private Session session;

	@BeforeEach
	void logIn() throws Exception {
		client = new AccountTestClient(mvc, users, passwordEncoder, tokenService);
		phone = AccountTestClient.uniquePhone();
		user = client.userWithPhone(phone);
		session = client.loginSession(phone);
	}

	@Test
	void deletionEndsEverySessionAndLoggingInAgainCancelsIt() throws Exception {
		deleteAccount(AccountTestClient.PASSWORD).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("PENDING_DELETION"))
			.andExpect(jsonPath("$.deleteAfter").isNotEmpty());

		client.postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + session.refreshToken() + "\"}")
			.andExpect(status().isUnauthorized());
		Session newSession = client.loginSession(phone);
		mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, newSession.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void deletionStopsPushesToEveryDevice() throws Exception {
		jdbc.sql("INSERT INTO device_tokens (id, user_id, fcm_token) VALUES (:id, :userId, :fcmToken)")
			.param("id", UUID.randomUUID())
			.param("userId", user.getId())
			.param("fcmToken", "fcm-" + user.getId())
			.update();

		deleteAccount(AccountTestClient.PASSWORD).andExpect(status().isAccepted());

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(jdbc.sql("SELECT count(*) FROM device_tokens WHERE user_id = :userId")
				.param("userId", user.getId())
				.query(Long.class)
				.single()).isZero());
	}

	@Test
	void wrongPasswordKeepsTheAccount() throws Exception {
		deleteAccount("Wrong1234").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));

		assertThat(users.getRequired(user.getId()).getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void afterTheGracePeriodTheAccountIsAnonymisedAndTheNumberIsFreeAgain() throws Exception {
		deleteAccount(AccountTestClient.PASSWORD).andExpect(status().isAccepted());
		movingDeletionRequestDaysBack(user.getId(), 8);

		anonymisationJob.anonymiseDueAccounts();

		User deleted = users.getRequired(user.getId());
		assertThat(deleted.getStatus()).isEqualTo(AccountStatus.DELETED);
		assertThat(users.existsByPhoneE164(phone)).isFalse();
		client.login(phone, AccountTestClient.PASSWORD).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/" + user.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deleted").value(true))
			.andExpect(jsonPath("$.displayName").value(User.DELETED_DISPLAY_NAME));
		client.userWithPhone(phone);
	}

	@Test
	void requestStillInsideTheGracePeriodIsNotAnonymised() throws Exception {
		deleteAccount(AccountTestClient.PASSWORD).andExpect(status().isAccepted());
		movingDeletionRequestDaysBack(user.getId(), 6);

		anonymisationJob.anonymiseDueAccounts();

		assertThat(users.getRequired(user.getId()).getStatus()).isEqualTo(AccountStatus.PENDING_DELETION);
	}

	@Test
	void administratorCannotDeleteTheirAccount() throws Exception {
		User admin = client.admin();

		mvc.perform(AccountTestClient.jsonRequest(delete(ME), "{\"password\":\"" + AccountTestClient.PASSWORD + "\"}")
			.header(HttpHeaders.AUTHORIZATION, client.bearerFor(admin)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CANNOT_DELETE_ADMIN"));
	}

	private ResultActions deleteAccount(String password) throws Exception {
		return mvc.perform(AccountTestClient.jsonRequest(delete(ME), "{\"password\":\"" + password + "\"}")
			.header(HttpHeaders.AUTHORIZATION, session.bearer()));
	}

	private void movingDeletionRequestDaysBack(UUID userId, int days) {
		jdbc.sql("UPDATE users SET deletion_requested_at = now() - make_interval(days => :days) WHERE id = :id")
			.param("days", days)
			.param("id", userId)
			.update();
	}

}
