package vn.edu.uit.nearby.nearby_backend.listing.web;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaTestAccounts;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaVerificationService;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.support.TestImages;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingTestData;
import vn.edu.uit.nearby.nearby_backend.listing.application.ListingTestData.Seller;
import vn.edu.uit.nearby.nearby_backend.listing.infrastructure.ListingImageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.HeaderNames;
import vn.edu.uit.nearby.nearby_backend.shared.constant.Roles;
import vn.edu.uit.nearby.nearby_backend.shared.constant.SecurityConstants;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.shared.security.TokenService;
import vn.edu.uit.nearby.nearby_backend.support.InMemoryImageStorage;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * The listing and discovery endpoints (A06-A15): status codes, RFC 9457 bodies with their
 * codes, the guest/user split, and no exact pin in any response but the owner's
 * (FR-LS-03, NFR-15).
 */
@IntegrationTest
class ListingApiIntegrationTests {

	private static final String LISTINGS = "/api/v1/listings";

	@Autowired
	MockMvc mvc;

	@Autowired
	TokenService tokenService;

	@Autowired
	InMemoryImageStorage storage;

	@Autowired
	ListingImageRepository images;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	UserRepository users;

	@Autowired
	LocationConsentService locationConsents;

	@Autowired
	AppParameters appParameters;

	@Autowired
	AreaVerificationService areaVerification;

	private ListingTestData data;

	private Seller seller;

	private String sellerToken;

	@BeforeEach
	void createSeller() {
		data = new ListingTestData(new AreaTestAccounts(users, locationConsents, appParameters), areaVerification,
				images, transaction);
		seller = data.aSeller();
		sellerToken = bearerFor(seller.id());
	}

	@Nested
	class Security {

		@Test
		@DisplayName("FR-LS-01, FR-LS-11: posting and the seller's own reads need an access token")
		void sellerEndpointsNeedAToken() throws Exception {
			mvc.perform(post(LISTINGS).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
			mvc.perform(get(LISTINGS + "/me")).andExpect(status().isUnauthorized());
			mvc.perform(get(LISTINGS + "/posting-eligibility")).andExpect(status().isUnauthorized());
			mvc.perform(get(LISTINGS + "/images/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
			mvc.perform(get("/api/v1/users/me/favorites")).andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("FR-DC-01..08: guests may browse the feed, search, map, categories and details")
		void guestsMayBrowse() throws Exception {
			mvc.perform(get("/api/v1/feed").param("lat", "10.87").param("lon", "106.8031")).andExpect(status().isOk());
			mvc.perform(get("/api/v1/search").param("lat", "10.87").param("lon", "106.8031").param("q", "ghe"))
				.andExpect(status().isOk());
			mvc.perform(get("/api/v1/categories")).andExpect(status().isOk()).andExpect(jsonPath("$[0].code").exists());
			mvc.perform(get("/api/v1/users/" + seller.id() + "/listings")).andExpect(status().isOk());
		}

	}

	@Nested
	class Posting {

		@Test
		@DisplayName("UC-LS-01: upload, process, post; the owner gets the pin and a Location")
		void postsAListingEndToEnd() throws Exception {
			UUID imageId = uploadProcessedImage();
			UUID key = UUID.randomUUID();

			createListing(key, listingBody(List.of(imageId), "Áo khoác da nam")).andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, containsString(LISTINGS + "/")))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.pin.latitude").value(seller.areaCenter().latitude()))
				.andExpect(jsonPath("$.publicPoint.latitude", notNullValue()))
				.andExpect(jsonPath("$.images[0].id").value(imageId.toString()))
				.andExpect(jsonPath("$.version").value(0));
		}

		@Test
		@DisplayName("UC-LS-01 A4: the same Idempotency-Key and body returns the original 201")
		void replaysTheSameRequest() throws Exception {
			String body = listingBody(List.of(uploadProcessedImage()), "Áo khoác da nam");
			UUID key = UUID.randomUUID();
			String first = createListing(key, body).andReturn().getResponse().getContentAsString();

			createListing(key, body).andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "true"))
				.andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")));
		}

