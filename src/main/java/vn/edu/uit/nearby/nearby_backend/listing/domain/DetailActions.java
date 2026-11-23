package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The buttons of A10 (FR-DC-08): each is enabled, or disabled with the reason and the
 * unlock action the app shows. Chat follows FR-CH-01 (ACTIVE or RESERVED listing), Offer
 * follows FR-TX-01 (ACTIVE listing); both need the trading gate (FR-ID-20), a seller area
 * that still takes new contacts (FR-AR-03) and the interaction range (FR-AR-09). Guests
 * are sent to log in; on their own listing sellers get edit actions instead.
 */
public record DetailActions(boolean ownListing, ActionState chat, ActionState offer, ActionState save,
		ActionState report) {

	public static DetailActions forGuest() {
		ActionState login = ActionState.disabled(DisabledReason.LOGIN_REQUIRED);
		return new DetailActions(false, login, login, login, login);
	}

	public static DetailActions forOwner() {
		ActionState own = ActionState.disabled(DisabledReason.OWN_LISTING);
		return new DetailActions(true, own, own, ActionState.ENABLED, own);
	}

	public static DetailActions forBuyer(BuyerContext context) {
		return new DetailActions(false,
				contactAction(context,
						context.listingStatus() == ListingStatus.ACTIVE
								|| context.listingStatus() == ListingStatus.RESERVED),
				contactAction(context, context.listingStatus() == ListingStatus.ACTIVE), ActionState.ENABLED,
				ActionState.ENABLED);
	}

	private static ActionState contactAction(BuyerContext context, boolean listingTakesContacts) {
		if (!listingTakesContacts) {
			return ActionState.disabled(DisabledReason.LISTING_NOT_AVAILABLE);
		}
		if (!context.gateFailures().isEmpty()) {
			return new ActionState(false, DisabledReason.TRADING_GATE_FAILED, context.gateFailures());
		}
		if (!context.sellerAreaTakesContacts()) {
			return ActionState.disabled(DisabledReason.LISTING_AREA_EXPIRED);
		}
		if (!context.withinRange()) {
			return ActionState.disabled(DisabledReason.OUT_OF_RANGE);
		}
		return ActionState.ENABLED;
	}

	/**
	 * What decides a signed-in buyer's buttons.
	 *
	 * @param gateFailures the unmet trading gate conditions, by name; empty when open
	 * @param sellerAreaTakesContacts false once the listing's area is EXPIRED or REMOVED
	 * @param withinRange the listing's public point is within one of the buyer's areas
	 */
	public record BuyerContext(ListingStatus listingStatus, List<String> gateFailures, boolean sellerAreaTakesContacts,
			boolean withinRange) {

		public BuyerContext {
			gateFailures = List.copyOf(gateFailures);
		}

	}

	/**
	 * @param gateFailures the trading gate conditions to fix, only for
	 * {@link DisabledReason#TRADING_GATE_FAILED}
	 */
	public record ActionState(boolean enabled, @Nullable DisabledReason reason, List<String> gateFailures) {

		public static final ActionState ENABLED = new ActionState(true, null, List.of());

		public ActionState {
			gateFailures = List.copyOf(gateFailures);
		}

		static ActionState disabled(DisabledReason reason) {
			return new ActionState(false, reason, List.of());
		}

	}

	public enum DisabledReason {

		LOGIN_REQUIRED, OWN_LISTING, LISTING_NOT_AVAILABLE, TRADING_GATE_FAILED, LISTING_AREA_EXPIRED, OUT_OF_RANGE

	}

}
