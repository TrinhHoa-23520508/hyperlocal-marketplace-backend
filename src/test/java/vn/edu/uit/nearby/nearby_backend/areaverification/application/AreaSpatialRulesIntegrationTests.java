package vn.edu.uit.nearby.nearby_backend.areaverification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.DISTRICT_1;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.UIT;
import static vn.edu.uit.nearby.nearby_backend.areaverification.application.AreaSubmissions.realFixAt;
import static vn.edu.uit.nearby.nearby_backend.areaverification.domain.AreaFixtures.northOf;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.nearby.nearby_backend.areaverification.FeedArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.areaverification.ListingPinRule;
import vn.edu.uit.nearby.nearby_backend.areaverification.application.VerificationOutcome.Verified;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.RangeLevel;
import vn.edu.uit.nearby.nearby_backend.areaverification.domain.UserArea;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.AreaVerificationAttemptRepository;
import vn.edu.uit.nearby.nearby_backend.areaverification.infrastructure.UserAreaRepository;
import vn.edu.uit.nearby.nearby_backend.identity.AppParameters;
import vn.edu.uit.nearby.nearby_backend.identity.application.consent.LocationConsentService;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;
import vn.edu.uit.nearby.nearby_backend.support.IntegrationTest;

/**
 * FR-AR-09 (BR-08), FR-LS-01 pin rule, FR-AR-03 and FR-DC-01 against real PostGIS
 * {@code ST_DWithin}.
 */
@IntegrationTest
class AreaSpatialRulesIntegrationTests {

	@Autowired
	InteractionRange interactionRange;

	@Autowired
	ListingPinRule listingPinRule;

	@Autowired
	FeedArea feedArea;

	@Autowired
	AreaVerificationService verification;

	@Autowired
	AreaManagementService management;

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

	private AreaTestAccounts accounts;

	private UUID userId;

	private UserArea area;

	@BeforeEach
	void verifyOneArea() {
		accounts = new AreaTestAccounts(users, locationConsents, appParameters);
		userId = accounts.eligibleUser();
		area = verifyAt(userId, UIT);
	}

	@Test
	@DisplayName("FR-AR-09 acceptance: with MEDIUM range a listing 3.9 km away can be contacted")
	void listingAtThreePointNineKilometersIsInRange() {
		assertThat(interactionRange.isWithinRange(userId, northOf(area.center(), 3900))).isTrue();
	}

	@Test
	@DisplayName("FR-AR-09 acceptance: with MEDIUM range a listing 4.1 km away cannot be contacted")
	void listingAtFourPointOneKilometersIsOutOfRange() {
		assertThat(interactionRange.isWithinRange(userId, northOf(area.center(), 4100))).isFalse();
	}

	@ParameterizedTest(name = "{0}: {1} m in range = {2}")
	@CsvSource({ "NEAR, 1900, true", "NEAR, 2100, false", "FAR, 5900, true", "FAR, 6100, false" })
	@DisplayName("FR-AR-08: each range level uses its own radius (2, 4, 6 km)")
	void eachRangeLevelUsesItsRadius(RangeLevel level, int meters, boolean expected) {
		management.selectActive(userId, area.getId(), level);

		assertThat(interactionRange.isWithinRange(userId, northOf(area.center(), meters))).isEqualTo(expected);
	}

	@Test
	@DisplayName("FR-AR-09: a listing near any valid area of the buyer counts, not only the active one")
	void anyValidAreaOfTheBuyerCounts() {
		UserArea second = verifyAt(userId, DISTRICT_1);

		assertThat(second.isActive()).isFalse();
		assertThat(interactionRange.isWithinRange(userId, northOf(second.center(), 3000))).isTrue();
	}

	@Test
	@DisplayName("FR-AR-03: an EXPIRING area still counts for the range")
	void expiringAreaStillCounts() {
		change(area -> area.markExpiring(area.getExpiresAt().minus(Duration.ofDays(2))));

		assertThat(interactionRange.isWithinRange(userId, northOf(area.center(), 1000))).isTrue();
	}

	@Test
	@DisplayName("FR-AR-03: an EXPIRED area no longer counts for the range")
	void expiredAreaDoesNotCount() {
		expireTheArea();

		assertThat(interactionRange.isWithinRange(userId, northOf(area.center(), 1000))).isFalse();
	}

	@Test
	@DisplayName("FR-AR-09: another user's areas give no range")
	void anotherUsersAreasDoNotCount() {
		UUID otherBuyer = accounts.eligibleUser();

		assertThat(interactionRange.isWithinRange(otherBuyer, northOf(area.center(), 100))).isFalse();
	}

