package vn.edu.uit.nearby.nearby_backend.listing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;

/**
 * FR-LS-13: every R1 row of SRS table {@code tab:sm-listing}, plus the UC-SY-12
 * anonymisation of {@code tab:retention}, is allowed; every other (status, event) pair is
 * refused.
 */
class ListingStatusTests {

	@ParameterizedTest(name = "{0} --{1}--> {2}")
	@CsvSource({ "ACTIVE, OWNER_HIDE, HIDDEN", "HIDDEN, OWNER_UNHIDE, ACTIVE", "ACTIVE, RESERVE, RESERVED",
			"RESERVED, RELEASE, ACTIVE", "RESERVED, RESERVED_SALE, SOLD", "ACTIVE, DIRECT_SALE, SOLD",
			"SOLD, SALE_DENIED, ACTIVE", "ACTIVE, OWNER_DELETE, DELETED", "HIDDEN, OWNER_DELETE, DELETED",
			"PENDING_REVIEW, OWNER_DELETE, DELETED", "PENDING_REVIEW, MODERATOR_REMOVE, REMOVED",
			"ACTIVE, MODERATOR_REMOVE, REMOVED", "RESERVED, MODERATOR_REMOVE, REMOVED",
			"SOLD, MODERATOR_REMOVE, REMOVED", "HIDDEN, MODERATOR_REMOVE, REMOVED", "ACTIVE, AREA_REMOVED, HIDDEN",
			"HIDDEN, AREA_REMOVED, HIDDEN", "ACTIVE, SANCTION, HIDDEN", "HIDDEN, SANCTION_LIFTED, ACTIVE",
			"PENDING_REVIEW, ACCOUNT_ANONYMISED, DELETED", "ACTIVE, ACCOUNT_ANONYMISED, DELETED",
			"RESERVED, ACCOUNT_ANONYMISED, DELETED", "SOLD, ACCOUNT_ANONYMISED, DELETED",
			"HIDDEN, ACCOUNT_ANONYMISED, DELETED" })
	@DisplayName("FR-LS-13: allows every R1 transition of tab:sm-listing")
	void allowsEveryTransitionOfTheSrsTable(ListingStatus from, Event event, ListingStatus expected) {
		assertThat(event.apply(from)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} --{1}--> refused")
	@MethodSource("transitionsNotInTheTable")
	@DisplayName("FR-LS-13: refuses every transition not in tab:sm-listing")
	void refusesEveryOtherTransition(ListingStatus from, Event event) {
		assertThatThrownBy(() -> event.apply(from)).isInstanceOf(ConflictException.class)
			.hasFieldOrPropertyWithValue("code", "INVALID_LISTING_STATE")
			.extracting(ex -> ((ConflictException) ex).getProperties())
			.isEqualTo(Map.of("status", from.name()));
	}

	@ParameterizedTest
	@CsvSource({ "PENDING_REVIEW, true", "ACTIVE, true", "RESERVED, true", "SOLD, false", "HIDDEN, false",
			"DELETED, false", "REMOVED, false" })
	@DisplayName("FR-LS-04: open listings are ACTIVE + RESERVED + PENDING_REVIEW")
	void countsActiveReservedAndPendingListingsAsOpen(ListingStatus status, boolean expected) {
		assertThat(status.isOpen()).isEqualTo(expected);
	}

	@ParameterizedTest
	@EnumSource(value = ListingStatus.class, names = { "SOLD", "DELETED", "REMOVED" })
	@DisplayName("FR-LS-06: SOLD, REMOVED and DELETED listings cannot be edited")
	void soldRemovedAndDeletedListingsAreNotEditable(ListingStatus status) {
		assertThat(status.isEditable()).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = ListingStatus.class, names = { "PENDING_REVIEW", "ACTIVE", "RESERVED", "HIDDEN" })
	void otherListingsAreEditable(ListingStatus status) {
		assertThat(status.isEditable()).isTrue();
	}

	@ParameterizedTest
	@CsvSource({ "ACTIVE, true", "RESERVED, true", "SOLD, true", "HIDDEN, false", "PENDING_REVIEW, false",
			"DELETED, false", "REMOVED, false" })
	@DisplayName("FR-DC-02: ACTIVE, RESERVED and SOLD listings are public")
	void onlyActiveReservedAndSoldListingsArePublic(ListingStatus status, boolean expected) {
		assertThat(status.isPubliclyVisible()).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "DELETED, true", "REMOVED, true", "SOLD, false", "HIDDEN, false", "ACTIVE, false" })
	@DisplayName("FR-LS-08: DELETED and REMOVED listings are gone for the public")
	void onlyDeletedAndRemovedListingsAreGone(ListingStatus status, boolean expected) {
		assertThat(status.isGone()).isEqualTo(expected);
	}

	static Stream<Arguments> transitionsNotInTheTable() {
		return Arrays.stream(Event.values())
			.flatMap(event -> EnumSet.complementOf(EnumSet.copyOf(event.sources))
				.stream()
				.map(from -> Arguments.of(from, event)));
	}

	enum Event implements UnaryOperator<ListingStatus> {

		OWNER_HIDE(ListingStatus::afterOwnerHide, ListingStatus.ACTIVE),
		OWNER_UNHIDE(ListingStatus::afterOwnerUnhide, ListingStatus.HIDDEN),
		RESERVE(ListingStatus::afterReservation, ListingStatus.ACTIVE),
		RELEASE(ListingStatus::afterReservationReleased, ListingStatus.RESERVED),
		RESERVED_SALE(ListingStatus::afterReservedSale, ListingStatus.RESERVED),
		DIRECT_SALE(ListingStatus::afterDirectSale, ListingStatus.ACTIVE),
		SALE_DENIED(ListingStatus::afterSaleDenied, ListingStatus.SOLD),
		OWNER_DELETE(ListingStatus::afterOwnerDeletion, ListingStatus.ACTIVE, ListingStatus.HIDDEN,
				ListingStatus.PENDING_REVIEW),
		MODERATOR_REMOVE(ListingStatus::afterModeratorRemoval, ListingStatus.PENDING_REVIEW, ListingStatus.ACTIVE,
				ListingStatus.RESERVED, ListingStatus.SOLD, ListingStatus.HIDDEN),
		AREA_REMOVED(ListingStatus::afterAreaRemoved, ListingStatus.ACTIVE, ListingStatus.HIDDEN),
		SANCTION(ListingStatus::afterSanction, ListingStatus.ACTIVE),
		SANCTION_LIFTED(ListingStatus::afterSanctionLifted, ListingStatus.HIDDEN),
		ACCOUNT_ANONYMISED(ListingStatus::afterAccountAnonymised, ListingStatus.PENDING_REVIEW, ListingStatus.ACTIVE,
				ListingStatus.RESERVED, ListingStatus.SOLD, ListingStatus.HIDDEN);

		private final UnaryOperator<ListingStatus> move;

		private final Set<ListingStatus> sources;

		Event(UnaryOperator<ListingStatus> move, ListingStatus... sources) {
			this.move = move;
			this.sources = Set.of(sources);
		}

		@Override
		public ListingStatus apply(ListingStatus status) {
			return move.apply(status);
		}

	}

}
