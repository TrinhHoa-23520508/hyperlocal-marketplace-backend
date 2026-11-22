package vn.edu.uit.nearby.nearby_backend.areaverification.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaTestAccounts;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.HeaderNames;
import vn.edu.uit.nearby.nearby_backend.shared.constant.Roles;
import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * The area endpoints (A27, A28): status codes, RFC 9457 bodies with their codes, and no
 * coordinates in any response (NFR-15).
 */
@IntegrationTest
class AreaApiIntegrationTests {

	private static final String AREAS = "/api/v1/areas";

	private static final String MY_AREAS = AREAS + "/me";

	private static final String VERIFICATIONS = AREAS + "/verifications";

	private static final GeoPoint UIT = new GeoPoint(10.8700, 106.8031);

	private static final GeoPoint DISTRICT_1 = new GeoPoint(10.7769, 106.7009);

	private static final GeoPoint THU_DUC_MARKET = new GeoPoint(10.8506, 106.7720);

	@Autowired
	MockMvc mvc;

	@Autowired
	TokenService tokenService;

	@Autowired
	UserAreaRepository areas;

	@Autowired
	AreaVerificationAttemptRepository attempts;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	UserRepository users;

	@Autowired
	LocationConsentService locationConsents;

	@Autowired
	AppParameters appParameters;

	@Autowired
	JdbcTemplate jdbc;

	private AreaTestAccounts accounts;

	private UUID userId;

	private String bearer;

	@BeforeEach
	void createEligibleUser() {
		accounts = new AreaTestAccounts(users, locationConsents, appParameters);
		userId = accounts.eligibleUser();
		bearer = bearerFor(userId);
	}

