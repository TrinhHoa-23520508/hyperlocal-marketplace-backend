package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions.ActionState;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions.BuyerContext;
import vn.edu.uit.nearby.nearby_backend.listing.domain.DetailActions.DisabledReason;

/**
 * FR-DC-08: every disabled button carries its reason.
 */
class DetailActionsTests {

	private static final BuyerContext ALL_CLEAR = new BuyerContext(ListingStatus.ACTIVE, List.of(), true, true);

	@Test
	@DisplayName("FR-DC-08: a buyer who meets every rule can chat, offer, save and report")
	void enablesEverythingForAnEligibleBuyer() {
		DetailActions actions = DetailActions.forBuyer(ALL_CLEAR);

		assertThat(List.of(actions.chat(), actions.offer(), actions.save(), actions.report()))
			.containsOnly(ActionState.ENABLED);
		assertThat(actions.ownListing()).isFalse();
	}

	@Test
	@DisplayName("FR-DC-08: a guest pressing an action goes to login")
	void sendsGuestsToLogin() {
		DetailActions actions = DetailActions.forGuest();

		assertThat(List.of(actions.chat(), actions.offer(), actions.save(), actions.report()))
			.extracting(ActionState::reason)
			.containsOnly(DisabledReason.LOGIN_REQUIRED);
	}

	@Test
	@DisplayName("FR-DC-08: on their own listing the seller gets edit actions instead of Chat/Offer")
	void showsEditActionsOnTheSellersOwnListing() {
		DetailActions actions = DetailActions.forOwner();

		assertThat(actions.ownListing()).isTrue();
		assertThat(actions.chat().reason()).isEqualTo(DisabledReason.OWN_LISTING);
		assertThat(actions.offer().reason()).isEqualTo(DisabledReason.OWN_LISTING);
	}

	@Test
	@DisplayName("FR-CH-01/FR-TX-01: a RESERVED listing takes chats but not offers")
	void reservedListingTakesChatsOnly() {
		DetailActions actions = DetailActions.forBuyer(new BuyerContext(ListingStatus.RESERVED, List.of(), true, true));

		assertThat(actions.chat().enabled()).isTrue();
		assertThat(actions.offer().reason()).isEqualTo(DisabledReason.LISTING_NOT_AVAILABLE);
	}

	@ParameterizedTest
	@EnumSource(value = ListingStatus.class, names = { "SOLD", "HIDDEN" })
	void soldOrHiddenListingsTakeNoContacts(ListingStatus status) {
		DetailActions actions = DetailActions.forBuyer(new BuyerContext(status, List.of(), true, true));

		assertThat(actions.chat().reason()).isEqualTo(DisabledReason.LISTING_NOT_AVAILABLE);
		assertThat(actions.offer().reason()).isEqualTo(DisabledReason.LISTING_NOT_AVAILABLE);
		assertThat(actions.save().enabled()).isTrue();
	}

	@Test
	@DisplayName("FR-ID-20: a closed gate disables Chat and Offer with the conditions to fix")
	void namesTheGateFailures() {
		DetailActions actions = DetailActions
			.forBuyer(new BuyerContext(ListingStatus.ACTIVE, List.of("NO_VALID_AREA"), true, true));

		assertThat(actions.offer().reason()).isEqualTo(DisabledReason.TRADING_GATE_FAILED);
		assertThat(actions.offer().gateFailures()).containsExactly("NO_VALID_AREA");
	}

	@Test
	@DisplayName("FR-AR-03: a listing whose area expired takes no new chats or offers")
	void blocksContactsOnAnExpiredSellerArea() {
		DetailActions actions = DetailActions.forBuyer(new BuyerContext(ListingStatus.ACTIVE, List.of(), false, true));

		assertThat(actions.chat().reason()).isEqualTo(DisabledReason.LISTING_AREA_EXPIRED);
		assertThat(actions.offer().reason()).isEqualTo(DisabledReason.LISTING_AREA_EXPIRED);
	}

	@Test
	@DisplayName("FR-AR-09: a listing outside the buyer's range is OUT_OF_RANGE")
	void blocksContactsOutOfRange() {
		DetailActions actions = DetailActions.forBuyer(new BuyerContext(ListingStatus.ACTIVE, List.of(), true, false));

		assertThat(actions.chat().reason()).isEqualTo(DisabledReason.OUT_OF_RANGE);
		assertThat(actions.report().enabled()).isTrue();
	}

}
