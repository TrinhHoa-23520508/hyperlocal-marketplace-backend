package vn.edu.uit.nearby.nearby_backend.identity.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
class TradingGateApiIntegrationTests {

	private static final String TRADING_GATE = "/api/v1/users/me/trading-gate";

	@Autowired
	MockMvc mvc;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TokenService tokenService;

	@Autowired
	LocationConsentService locationConsents;

	@Autowired
	AppParameters appParameters;

	private AccountTestClient client;

	@BeforeEach
	void createClient() {
		client = new AccountTestClient(mvc, users, passwordEncoder, tokenService);
	}

	@Test
	@DisplayName("FR-ID-20: the gate needs an access token")
	void gateRequiresAnAccessToken() throws Exception {
		mvc.perform(get(TRADING_GATE))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	@DisplayName("UC-LS-01 step 1: a new user sees the gate closed with the steps to do")
	void newUserSeesTheMissingSteps() throws Exception {
		User user = client.userWithPhone(AccountTestClient.uniquePhone());

		mvc.perform(get(TRADING_GATE).header(HttpHeaders.AUTHORIZATION, client.bearerFor(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.open").value(false))
			.andExpect(jsonPath("$.failures", contains("NO_VALID_AREA", "CONSENT_MISSING")));
	}

	@Test
	@DisplayName("FR-ID-15: after consenting, only the area step is left")
	void consentRemovesItsStep() throws Exception {
		User user = client.userWithPhone(AccountTestClient.uniquePhone());
		locationConsents.grantConsent(user.getId(), appParameters.get("consent.location_terms_version"));

		mvc.perform(get(TRADING_GATE).header(HttpHeaders.AUTHORIZATION, client.bearerFor(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.open").value(false))
			.andExpect(jsonPath("$.failures", contains("NO_VALID_AREA")));
	}

}
