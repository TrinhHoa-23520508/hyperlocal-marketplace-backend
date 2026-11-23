package vn.edu.uit.nearby.nearby_backend.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vn.edu.uit.nearby.nearby_backend.chat.domain.ConversationAccess.OpenRefusal;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ConversationAccess.SendLock;

class ConversationAccessTests {

	private static final UUID BUYER = UUID.randomUUID();

	private static final UUID SELLER = UUID.randomUUID();

	@Nested
	@DisplayName("FR-CH-01: opening a new conversation")
	class Opening {

		@Test
		void aBuyerMayOpenAConversationOnAnAvailableListing() {
			assertThat(ConversationAccess.checkOpen(BUYER, SELLER, true, false)).isEmpty();
		}

		@Test
		@DisplayName("a block either way refuses with CONTACT_NOT_ALLOWED, before anything else")
		void aBlockComesFirst() {
			assertThat(ConversationAccess.checkOpen(BUYER, BUYER, false, true)).contains(OpenRefusal.CONTACT_BLOCKED);
		}

		@Test
		void yourOwnListingIsRefused() {
			assertThat(ConversationAccess.checkOpen(SELLER, SELLER, true, false)).contains(OpenRefusal.OWN_LISTING);
		}

		@Test
		@DisplayName("a listing that is not ACTIVE or RESERVED is refused with 409")
		void anUnavailableListingIsRefused() {
			assertThat(ConversationAccess.checkOpen(BUYER, SELLER, false, false))
				.contains(OpenRefusal.LISTING_NOT_AVAILABLE);
		}

	}

	@Nested
	@DisplayName("FR-CH-11: who may still send")
	class Sending {

		@ParameterizedTest(name = "listing {0}: members can still write")
		@ValueSource(strings = { "ACTIVE", "RESERVED", "SOLD", "DELETED", "HIDDEN", "EXPIRED" })
		@DisplayName("after SOLD both sides can still write; DELETED also stays open (reading R1)")
		void everyStatusButRemovedStaysOpen(String status) {
			assertThat(ConversationAccess.checkSend(status, false, true)).isEmpty();
		}

		@Test
		@DisplayName("after REMOVED nobody can write")
		void removedLocksSending() {
			assertThat(ConversationAccess.checkSend("REMOVED", false, true)).contains(SendLock.LISTING_REMOVED);
		}

		@Test
		void aBlockEitherWayLocksSending() {
			assertThat(ConversationAccess.checkSend("ACTIVE", true, true)).contains(SendLock.BLOCKED);
		}

		@Test
		@DisplayName("a suspended, banned or deleted member locks sending (reading R2)")
		void aRestrictedMemberLocksSending() {
			assertThat(ConversationAccess.checkSend("ACTIVE", false, false)).contains(SendLock.ACCOUNT_RESTRICTED);
		}

	}

	@Nested
	class Members {

		private final Conversation conversation = new Conversation(UUID.randomUUID(), UUID.randomUUID(), BUYER, SELLER);

		@Test
		void membersHaveARoleAndACounterpart() {
			assertThat(conversation.roleOf(BUYER)).contains(ParticipantRole.BUYER);
			assertThat(conversation.roleOf(SELLER)).contains(ParticipantRole.SELLER);
			assertThat(conversation.counterpartOf(BUYER)).isEqualTo(SELLER);
			assertThat(conversation.counterpartOf(SELLER)).isEqualTo(BUYER);
		}

		@Test
		@DisplayName("FR-CH-02 E4: a stranger is not a member")
		void aStrangerIsNotAMember() {
			UUID stranger = UUID.randomUUID();

			assertThat(conversation.isMember(stranger)).isFalse();
			assertThat(conversation.roleOf(stranger)).isEmpty();
			assertThatThrownBy(() -> conversation.counterpartOf(stranger)).isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void sellerAndBuyerMustDiffer() {
			assertThatThrownBy(() -> new Conversation(UUID.randomUUID(), UUID.randomUUID(), BUYER, BUYER))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

}
