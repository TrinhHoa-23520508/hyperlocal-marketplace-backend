package vn.edu.uit.nearby.nearby_backend.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.identity.web.AccountTestClient.Session;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;
import vn.edu.uit.nearby.nearby_backend.support.RecordingAccountMailer;
import vn.edu.uit.nearby.nearby_backend.support.RecordingSmsSender;

@IntegrationTest
class AuthApiIntegrationTests {

	private static final String SEND_CODE = "/api/v1/auth/register/otp";

	private static final String REGISTER = "/api/v1/auth/register";

	private static final String REFRESH = "/api/v1/auth/refresh";

	private static final String LOGOUT = "/api/v1/auth/logout";

	private static final String FORGOT = "/api/v1/auth/password/forgot";

	private static final String VERIFY_RESET_CODE = "/api/v1/auth/password/verify-otp";

	private static final String RESET = "/api/v1/auth/password/reset";

	private static final String VERIFY_EMAIL = "/api/v1/auth/email-verification";

	private static final String NEW_PASSWORD = "NewSecret456";

	@Autowired
	MockMvc mvc;

	@Autowired
	RecordingSmsSender sms;

	@Autowired
	RecordingAccountMailer mailer;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TokenService tokenService;

	@Autowired
	JdbcClient jdbc;

	private AccountTestClient client;

	private final String phone = AccountTestClient.uniquePhone();

	@BeforeEach
	void createClient() {
		client = new AccountTestClient(mvc, users, passwordEncoder, tokenService);
	}

