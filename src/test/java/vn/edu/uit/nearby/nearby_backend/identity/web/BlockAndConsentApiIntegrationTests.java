package vn.edu.uit.nearby.nearby_backend.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

@IntegrationTest
class BlockAndConsentApiIntegrationTests {

	private static final String BLOCKS = "/api/v1/users/me/blocks";

	private static final String CONSENTS = "/api/v1/users/me/consents";

	@Autowired
	MockMvc mvc;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TokenService tokenService;

	@Autowired
	UserBlocks userBlocks;

	private AccountTestClient client;

	private User user;

	private String bearer;

	@BeforeEach
	void createUser() {
		client = new AccountTestClient(mvc, users, passwordEncoder, tokenService);
		user = client.userWithPhone(AccountTestClient.uniquePhone());
		bearer = client.bearerFor(user);
	}

	@Test
	void blockedUserIsListedAndCountsInBothDirectionsUntilUnblocked() throws Exception {
		User other = client.userWithPhone(AccountTestClient.uniquePhone());

		perform(put(BLOCKS + "/" + other.getId())).andExpect(status().isNoContent());
		perform(put(BLOCKS + "/" + other.getId())).andExpect(status().isNoContent());

		perform(get(BLOCKS)).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].userId").value(other.getId().toString()))
			.andExpect(jsonPath("$.items[0].displayName").value("Test User"))
			.andExpect(jsonPath("$.items[0].phone").doesNotExist());
		assertThat(userBlocks.isBlockedEitherWay(other.getId(), user.getId())).isTrue();
		assertThat(userBlocks.blockedEitherWay(other.getId())).containsExactly(user.getId());

		perform(delete(BLOCKS + "/" + other.getId())).andExpect(status().isNoContent());
		perform(delete(BLOCKS + "/" + other.getId())).andExpect(status().isNoContent());
		assertThat(userBlocks.isBlockedEitherWay(user.getId(), other.getId())).isFalse();
	}

	@Test
	void blockingYourselfOrAnUnknownUserIsRejected() throws Exception {
		perform(put(BLOCKS + "/" + user.getId())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CANNOT_BLOCK_SELF"));
		perform(put(BLOCKS + "/" + UUID.randomUUID())).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void blockListIsPaged() throws Exception {
		for (int i = 0; i < 3; i++) {
			perform(put(BLOCKS + "/" + client.userWithPhone(AccountTestClient.uniquePhone()).getId()));
		}

		String cursor = AccountTestClient.read(perform(get(BLOCKS).param("limit", "2")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.hasMore").value(true))
			.andReturn(), "$.nextCursor");
		perform(get(BLOCKS).param("limit", "2").param("cursor", cursor))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void publicProfileIsOpenToGuestsAndHidesContactData() throws Exception {
		mvc.perform(get("/api/v1/users/" + user.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Test User"))
			.andExpect(jsonPath("$.phoneVerified").value(true))
			.andExpect(jsonPath("$.joinedAt").isNotEmpty())
			.andExpect(jsonPath("$.phone").doesNotExist())
			.andExpect(jsonPath("$.email").doesNotExist())
			.andExpect(jsonPath("$.homeArea").doesNotExist());
		mvc.perform(get("/api/v1/users/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void ownProfileStillRequiresAToken() throws Exception {
		mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void marketingConsentCanBeGrantedAndWithdrawn() throws Exception {
		perform(get(CONSENTS)).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[?(@.purpose == 'PUSH_MARKETING')].granted").value(false));

		perform(put(CONSENTS + "/push-marketing")).andExpect(status().isOk())
			.andExpect(jsonPath("$.granted").value(true))
			.andExpect(jsonPath("$.policyVersion").value("1.0"));
		perform(get(CONSENTS)).andExpect(jsonPath("$[?(@.purpose == 'PUSH_MARKETING')].granted").value(true));

		perform(delete(CONSENTS + "/push-marketing")).andExpect(status().isNoContent());
		perform(get(CONSENTS)).andExpect(jsonPath("$[?(@.purpose == 'PUSH_MARKETING')].granted").value(false))
			.andExpect(jsonPath("$[?(@.purpose == 'PUSH_MARKETING')].withdrawnAt").isNotEmpty());
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

}
