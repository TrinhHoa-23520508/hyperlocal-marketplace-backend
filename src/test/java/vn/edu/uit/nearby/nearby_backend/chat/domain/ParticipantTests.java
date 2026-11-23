package vn.edu.uit.nearby.nearby_backend.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParticipantTests {

	private final Participant buyer = Participant.joined(UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.BUYER);

	@Test
	void aNewMemberHasReadNothingAndHasNothingUnread() {
		assertThat(buyer.lastReadMessageId()).isZero();
		assertThat(buyer.unreadCount()).isZero();
		assertThat(buyer.hasUnread()).isFalse();
	}

	@Test
	void eachMessageFromTheCounterpartAddsOneUnread() {
		Participant after = buyer.receivedMessage().receivedMessage();

		assertThat(after.unreadCount()).isEqualTo(2);
		assertThat(after.hasUnread()).isTrue();
		assertThat(after.lastReadMessageId()).isZero();
	}

	@Test
	@DisplayName("FR-CH-06: reading up to the newest message sets the count to what is left")
	void readingMovesTheMarkerAndSetsTheCount() {
		Participant unread = buyer.receivedMessage().receivedMessage().receivedMessage();

		Participant read = unread.readUpTo(42, 0);

		assertThat(read.lastReadMessageId()).isEqualTo(42);
		assertThat(read.unreadCount()).isZero();
	}

	@Test
	@DisplayName("FR-CH-06: a marker that goes backwards is ignored")
	void aBackwardsMarkerIsIgnored() {
		Participant read = buyer.readUpTo(50, 0).receivedMessage();

		assertThat(read.readUpTo(40, 7)).isSameAs(read);
		assertThat(read.readUpTo(50, 7)).isSameAs(read);
	}

	@Test
	void aMarkerInTheMiddleKeepsTheMessagesAfterItUnread() {
		Participant read = buyer.receivedMessage().receivedMessage().readUpTo(10, 1);

		assertThat(read.lastReadMessageId()).isEqualTo(10);
		assertThat(read.unreadCount()).isEqualTo(1);
	}

	@Test
	void negativeStateIsRefused() {
		assertThatThrownBy(() -> new Participant(UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.BUYER, -1, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Participant(UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.BUYER, 0, -1))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