	@Test
	@DisplayName("Area endpoints need an access token")
	void areasNeedAnAccessToken() throws Exception {
		mvc.perform(get(MY_AREAS))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	@DisplayName("UC-AR-01: verifying answers 201 with the area and never a coordinate (NFR-15)")
	void verifyingCreatesAnArea() throws Exception {
		verify(UIT, UUID.randomUUID()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.renewal").value(false))
			.andExpect(jsonPath("$.area.slot").value(1))
			.andExpect(jsonPath("$.area.label").value("Khu vực 1"))
			.andExpect(jsonPath("$.area.status").value("VERIFIED"))
			.andExpect(jsonPath("$.area.rangeLevel").value("MEDIUM"))
			.andExpect(jsonPath("$.area.active").value(true))
			.andExpect(content().string(not(containsString("latitude"))))
			.andExpect(content().string(not(containsString("center"))))
			.andExpect(content().string(not(containsString("longitude"))));
	}

	@Test
	@DisplayName("FR-AR-05: device signals the app could not read may be left out; they count as unknown")
	void deviceSignalsAreOptional() throws Exception {
		String body = """
				{"latitude": 10.87, "longitude": 106.8031, "accuracyMeters": 20, "fixTime": "%s", "mockLocation": false}"""
			.formatted(Instant.now().minusSeconds(5));

		send(post(VERIFICATIONS), UUID.randomUUID(), body).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("UC-AR-01 A2: resending the same Idempotency-Key replays the response and creates one area")
	void resendingTheSameKeyReplays() throws Exception {
		UUID key = UUID.randomUUID();
		String firstBody = verifyBody(UIT, null, null);
		String first = send(post(VERIFICATIONS), key, firstBody).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		send(post(VERIFICATIONS), key, firstBody).andExpect(status().isCreated())
			.andExpect(header().string(HeaderNames.IDEMPOTENT_REPLAYED, "true"))
			.andExpect(content().json(first));
		mvc.perform(get(MY_AREAS).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	@DisplayName("UC-AR-01 E6: the same key with another body answers 409 IDEMPOTENCY_CONFLICT")
	void sameKeyWithAnotherBodyConflicts() throws Exception {
		UUID key = UUID.randomUUID();
		verify(UIT, key);

		verify(DISTRICT_1, key).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
	}

	@Test
	@DisplayName("Section 5.4: verification without an Idempotency-Key is refused")
	void verificationNeedsAnIdempotencyKey() throws Exception {
		mvc.perform(post(VERIFICATIONS).header(HttpHeaders.AUTHORIZATION, bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(verifyBody(UIT, null, null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	@DisplayName("tab:fieldrules ARE: coordinates and accuracy out of range answer 400 VALIDATION_FAILED")
	void invalidFieldsAreRefused() throws Exception {
		String body = """
				{"latitude": 91, "longitude": 106.8, "accuracyMeters": -1, "fixTime": "%s", "mockLocation": false,
				 "label": "   "}""".formatted(Instant.now());

		send(post(VERIFICATIONS), UUID.randomUUID(), body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("latitude", "accuracyMeters", "label")));
	}

	@Test
	@DisplayName("UC-AR-01 E4: a mock fix answers 422 with its reason, Vietnamese guidance and the stored attempt")
	void mockFixIsRejected() throws Exception {
		send(post(VERIFICATIONS), UUID.randomUUID(), verifyBody(UIT, true, null))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("AREA_VERIFICATION_REJECTED"))
			.andExpect(jsonPath("$.reasons", contains("MOCK_LOCATION")))
			.andExpect(jsonPath("$.guidance[0].reason").value("MOCK_LOCATION"))
			.andExpect(jsonPath("$.guidance[0].message").value(containsString("vị trí giả")))
			.andExpect(jsonPath("$.attemptId").isNotEmpty());
	}

	@Test
	@DisplayName("UC-AR-01 E1: without location consent the answer is 403 LOCATION_CONSENT_REQUIRED")
	void verificationNeedsConsent() throws Exception {
		String noConsent = bearerFor(accounts.userWithoutConsent());

		mvc.perform(post(VERIFICATIONS).header(HttpHeaders.AUTHORIZATION, noConsent)
			.header(HeaderNames.IDEMPOTENCY_KEY, UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(verifyBody(UIT, null, null)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOCATION_CONSENT_REQUIRED"));
	}

	@Test
	@DisplayName("FR-AR-02: a third area answers 409 AREA_LIMIT_REACHED with the two areas")
	void thirdAreaIsRefused() throws Exception {
		verify(UIT, UUID.randomUUID()).andExpect(status().isCreated());
		forgetPreviousFixes();
		verify(DISTRICT_1, UUID.randomUUID()).andExpect(status().isCreated());
		forgetPreviousFixes();

		verify(THU_DUC_MARKET, UUID.randomUUID()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AREA_LIMIT_REACHED"))
			.andExpect(jsonPath("$.areaIds", hasSize(2)));
	}

	@Test
	@DisplayName("UC-AR-03: replacing an area answers 201 with the new area in the old slot")
	void replacingAnArea() throws Exception {
		String first = areaIdOf(verify(UIT, UUID.randomUUID()));
		forgetPreviousFixes();

		send(post(VERIFICATIONS), UUID.randomUUID(), verifyBody(DISTRICT_1, null, first))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.area.slot").value(1))
			.andExpect(jsonPath("$.area.id").value(not(first)));
	}

	@Test
	@DisplayName("FR-AR-06: the 6th attempt of the day answers 429 AREA_RATE_LIMITED with Retry-After")
	void sixthAttemptIsRateLimited() throws Exception {
		for (int attempt = 0; attempt < 5; attempt++) {
			send(post(VERIFICATIONS), UUID.randomUUID(), verifyBody(UIT, true, null));
		}

		verify(UIT, UUID.randomUUID()).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("AREA_RATE_LIMITED"))
			.andExpect(jsonPath("$.reason").value("DAILY_ATTEMPTS"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	@Test
	@DisplayName("UC-AR-02: renewing within 1,500 m answers 200 with the renewed area")
	void renewingWithinTheRadius() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));
		forgetPreviousFixes();

		renew(areaId, northOf(centerOf(areaId), 1499)).andExpect(status().isOk())
			.andExpect(jsonPath("$.renewal").value(true))
			.andExpect(jsonPath("$.area.id").value(areaId));
	}

	@Test
	@DisplayName("FR-AR-04: renewing 1,501 m away answers 422 OUT_OF_RENEW_RADIUS with the rounded distance")
	void renewingOutsideTheRadius() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));
		forgetPreviousFixes();

		renew(areaId, northOf(centerOf(areaId), 1501)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("OUT_OF_RENEW_RADIUS"))
			.andExpect(jsonPath("$.distanceMeters").value(1500))
			.andExpect(jsonPath("$.guidance[0].reason").value("OUT_OF_RENEW_RADIUS"));
	}

	@Test
	@DisplayName("UC-AR-02 E3: renewing another user's area answers 404 AREA_NOT_FOUND")
	void renewingSomeoneElsesArea() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));
		bearer = bearerFor(accounts.eligibleUser());

		renew(areaId, UIT).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AREA_NOT_FOUND"));
	}

	@Test
	@DisplayName("A27: the list shows the caller's areas")
	void listsMyAreas() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));

		mvc.perform(get(MY_AREAS).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(areaId));
	}

	@Test
	@DisplayName("UC-AR-01 step 9: naming an area; a blank name answers 400")
	void namingAnArea() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));