	@Test
	void phoneVerifiedByCodeOpensAnActiveAccountWithASession() throws Exception {
		String challengeId = sendRegistrationCode(phone);

		MvcResult registered = register(challengeId, sms.lastCodeSentTo(phone).orElseThrow(), "Secret123")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.phone").value(phone))
			.andExpect(jsonPath("$.tokens.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.tokens.refreshToken").isNotEmpty())
			.andReturn();

		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + AccountTestClient.read(registered, "$.tokens.accessToken")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value(phone))
			.andExpect(jsonPath("$.phoneVerified").value(true))
			.andExpect(jsonPath("$.status").value("ACTIVE"));
		client.login(localFormOf(phone), "Secret123").andExpect(status().isOk());
	}

	@Test
	void sendingACodeAnswersWithTheChallengeAndItsTimings() throws Exception {
		client.postJson(SEND_CODE, phoneJson(phone))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.challengeId").isNotEmpty())
			.andExpect(jsonPath("$.expiresInSeconds").value(300))
			.andExpect(jsonPath("$.resendAfterSeconds").value(60));
	}

	@Test
	void redisNeverHoldsTheCodeInPlainText() throws Exception {
		String challengeId = sendRegistrationCode(phone);
		String code = sms.lastCodeSentTo(phone).orElseThrow();

		Map<Object, Object> stored = redis.opsForHash().entries("otp:" + challengeId);

		assertThat(stored).isNotEmpty();
		assertThat(stored.values()).allSatisfy(value -> assertThat(value.toString()).doesNotContain(code));
	}

	@Test
	void askingForAnotherCodeWithinAMinuteIsRateLimited() throws Exception {
		sendRegistrationCode(phone);

		client.postJson(SEND_CODE, phoneJson(phone))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("OTP_RATE_LIMITED"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	@Test
	void wrongCodeTellsHowManyAttemptsAreLeft() throws Exception {
		String challengeId = sendRegistrationCode(phone);
		String wrongCode = sms.lastCodeSentTo(phone).orElseThrow().equals("000000") ? "111111" : "000000";

		register(challengeId, wrongCode, "Secret123").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("OTP_INVALID"))
			.andExpect(jsonPath("$.remainingAttempts").value(4));
	}

	@Test
	void codeWorksOnlyOnce() throws Exception {
		String challengeId = sendRegistrationCode(phone);
		String code = sms.lastCodeSentTo(phone).orElseThrow();
		register(challengeId, code, "Secret123").andExpect(status().isCreated());

		register(challengeId, code, "Secret123").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
	}

	@Test
	void registeredNumberIsReportedOnlyAfterTheCodeIsVerified() throws Exception {
		client.userWithPhone(phone);
		String challengeId = sendRegistrationCode(phone);

		register(challengeId, sms.lastCodeSentTo(phone).orElseThrow(), "Secret123").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
	}

	@Test
	void registrationWithoutAcceptingThePoliciesIsRejected() throws Exception {
		client.postJson(REGISTER, """
				{"phone":"%s","challengeId":"%s","otp":"123456","password":"Secret123","displayName":"An Nguyen",
				 "termsVersion":"1.0"}""".formatted(phone, UUID.randomUUID()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
	}

	@Test
	void outdatedPolicyVersionIsRejected() throws Exception {
		client.postJson(REGISTER, registrationJson(phone, UUID.randomUUID().toString(), "123456", "Secret123", "0.9"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("POLICY_VERSION_STALE"));
	}

	@Test
	void weakPasswordIsRejectedWithEveryReason() throws Exception {
		client.postJson(REGISTER, registrationJson(phone, UUID.randomUUID().toString(), "123456", "short", "1.0"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
			.andExpect(jsonPath("$.violations[*].rule").value(containsInAnyOrder("MIN_LENGTH", "LETTER_AND_DIGIT")));
	}

	@Test
	void malformedRegistrationIsAValidationError() throws Exception {
		client.postJson(REGISTER, """
				{"phone":"","otp":"12","password":"Secret123","displayName":""}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field").value(hasItems("phone", "challengeId", "otp", "displayName")));
	}

	@Test
	void numberThatIsNotAVietnameseMobileIsRejected() throws Exception {
		client.postJson(SEND_CODE, phoneJson("+14155550100"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PHONE"));
	}

	@Test
	void tooManyRegistrationsFromOneIpAreRateLimited() throws Exception {
		String sameIp = AccountTestClient.uniqueClientIp();
		for (int i = 0; i < 10; i++) {
			mvc.perform(registration(sameIp)).andExpect(status().isUnprocessableContent());
		}

		mvc.perform(registration(sameIp))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	@Test
	void wrongPasswordAndUnknownNumberGetIdenticalAnswers() throws Exception {
		client.userWithPhone(phone);

		MvcResult wrongPassword = client.login(phone, "Wrong1234").andExpect(status().isUnauthorized()).andReturn();
		MvcResult unknownPhone = client.login(AccountTestClient.uniquePhone(), "Wrong1234")
			.andExpect(status().isUnauthorized())
			.andReturn();

		assertThat(AccountTestClient.read(unknownPhone, "$.code")).isEqualTo("INVALID_CREDENTIALS");
		assertThat(unknownPhone.getResponse().getContentAsString())
			.isEqualTo(wrongPassword.getResponse().getContentAsString());
	}

	@Test
	void fiveFailedLoginsLockTheAccountEvenForTheRightPassword() throws Exception {
		client.userWithPhone(phone);
		for (int i = 0; i < 5; i++) {
			client.login(phone, "Wrong1234").andExpect(status().isUnauthorized());
		}

		client.login(phone, AccountTestClient.PASSWORD)
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	@Test
	void successfulLoginResetsTheFailureCount() throws Exception {
		client.userWithPhone(phone);
		for (int i = 0; i < 4; i++) {
			client.login(phone, "Wrong1234");
		}
		client.login(phone, AccountTestClient.PASSWORD).andExpect(status().isOk());

		for (int i = 0; i < 4; i++) {
			client.login(phone, "Wrong1234");
		}
		client.login(phone, AccountTestClient.PASSWORD).andExpect(status().isOk());
	}

	@Test
	void refreshRotatesTheTokenAndReusingTheOldOneRevokesTheWholeChain() throws Exception {
		client.userWithPhone(phone);
		Session session = client.loginSession(phone);

		MvcResult rotated = client.postJson(REFRESH, refreshJson(session.refreshToken()))
			.andExpect(status().isOk())
			.andReturn();
		String newRefreshToken = AccountTestClient.read(rotated, "$.refreshToken");
		assertThat(newRefreshToken).isNotEqualTo(session.refreshToken());

		client.postJson(REFRESH, refreshJson(session.refreshToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
		client.postJson(REFRESH, refreshJson(newRefreshToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
	}

	@Test
	void unknownRefreshTokenIsRejected() throws Exception {
		client.postJson(REFRESH, refreshJson("not-a-real-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void manyFailuresFromOneIpLockThatIpForEveryAccount() throws Exception {
		String sharedIp = AccountTestClient.uniqueClientIp();
		client.userWithPhone(phone);
		for (int i = 0; i < 20; i++) {
			client.loginFrom(sharedIp, AccountTestClient.uniquePhone(), "Wrong1234")
				.andExpect(status().isUnauthorized());
		}

		client.loginFrom(sharedIp, phone, AccountTestClient.PASSWORD)
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
		client.login(phone, AccountTestClient.PASSWORD).andExpect(status().isOk());
	}

	@Test
	void logoutWithThePushTokenRemovesThatDeviceOnly() throws Exception {
		User user = client.userWithPhone(phone);
		Session session = client.loginSession(phone);
		insertDeviceToken(user.getId(), "fcm-phone");
		insertDeviceToken(user.getId(), "fcm-tablet");

		client.postJson(LOGOUT, """
				{"refreshToken":"%s","fcmToken":"fcm-phone"}""".formatted(session.refreshToken()))
			.andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(deviceTokensOf(user.getId())).containsExactly("fcm-tablet"));
	}

	@Test
	void logoutRevokesTheRefreshTokenAndIsIdempotent() throws Exception {
		client.userWithPhone(phone);
		Session session = client.loginSession(phone);

		client.postJson(LOGOUT, refreshJson(session.refreshToken())).andExpect(status().isNoContent());
		client.postJson(LOGOUT, refreshJson(session.refreshToken())).andExpect(status().isNoContent());
		client.postJson(LOGOUT, refreshJson("unknown-token")).andExpect(status().isNoContent());

		client.postJson(REFRESH, refreshJson(session.refreshToken())).andExpect(status().isUnauthorized());
	}

	@Test
	void forgotPasswordAnswersTheSameForUnknownNumbersWithoutTexting() throws Exception {
		client.postJson(FORGOT, identifierJson(phone))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.channel").value("SMS"))
			.andExpect(jsonPath("$.challengeId").isNotEmpty())
			.andExpect(jsonPath("$.expiresInSeconds").value(300));

		assertThat(sms.hasTexted(phone)).isFalse();
	}

	@Test
	void passwordResetBySmsSetsTheNewPasswordAndLogsOutEverySession() throws Exception {
		client.userWithPhone(phone);
		Session session = client.loginSession(phone);
		MvcResult started = client.postJson(FORGOT, identifierJson(phone)).andExpect(status().isAccepted()).andReturn();
		String challengeId = AccountTestClient.read(started, "$.challengeId");

		MvcResult verified = client
			.postJson(VERIFY_RESET_CODE,
					"""
							{"challengeId":"%s","phone":"%s","otp":"%s"}""".formatted(challengeId, phone,
							sms.lastCodeSentTo(phone).orElseThrow()))
			.andExpect(status().isOk())
			.andReturn();
		String resetToken = AccountTestClient.read(verified, "$.resetToken");

		client.postJson(RESET, resetJson(resetToken, NEW_PASSWORD)).andExpect(status().isNoContent());

		client.login(phone, AccountTestClient.PASSWORD).andExpect(status().isUnauthorized());
		client.login(phone, NEW_PASSWORD).andExpect(status().isOk());
		client.postJson(REFRESH, refreshJson(session.refreshToken())).andExpect(status().isUnauthorized());
		client.postJson(RESET, resetJson(resetToken, "Another789"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
	}

	@Test
	void passwordResetByVerifiedEmailUsesTheEmailedLink() throws Exception {
		String email = AccountTestClient.uniqueEmail();
		client.activeUser(email);

		client.postJson(FORGOT, identifierJson(email))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.channel").value("EMAIL"));
		String resetToken = mailer.passwordResetTokenFor(email).orElseThrow();

		client.postJson(RESET, resetJson(resetToken, NEW_PASSWORD)).andExpect(status().isNoContent());
		client.login(email, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void optionalEmailCanBeUsedToLogInOnlyOnceVerified() throws Exception {
		client.userWithPhone(phone);
		Session session = client.loginSession(phone);
		String email = AccountTestClient.uniqueEmail();

		mvc.perform(AccountTestClient.jsonRequest(put("/api/v1/users/me/email"), "{\"email\":\"" + email + "\"}")
			.header(HttpHeaders.AUTHORIZATION, session.bearer())).andExpect(status().isAccepted());
		client.login(email, AccountTestClient.PASSWORD).andExpect(status().isUnauthorized());

		String token = mailer.verificationTokenFor(email).orElseThrow();
		client.postJson(VERIFY_EMAIL, "{\"token\":\"" + token + "\"}").andExpect(status().isNoContent());

		client.login(email, AccountTestClient.PASSWORD).andExpect(status().isOk());
		client.postJson(VERIFY_EMAIL, "{\"token\":\"" + token + "\"}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
	}

	@Test
	void changingThePasswordLogsOutOtherDevicesButKeepsThisOne() throws Exception {
		client.userWithPhone(phone);
		Session thisDevice = client.loginSession(phone);
		Session otherDevice = client.loginSession(phone);

		mvc.perform(
				AccountTestClient
					.jsonRequest(put("/api/v1/users/me/password"),
							"""
									{"currentPassword":"%s","newPassword":"%s"}""".formatted(AccountTestClient.PASSWORD,
									NEW_PASSWORD))
					.header(HttpHeaders.AUTHORIZATION, thisDevice.bearer()))
			.andExpect(status().isNoContent());

		client.postJson(REFRESH, refreshJson(otherDevice.refreshToken())).andExpect(status().isUnauthorized());
		client.postJson(REFRESH, refreshJson(thisDevice.refreshToken())).andExpect(status().isOk());
		client.login(phone, NEW_PASSWORD).andExpect(status().isOk());
	}

	private String sendRegistrationCode(String phoneNumber) throws Exception {
		MvcResult result = client.postJson(SEND_CODE, phoneJson(phoneNumber))
			.andExpect(status().isAccepted())
			.andReturn();
		return AccountTestClient.read(result, "$.challengeId");
	}

	private ResultActions register(String challengeId, String code, String password) throws Exception {
		return client.postJson(REGISTER, registrationJson(phone, challengeId, code, password, "1.0"));
	}

	private static String registrationJson(String phoneNumber, String challengeId, String code, String password,
			String policyVersion) {
		return """
				{"phone":"%s","challengeId":"%s","otp":"%s","password":"%s","displayName":"An Nguyen",
				 "termsVersion":"%s","privacyVersion":"1.0","pushMarketing":false}""".formatted(phoneNumber,
				challengeId, code, password, policyVersion);
	}

	private RequestBuilder registration(String clientIp) {
		return post(REGISTER).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","challengeId":"%s","otp":"123456","password":"Secret123","displayName":"An"}"""
				.formatted(AccountTestClient.uniquePhone(), UUID.randomUUID()))
			.with(request -> {
				request.setRemoteAddr(clientIp);
				return request;
			});
	}

	private static String localFormOf(String phoneE164) {
		return "0" + phoneE164.substring("+84".length());
	}

	private static String phoneJson(String phoneNumber) {
		return "{\"phone\":\"" + phoneNumber + "\"}";
	}

	private static String identifierJson(String identifier) {
		return "{\"identifier\":\"" + identifier + "\"}";
	}

	private static String resetJson(String resetToken, String newPassword) {
		return "{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\"}";
	}

	private static String refreshJson(String refreshToken) {
		return "{\"refreshToken\":\"" + refreshToken + "\"}";
	}

	private void insertDeviceToken(UUID userId, String fcmToken) {
		jdbc.sql(
				"INSERT INTO device_tokens (id, user_id, device_id, fcm_token) VALUES (:id, :userId, :deviceId, :fcmToken)")
			.param("id", UUID.randomUUID())
			.param("deviceId", fcmToken)
			.param("userId", userId)
			.param("fcmToken", fcmToken)
			.update();
	}

	private List<String> deviceTokensOf(UUID userId) {
		return jdbc.sql("SELECT fcm_token FROM device_tokens WHERE user_id = :userId")
			.param("userId", userId)
			.query(String.class)
			.list();
	}

}