	@Test
	@DisplayName("FR-CH-01 / FR-TX-01: out of range answers 403 OUT_OF_RANGE")
	void outOfRangeIsRefusedWithItsCode() {
		GeoPoint farListing = northOf(area.center(), 10_000);

		assertThatThrownBy(() -> interactionRange.requireWithinRange(userId, farListing))
			.isInstanceOf(OperationNotAllowedException.class)
			.hasFieldOrPropertyWithValue("code", "OUT_OF_RANGE");
		assertThatCode(() -> interactionRange.requireWithinRange(userId, area.center())).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-LS-01: a pin 1,499 m from the area center is inside")
	void pinAtOneThousandFourHundredNinetyNineMetersIsInside() {
		assertThat(listingPinRule.isPinInsideArea(userId, area.getId(), northOf(area.center(), 1499))).isTrue();
	}

	@Test
	@DisplayName("FR-LS-01: a pin 1,501 m from the area center is outside")
	void pinAtOneThousandFiveHundredOneMetersIsOutside() {
		assertThat(listingPinRule.isPinInsideArea(userId, area.getId(), northOf(area.center(), 1501))).isFalse();
	}

	@Test
	@DisplayName("FR-LS-01: a pin in another user's area is refused")
	void pinInAnotherUsersAreaIsRefused() {
		UUID otherSeller = accounts.eligibleUser();

		assertThat(listingPinRule.isPinInsideArea(otherSeller, area.getId(), area.center())).isFalse();
	}

	@Test
	@DisplayName("FR-LS-01: a pin in an expired area is refused")
	void pinInAnExpiredAreaIsRefused() {
		expireTheArea();

		assertThat(listingPinRule.isPinInsideArea(userId, area.getId(), area.center())).isFalse();
	}

	@Test
	@DisplayName("FR-LS-01: an invalid area or a pin outside it answers 422 PIN_OUTSIDE_AREA")
	void pinOutsideIsRefusedWithItsCode() {
		assertThatThrownBy(() -> listingPinRule.requirePinInsideArea(userId, UUID.randomUUID(), area.center()))
			.isInstanceOf(UnprocessableRequestException.class)
			.hasFieldOrPropertyWithValue("code", "PIN_OUTSIDE_AREA");
		assertThatCode(() -> listingPinRule.requirePinInsideArea(userId, area.getId(), area.center()))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-AR-03: a listing's area takes new contacts while VERIFIED or EXPIRING")
	void validListingAreaTakesNewContacts() {
		assertThat(interactionRange.allowsNewContacts(area.getId())).isTrue();

		change(area -> area.markExpiring(area.getExpiresAt().minus(Duration.ofDays(1))));

		assertThat(interactionRange.allowsNewContacts(area.getId())).isTrue();
	}

	@Test
	@DisplayName("FR-AR-03: an EXPIRED, REMOVED or unknown listing area takes no new contacts")
	void invalidListingAreaTakesNoNewContacts() {
		expireTheArea();
		UserArea removed = verifyAt(accounts.eligibleUser(), DISTRICT_1);
		management.remove(removed.getUserId(), removed.getId());

		assertThat(interactionRange.allowsNewContacts(area.getId())).isFalse();
		assertThat(interactionRange.allowsNewContacts(removed.getId())).isFalse();
		assertThat(interactionRange.allowsNewContacts(UUID.randomUUID())).isFalse();
	}

	@Test
	@DisplayName("FR-DC-01: the feed is centered on the active area with its MEDIUM radius")
	void feedUsesTheActiveArea() {
		assertThat(feedArea.activeFeedArea(userId)).hasValueSatisfying(feed -> {
			assertThat(feed.areaId()).isEqualTo(area.getId());
			assertThat(feed.center()).isEqualTo(area.center());
			assertThat(feed.radiusMeters()).isEqualTo(4000);
		});
	}

	@Test
	@DisplayName("FR-AR-08: choosing another area and range moves the feed")
	void feedFollowsTheChosenAreaAndRange() {
		UserArea second = verifyAt(userId, DISTRICT_1);

		management.selectActive(userId, second.getId(), RangeLevel.FAR);

		assertThat(feedArea.activeFeedArea(userId)).hasValueSatisfying(feed -> {
			assertThat(feed.areaId()).isEqualTo(second.getId());
			assertThat(feed.radiusMeters()).isEqualTo(6000);
		});
	}

	@Test
	@DisplayName("FR-DC-01: an expired active area still centers the feed, since browsing needs no valid area")
	void expiredActiveAreaStillCentersTheFeed() {
		expireTheArea();

		assertThat(feedArea.activeFeedArea(userId))
			.hasValueSatisfying(feed -> assertThat(feed.areaId()).isEqualTo(area.getId()));
	}

	@Test
	@DisplayName("FR-DC-04: without an active area there is no default feed center")
	void noAreaMeansNoFeedCenter() {
		management.remove(userId, area.getId());

		assertThat(feedArea.activeFeedArea(userId)).isEmpty();
		assertThat(feedArea.activeFeedArea(accounts.eligibleUser())).isEmpty();
	}

	private UserArea verifyAt(UUID owner, GeoPoint place) {
		transaction.executeWithoutResult(status -> attempts.purgeLocationsOfUser(owner, Instant.now()));
		UUID areaId = ((Verified) verification.verify(new VerifyAreaCommand(owner, realFixAt(place), null, null)))
			.area()
			.id();
		return areas.findById(areaId).orElseThrow();
	}

	private void expireTheArea() {
		change(area -> {
			area.markExpiring(area.getExpiresAt().minus(Duration.ofDays(1)));
			area.expire(area.getExpiresAt());
		});
	}

	private void change(Consumer<UserArea> change) {
		transaction.executeWithoutResult(status -> change.accept(areas.findById(area.getId()).orElseThrow()));
	}

}
