package vn.edu.uit.nearby.nearby_backend.listing.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

/**
 * Listing state machine, the R1 rows of SRS table {@code tab:sm-listing} (FR-LS-13). The
 * R2 screening rows (flag, approve, reject) are not built; PENDING_REVIEW exists only as
 * a source state. Every move not listed is a {@code 409 INVALID_LISTING_STATE}.
 */
public enum ListingStatus {

	PENDING_REVIEW, ACTIVE, RESERVED, SOLD, HIDDEN, DELETED, REMOVED;

	private static final Set<ListingStatus> OPEN = EnumSet.of(ACTIVE, RESERVED, PENDING_REVIEW);

	private static final Set<ListingStatus> FINAL = EnumSet.of(DELETED, REMOVED);

	private static final Set<ListingStatus> NOT_FINAL = EnumSet.of(PENDING_REVIEW, ACTIVE, RESERVED, SOLD, HIDDEN);

	private static final Set<ListingStatus> NOT_EDITABLE = EnumSet.of(SOLD, DELETED, REMOVED);

	public ListingStatus afterOwnerHide() {
		return moveTo(HIDDEN, EnumSet.of(ACTIVE));
	}

	public ListingStatus afterOwnerUnhide() {
		return moveTo(ACTIVE, EnumSet.of(HIDDEN));
	}

	public ListingStatus afterReservation() {
		return moveTo(RESERVED, EnumSet.of(ACTIVE));
	}

	/**
	 * The holding transaction was CANCELLED or EXPIRED.
	 */
	public ListingStatus afterReservationReleased() {
		return moveTo(ACTIVE, EnumSet.of(RESERVED));
	}

	/**
	 * The holding transaction was COMPLETED, or the seller marked the listing sold to the
	 * buyer of that ACCEPTED transaction.
	 */
	public ListingStatus afterReservedSale() {
		return moveTo(SOLD, EnumSet.of(RESERVED));
	}

	/**
	 * Sold to a chat partner ({@code SaleMarked}) or outside the app (FR-LS-09).
	 */
	public ListingStatus afterDirectSale() {
		return moveTo(SOLD, EnumSet.of(ACTIVE));
	}

	/**
	 * The buyer denied a marked sale ({@code SaleDenied}).
	 */
	public ListingStatus afterSaleDenied() {
		return moveTo(ACTIVE, EnumSet.of(SOLD));
	}

	public ListingStatus afterOwnerDeletion() {
		return moveTo(DELETED, EnumSet.of(ACTIVE, HIDDEN, PENDING_REVIEW));
	}

	/**
	 * "Mọi trạng thái chưa kết thúc": every state but DELETED and REMOVED. SOLD stays
	 * removable because sold listings remain searchable (FR-DC-02).
	 */
	public ListingStatus afterModeratorRemoval() {
		return moveTo(REMOVED, NOT_FINAL);
	}

	/**
	 * UC-SY-12 anonymisation of the seller's account: "tin chuyển DELETED" (SRS table
	 * {@code tab:retention}), whatever the state the listing was left in.
	 */
	public ListingStatus afterAccountAnonymised() {
		return moveTo(DELETED, NOT_FINAL);
	}

	public ListingStatus afterAreaRemoved() {
		return moveTo(HIDDEN, EnumSet.of(ACTIVE, HIDDEN));
	}

	public ListingStatus afterSanction() {
		return moveTo(HIDDEN, EnumSet.of(ACTIVE));
	}

	public ListingStatus afterSanctionLifted() {
		return moveTo(ACTIVE, EnumSet.of(HIDDEN));
	}

	/**
	 * ACTIVE, RESERVED and PENDING_REVIEW: the states counted by
	 * {@code listing.max_open_per_user} (FR-LS-04).
	 */
	public static Set<ListingStatus> openStatuses() {
		return EnumSet.copyOf(OPEN);
	}

	/**
	 * Counts toward {@code listing.max_open_per_user} (FR-LS-04).
	 */
	public boolean isOpen() {
		return OPEN.contains(this);
	}

	/**
	 * FR-LS-06: SOLD, REMOVED and DELETED listings cannot be edited.
	 */
	public boolean isEditable() {
		return !NOT_EDITABLE.contains(this);
	}

	/**
	 * What anyone may read (FR-DC-02, FR-DC-08): ACTIVE and RESERVED listings, and SOLD
	 * ones that searches include on request. HIDDEN and PENDING_REVIEW listings are only
	 * their owner's.
	 */
	public boolean isPubliclyVisible() {
		return this == ACTIVE || this == RESERVED || this == SOLD;
	}

	/**
	 * DELETED and REMOVED listings are gone for the public (FR-LS-08, "404 công khai").
	 */
	public boolean isGone() {
		return FINAL.contains(this);
	}

	ConflictException invalidMove(String action) {
		return new ConflictException(ErrorCodes.INVALID_LISTING_STATE,
				"A listing that is " + this + " cannot be " + action + ".", Map.of("status", name()));
	}

	private ListingStatus moveTo(ListingStatus target, Set<ListingStatus> allowedSources) {
		if (!allowedSources.contains(this)) {
			throw invalidMove("moved to " + target);
		}
		return target;
	}

}