		jsonRequest(patch(AREAS + "/" + areaId), "{\"label\": \"Nhà\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.label").value("Nhà"));
		jsonRequest(patch(AREAS + "/" + areaId), "{\"label\": \" \"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	@DisplayName("UC-AR-04: choosing the active area and range; an unknown range answers 400 INVALID_RANGE")
	void choosingTheActiveArea() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));

		jsonRequest(put(AREAS + "/active"), "{\"areaId\": \"%s\", \"rangeLevel\": \"FAR\"}".formatted(areaId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rangeLevel").value("FAR"))
			.andExpect(jsonPath("$.active").value(true));
		jsonRequest(put(AREAS + "/active"), "{\"areaId\": \"%s\", \"rangeLevel\": \"HUGE\"}".formatted(areaId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RANGE"));
	}

	@Test
	@DisplayName("FR-AR-07: removing an area answers 204; removing it again answers 409")
	void removingAnArea() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));

		mvc.perform(delete(AREAS + "/" + areaId).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isNoContent());
		mvc.perform(get(MY_AREAS).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(delete(AREAS + "/" + areaId).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AREA_STATUS_CONFLICT"));
	}

	@Test
	@DisplayName("FR-ID-12: the own profile lists each area with its status and expiry, never a coordinate")
	void ownProfileListsTheAreas() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));

		mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.areas", hasSize(1)))
			.andExpect(jsonPath("$.areas[0].id").value(areaId))
			.andExpect(jsonPath("$.areas[0].label").value("Khu vực 1"))
			.andExpect(jsonPath("$.areas[0].status").value("VERIFIED"))
			.andExpect(jsonPath("$.areas[0].active").value(true))
			.andExpect(jsonPath("$.areas[0].expiresAt").isNotEmpty())
			.andExpect(content().string(not(containsString("latitude"))))
			.andExpect(content().string(not(containsString("homeArea"))));
	}

	@Test
	@DisplayName("FR-ID-13: the public profile shows the area label of a valid area only, never a coordinate")
	void publicProfileShowsTheAreaLabel() throws Exception {
		String areaId = areaIdOf(verify(UIT, UUID.randomUUID()));
		jdbc.update("update user_areas set locality_name = 'Linh Trung' where id = ?::uuid", areaId);

		mvc.perform(get("/api/v1/users/" + userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.localityName").value("Linh Trung"))
			.andExpect(content().string(not(containsString("latitude"))))
			.andExpect(content().string(not(containsString("areas"))));
		jdbc.update("update user_areas set status = 'EXPIRED' where id = ?::uuid", areaId);
		mvc.perform(get("/api/v1/users/" + userId)).andExpect(jsonPath("$.localityName").doesNotExist());
	}

	private ResultActions verify(GeoPoint place, UUID key) throws Exception {
		return send(post(VERIFICATIONS), key, verifyBody(place, null, null));
	}

	private ResultActions renew(String areaId, GeoPoint place) throws Exception {
		return send(post(AREAS + "/" + areaId + "/renewals"), UUID.randomUUID(), fixJson(place, false) + "}");
	}

	private ResultActions send(MockHttpServletRequestBuilder request, UUID key, String body) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer)
			.header(HeaderNames.IDEMPOTENCY_KEY, key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions jsonRequest(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String verifyBody(GeoPoint place, Boolean mock, String replaceAreaId) {
		String replace = replaceAreaId == null ? "" : ", \"replaceAreaId\": \"" + replaceAreaId + "\"";
		return fixJson(place, Boolean.TRUE.equals(mock)) + ", \"appVersion\": \"1.0.0\"" + replace + "}";
	}

	private static String fixJson(GeoPoint place, boolean mock) {
		return String.format(Locale.ROOT,
				"{\"latitude\": %.7f, \"longitude\": %.7f, \"accuracyMeters\": 12.5, \"fixTime\": \"%s\", "
						+ "\"mockLocation\": %s, \"deviceSignals\": {\"emulator\": false, \"rooted\": null}",
				place.latitude(), place.longitude(), Instant.now().minusSeconds(5), mock);
	}

	private static String areaIdOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.area.id");
	}

	private GeoPoint centerOf(String areaId) {
		return areas.findById(UUID.fromString(areaId)).orElseThrow().center();
	}

	private void forgetPreviousFixes() {
		transaction.executeWithoutResult(status -> attempts.purgeLocationsOfUser(userId, Instant.now()));
	}

	private String bearerFor(UUID user) {
		return SecurityConstants.BEARER_PREFIX + tokenService.issueAccessToken(user, Roles.USER).value();
	}

}
