package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.Opened;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.listing.ListingCounters;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ConflictException;
import vn.edu.uit.nearby.nearby_backend.shared.error.OperationNotAllowedException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

	private static final UUID BUYER = UUID.randomUUID();

	private static final UUID SELLER = UUID.randomUUID();

	private static final UUID LISTING = UUID.randomUUID();

	private static final UUID AREA = UUID.randomUUID();

	private static final GeoPoint POINT = new GeoPoint(10.77, 106.70);

	@Mock
	ConversationRepository conversations;

	@Mock
	ListingLookup listings;

	@Mock
	ListingCounters listingCounters;

	@Mock
	TradingGate tradingGate;

	@Mock
	InteractionRange interactionRange;

	@Mock
	UserBlocks blocks;

	@Test
	@DisplayName("FR-CH-01: opening a second time returns the existing conversation without any check")
	void anExistingConversationIsReturnedAsIs() {
		Conversation existing = new Conversation(UUID.randomUUID(), LISTING, BUYER, SELLER);
		when(conversations.findByListingAndBuyer(LISTING, BUYER)).thenReturn(Optional.of(existing));

		Opened opened = service().open(BUYER, LISTING);

		assertThat(opened.created()).isFalse();
		assertThat(opened.conversation()).isEqualTo(existing);
		verifyNoInteractions(tradingGate, interactionRange, blocks, listingCounters, listings);
	}

	@Test
	@DisplayName("FR-CH-01 / LS-W6: a new conversation is created and counted on the listing")
	void aNewConversationIsCreatedAndCounted() {
		givenListing("ACTIVE", SELLER, true);
		Conversation created = new Conversation(UUID.randomUUID(), LISTING, BUYER, SELLER);
		when(conversations.insertIfAbsent(LISTING, BUYER, SELLER, NOW)).thenReturn(new Opened(created, true));

		Opened opened = service().open(BUYER, LISTING);

		assertThat(opened.created()).isTrue();
		verify(listingCounters).incrementChatCount(LISTING);
	}

	@Test
	@DisplayName("FR-CH-01: losing a race on the unique key returns the winner and counts nothing")
	void aLostRaceIsNotCountedTwice() {
		givenListing("RESERVED", SELLER, true);
		Conversation winner = new Conversation(UUID.randomUUID(), LISTING, BUYER, SELLER);
		when(conversations.insertIfAbsent(LISTING, BUYER, SELLER, NOW)).thenReturn(new Opened(winner, false));

		Opened opened = service().open(BUYER, LISTING);

		assertThat(opened.created()).isFalse();
		verify(listingCounters, never()).incrementChatCount(any());
	}

	@Test
	@DisplayName("UC-CH-01: the checks run in order: gate, range, block, then own listing and state")
	void checksRunInTheDocumentedOrder() {
		givenListing("ACTIVE", SELLER, true);
		when(conversations.insertIfAbsent(any(), any(), any(), any()))
			.thenReturn(new Opened(new Conversation(UUID.randomUUID(), LISTING, BUYER, SELLER), true));

		service().open(BUYER, LISTING);

		InOrder order = inOrder(tradingGate, interactionRange, blocks);
		order.verify(tradingGate).requireOpen(BUYER);
		order.verify(interactionRange).requireWithinRange(BUYER, POINT);
		order.verify(blocks).isBlockedEitherWay(BUYER, SELLER);
	}

	@Test
	@DisplayName("FR-CH-01 E: a failed trading gate is 403 TRADING_GATE_FAILED and nothing is created")
	void aFailedGateStopsEverything() {
		when(conversations.findByListingAndBuyer(LISTING, BUYER)).thenReturn(Optional.empty());
		when(listings.find(LISTING)).thenReturn(Optional.of(snapshot("ACTIVE", SELLER)));
		doThrow(new OperationNotAllowedException(ErrorCodes.TRADING_GATE_FAILED, "gate")).when(tradingGate)
			.requireOpen(BUYER);

		assertThat(codeOfOpening()).isEqualTo(ErrorCodes.TRADING_GATE_FAILED);
		verify(conversations, never()).insertIfAbsent(any(), any(), any(), any());
	}

	@Test
	void aListingOutsideTheBuyersAreasIsOutOfRange() {
		when(conversations.findByListingAndBuyer(LISTING, BUYER)).thenReturn(Optional.empty());
		when(listings.find(LISTING)).thenReturn(Optional.of(snapshot("ACTIVE", SELLER)));
		doThrow(new OperationNotAllowedException(ErrorCodes.OUT_OF_RANGE, "range")).when(interactionRange)
			.requireWithinRange(BUYER, POINT);

		assertThat(codeOfOpening()).isEqualTo(ErrorCodes.OUT_OF_RANGE);
	}

	@Test
	@DisplayName("FR-ID-14: a block either way is CONTACT_NOT_ALLOWED, in neutral words")
	void aBlockIsNeutral() {
		givenListing("ACTIVE", SELLER, true);
		when(blocks.isBlockedEitherWay(BUYER, SELLER)).thenReturn(true);

		assertThatThrownBy(() -> service().open(BUYER, LISTING)).isInstanceOf(OperationNotAllowedException.class)
			.satisfies(error -> {
				assertThat(((BusinessException) error).getCode()).isEqualTo(ErrorCodes.CONTACT_NOT_ALLOWED);
				assertThat(error.getMessage()).doesNotContainIgnoringCase("block");
			});
	}

	@Test
	@DisplayName("FR-CH-01: your own listing is 409 INVALID_LISTING_STATE")
	void yourOwnListingIsRefused() {
		givenListing("ACTIVE", BUYER, true);

		assertThatThrownBy(() -> service().open(BUYER, LISTING)).isInstanceOf(ConflictException.class)
			.extracting(error -> ((BusinessException) error).getCode())
			.isEqualTo(ErrorCodes.INVALID_LISTING_STATE);
	}

	@Test
	@DisplayName("FR-CH-01: a SOLD listing takes no new conversation (409)")
	void aSoldListingIsRefused() {
		givenListing("SOLD", SELLER, true);

		assertThatThrownBy(() -> service().open(BUYER, LISTING)).isInstanceOf(ConflictException.class);
	}

	@Test
	@DisplayName("FR-AR-03: a listing of an expired area takes no new conversation (409)")
	void anExpiredAreaIsRefused() {
		givenListing("ACTIVE", SELLER, false);

		assertThatThrownBy(() -> service().open(BUYER, LISTING)).isInstanceOf(ConflictException.class);
	}

	@Test
	@DisplayName("a DELETED or REMOVED listing without a conversation does not exist for the buyer (404)")
	void aGoneListingIsNotFound() {
		when(conversations.findByListingAndBuyer(LISTING, BUYER)).thenReturn(Optional.empty());
		when(listings.find(LISTING)).thenReturn(Optional.of(snapshot("DELETED", SELLER)));

		assertThatThrownBy(() -> service().open(BUYER, LISTING)).isInstanceOf(ResourceNotFoundException.class);

		when(listings.find(LISTING)).thenReturn(Optional.of(snapshot("REMOVED", SELLER)));
		assertThatThrownBy(() -> service().open(BUYER, LISTING)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void anUnknownListingIsNotFound() {
		when(conversations.findByListingAndBuyer(LISTING, BUYER)).thenReturn(Optional.empty());
		when(listings.find(LISTING)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().open(BUYER, LISTING)).isInstanceOf(ResourceNotFoundException.class)
			.extracting(error -> ((BusinessException) error).getCode())
			.isEqualTo(ErrorCodes.LISTING_NOT_FOUND);
	}

	private void givenListing(String status, UUID seller, boolean areaAllowsNewContacts) {
		when(conversations.findByListingAndBuyer(LISTING, BUYER)).thenReturn(Optional.empty());
		when(listings.find(LISTING)).thenReturn(Optional.of(snapshot(status, seller)));
		// A listing that is not ACTIVE or RESERVED never reaches the area check.
		lenient().when(interactionRange.allowsNewContacts(AREA)).thenReturn(areaAllowsNewContacts);
	}

	private static ListingSnapshot snapshot(String status, UUID seller) {
		return new ListingSnapshot(LISTING, seller, AREA, "Ghế gỗ", status, 150_000, true, POINT, null, null);
	}

	private String codeOfOpening() {
		return catchThrowableOfType(BusinessException.class, () -> service().open(BUYER, LISTING)).getCode();
	}

	private ConversationService service() {
		return new ConversationService(conversations, listings, listingCounters, tradingGate, interactionRange, blocks,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

}