		@Test
		@DisplayName("FR-LS-04: creating without an Idempotency-Key is a 400 IDEMPOTENCY_KEY_REQUIRED")
		void requiresAnIdempotencyKey() throws Exception {
			mvc.perform(post(LISTINGS).header(HttpHeaders.AUTHORIZATION, sellerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(listingBody(List.of(UUID.randomUUID()), "Áo khoác da nam")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
		}

		@Test
		@DisplayName("FR-LS-01 (DEC-LS-1): a 3-character title is a 400 VALIDATION_FAILED on title")
		void refusesAShortTitle() throws Exception {
			createListing(UUID.randomUUID(), listingBody(List.of(uploadProcessedImage()), "Ghế"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("title")));
		}

		@Test
		@DisplayName("FR-LS-01: a seller without an area gets 403 TRADING_GATE_FAILED with reasons")
		void refusesASellerWhoFailsTheGate() throws Exception {
			UUID noArea = data.aBuyerWithoutArea();

			mvc.perform(post(LISTINGS).header(HttpHeaders.AUTHORIZATION, bearerFor(noArea))
				.header(HeaderNames.IDEMPOTENCY_KEY, UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(listingBody(List.of(UUID.randomUUID()), "Áo khoác da nam")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("TRADING_GATE_FAILED"))
				.andExpect(jsonPath("$.reasons", hasItem("NO_VALID_AREA")));
		}

		@Test
		@DisplayName("UC-LS-01 step 1: posting eligibility shows the gate and the quota")
		void showsPostingEligibility() throws Exception {
			mvc.perform(get(LISTINGS + "/posting-eligibility").header(HttpHeaders.AUTHORIZATION, sellerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canPost").value(true))
				.andExpect(jsonPath("$.remainingToday").value(3))
				.andExpect(jsonPath("$.maxOpenListings").value(30));
		}

		@Test
		@DisplayName("FR-LS-02: another user's image is a 404 IMAGE_NOT_FOUND")
		void anotherUsersImageIsNotFound() throws Exception {
			UUID imageId = uploadProcessedImage();

			mvc.perform(get(LISTINGS + "/images/" + imageId).header(HttpHeaders.AUTHORIZATION,
					bearerFor(data.aBuyerWithoutArea())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
		}

		@Test
		@DisplayName("FR-LS-02: a GIF is a 400 INVALID_IMAGE")
		void refusesAGif() throws Exception {
			mvc.perform(post(LISTINGS + "/images/upload-url").header(HttpHeaders.AUTHORIZATION, sellerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contentType\": \"image/gif\", \"sizeBytes\": 1000}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
		}

	}

	@Nested
	class Managing {

		private String listingId;

		@BeforeEach
		void postOne() throws Exception {
			String created = createListing(UUID.randomUUID(),
					listingBody(List.of(uploadProcessedImage()), "Áo khoác da nam"))
				.andReturn()
				.getResponse()
				.getContentAsString();
			listingId = JsonPath.read(created, "$.id");
		}

		@Test
		@DisplayName("FR-LS-07: hide then unhide with the current version")
		void hidesAndUnhides() throws Exception {
			send(post(LISTINGS + "/" + listingId + "/hide"), "{\"version\": 0}").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("HIDDEN"))
				.andExpect(jsonPath("$.hiddenReason").value("OWNER"));
			send(post(LISTINGS + "/" + listingId + "/unhide"), "{\"version\": 1}").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		}

		@Test
		@DisplayName("FR-LS-13: a stale version is a 409 CONCURRENT_MODIFICATION")
		void refusesAStaleVersion() throws Exception {
			send(post(LISTINGS + "/" + listingId + "/hide"), "{\"version\": 7}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		}

		@Test
		@DisplayName("FR-ID-18: another seller editing the listing gets 404 LISTING_NOT_FOUND")
		void anotherSellerGetsNotFound() throws Exception {
			String intruder = bearerFor(data.aSeller().id());

			mvc.perform(put(LISTINGS + "/" + listingId).header(HttpHeaders.AUTHORIZATION, intruder)
				.contentType(MediaType.APPLICATION_JSON)
				.content(editBody(0, List.of(UUID.randomUUID()))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
		}

		@Test
		@DisplayName("FR-LS-10: a second bump within 48 h is a 429 BUMP_TOO_SOON with Retry-After")
		void bumpsOncePerCooldown() throws Exception {
			send(post(LISTINGS + "/" + listingId + "/bump"), "").andExpect(status().isOk())
				.andExpect(jsonPath("$.bumpCount").value(1));
			send(post(LISTINGS + "/" + listingId + "/bump"), "").andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("BUMP_TOO_SOON"))
				.andExpect(jsonPath("$.nextAllowedAt", notNullValue()))
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		}

		@Test
		@DisplayName("FR-LS-09 (b): sold outside the app needs an Idempotency-Key and makes the listing SOLD")
		void marksSoldOutside() throws Exception {
			mvc.perform(
					post(LISTINGS + "/" + listingId + "/sold-outside").header(HttpHeaders.AUTHORIZATION, sellerToken)
						.header(HeaderNames.IDEMPOTENCY_KEY, UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\": 0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SOLD"));
		}

		@Test
		@DisplayName("FR-LS-08: a deleted listing is 404 for everyone")
		void deletesTheListing() throws Exception {
			send(delete(LISTINGS + "/" + listingId), "").andExpect(status().isNoContent());

			mvc.perform(get(LISTINGS + "/" + listingId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
		}

		@Test
		@DisplayName("FR-LS-11: my listings by state with counters; an unknown state is 400 VALIDATION_FAILED")
		void listsMyListings() throws Exception {
			mvc.perform(get(LISTINGS + "/me").param("status", "ACTIVE").header(HttpHeaders.AUTHORIZATION, sellerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.countsByStatus.ACTIVE").value(1))
				.andExpect(jsonPath("$.items[0].id").value(listingId));
			mvc.perform(get(LISTINGS + "/me").param("status", "BOGUS").header(HttpHeaders.AUTHORIZATION, sellerToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}

		@Test
		@DisplayName("FR-LS-12: save, list and unsave a listing")
		void savesAndUnsaves() throws Exception {
			String buyer = bearerFor(data.aBuyerWithoutArea());

			mvc.perform(put(LISTINGS + "/" + listingId + "/favorite").header(HttpHeaders.AUTHORIZATION, buyer))
				.andExpect(status().isNoContent());
			mvc.perform(get("/api/v1/users/me/favorites").header(HttpHeaders.AUTHORIZATION, buyer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].listingId").value(listingId))
				.andExpect(jsonPath("$.items[0].available").value(true));
			mvc.perform(delete(LISTINGS + "/" + listingId + "/favorite").header(HttpHeaders.AUTHORIZATION, buyer))
				.andExpect(status().isNoContent());
		}

		@Test
		@DisplayName("FR-LS-03/NFR-15: guests never get the pin; the owner does")
		void onlyTheOwnerSeesThePin() throws Exception {
			mvc.perform(get(LISTINGS + "/" + listingId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pin").doesNotExist())
				.andExpect(jsonPath("$.publicPoint.latitude", notNullValue()))
				.andExpect(jsonPath("$.actions.chat.reason").value("LOGIN_REQUIRED"));
			mvc.perform(get(LISTINGS + "/" + listingId).header(HttpHeaders.AUTHORIZATION, sellerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pin.latitude").value(seller.areaCenter().latitude()))
				.andExpect(jsonPath("$.actions.ownListing").value(true));
		}

		@Test
		@DisplayName("FR-DC-08: the distance is measured from the given point, rounded to 100 m")
		void measuresTheDistanceFromTheGivenPoint() throws Exception {
			GeoPoint center = seller.areaCenter();

			mvc.perform(get(LISTINGS + "/" + listingId).param("lat", String.valueOf(center.latitude() + 0.01))
				.param("lon", String.valueOf(center.longitude())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.distanceMeters").value(1100));
			mvc.perform(get(LISTINGS + "/" + listingId).param("lat", "95").param("lon", "106"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}

		@Test
		@DisplayName("FR-DC-05/NFR-15: feed and search cards carry no location at all")
		void cardsCarryNoLocation() throws Exception {
			GeoPoint center = seller.areaCenter();

			for (String path : List.of("/api/v1/feed", "/api/v1/search")) {
				mvc.perform(get(path).param("lat", String.valueOf(center.latitude()))
					.param("lon", String.valueOf(center.longitude())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items[*].id", hasItem(listingId)))
					.andExpect(content().string(not(containsString("\"pin\""))))
					.andExpect(content().string(not(containsString("latitude"))));
			}
		}

	}

	@Nested
	class DiscoveryErrors {

		@Test
		@DisplayName("FR-DC-03: an unknown sort is a 400 VALIDATION_FAILED on sort")
		void refusesAnUnknownSort() throws Exception {
			mvc.perform(get("/api/v1/search").param("lat", "10.87").param("lon", "106.8").param("sort", "CHEAPEST"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("sort")));
		}

		@Test
		@DisplayName("FR-DC-02 E1: minPrice above maxPrice is a 400 VALIDATION_FAILED")
		void refusesContradictingPrices() throws Exception {
			mvc.perform(get("/api/v1/search").param("lat", "10.87")
				.param("lon", "106.8")
				.param("minPrice", "500")
				.param("maxPrice", "100"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", hasItem("minPrice")));
		}

		@Test
		@DisplayName("FR-DC-04: a guest without a center is asked for one")
		void guestsNeedACenter() throws Exception {
			mvc.perform(get("/api/v1/feed"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", hasItem("center")));
		}

		@Test
		@DisplayName("FR-DC-04: lat without lon is a 400 VALIDATION_FAILED on lon")
		void refusesHalfACenter() throws Exception {
			mvc.perform(get("/api/v1/feed").param("lat", "10.87"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", hasItem("lon")));
		}

		@Test
		@DisplayName("FR-DC-01: a broken cursor is a 400 INVALID_CURSOR")
		void refusesABrokenCursor() throws Exception {
			mvc.perform(get("/api/v1/feed").param("lat", "10.87").param("lon", "106.8").param("cursor", "garbage"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
		}

		@Test
		@DisplayName("FR-DC-07: a viewport whose south is above its north is a 400 VALIDATION_FAILED")
		void refusesAnUpsideDownViewport() throws Exception {
			mvc.perform(get("/api/v1/map/listings").param("south", "11")
				.param("west", "106")
				.param("north", "10")
				.param("east", "107")
				.param("zoom", "12"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}

		@Test
		void returnsMapMarkers() throws Exception {
			mvc.perform(get("/api/v1/map/listings").param("south", "10.8")
				.param("west", "106.7")
				.param("north", "10.9")
				.param("east", "106.9")
				.param("zoom", "14"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points").isArray())
				.andExpect(jsonPath("$.clusters").isArray());
		}

	}

	private UUID uploadProcessedImage() throws Exception {
		String ticket = send(post(LISTINGS + "/images/upload-url"),
				"{\"contentType\": \"image/jpeg\", \"sizeBytes\": 50000}")
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID imageId = UUID.fromString(JsonPath.read(ticket, "$.imageId"));
		storage.simulateUpload(images.findById(imageId).orElseThrow().getOriginalKey(), "image/jpeg",
				TestImages.jpeg(800, 600));
		send(post(LISTINGS + "/images/" + imageId + "/complete"), "").andExpect(status().isAccepted());
		await().atMost(Duration.ofSeconds(20))
			.until(() -> JsonPath
				.read(send(get(LISTINGS + "/images/" + imageId), "").andReturn().getResponse().getContentAsString(),
						"$.status")
				.equals("READY"));
		return imageId;
	}

	private ResultActions createListing(UUID idempotencyKey, String body) throws Exception {
		return mvc.perform(post(LISTINGS).header(HttpHeaders.AUTHORIZATION, sellerToken)
			.header(HeaderNames.IDEMPOTENCY_KEY, idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		request.header(HttpHeaders.AUTHORIZATION, sellerToken);
		if (!body.isEmpty()) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mvc.perform(request);
	}

	private String listingBody(List<UUID> imageIds, String title) {
		return """
				{"areaId": "%s", "title": "%s", "description": "Còn mới", "condition": "LIKE_NEW", "priceVnd": 150000,
				 "categoryId": 1, "latitude": %s, "longitude": %s, "imageIds": [%s]}""".formatted(seller.areaId(),
				title, seller.areaCenter().latitude(), seller.areaCenter().longitude(), quoted(imageIds));
	}

	private String editBody(long version, List<UUID> imageIds) {
		return """
				{"version": %d, "title": "Áo khoác da nam", "condition": "USED", "priceVnd": 1, "categoryId": 1,
				 "latitude": %s, "longitude": %s, "imageIds": [%s]}""".formatted(version,
				seller.areaCenter().latitude(), seller.areaCenter().longitude(), quoted(imageIds));
	}

	private static String quoted(List<UUID> ids) {
		return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
	}

	private String bearerFor(UUID user) {
		return SecurityConstants.BEARER_PREFIX + tokenService.issueAccessToken(user, Roles.USER).value();
	}

}
