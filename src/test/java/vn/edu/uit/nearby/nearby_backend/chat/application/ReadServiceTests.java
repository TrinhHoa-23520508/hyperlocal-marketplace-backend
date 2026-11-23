package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import vn.edu.uit.nearby.nearby_backend.chat.application.ReadService.ReadRecorded;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Participant;
import vn.edu.uit.nearby.nearby_backend.chat.domain.ParticipantRole;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class ReadServiceTests {

	private static final UUID BUYER = UUID.randomUUID();

	private static final UUID SELLER = UUID.randomUUID();

	private final Conversation conversation = new Conversation(UUID.randomUUID(), UUID.randomUUID(), BUYER, SELLER);

	@Mock
	ConversationRepository conversations;

	@Mock
	MessageRepository messages;

	@Mock
	ApplicationEventPublisher events;

	private ReadService service() {
		return new ReadService(conversations, messages, events);
	}

	private Participant state(long marker, int unread) {
		return new Participant(conversation.id(), BUYER, ParticipantRole.BUYER, marker, unread);
	}

	@Test
	@DisplayName("FR-CH-06: a forward marker is stored and the counterpart gets a read receipt event")
	void aForwardMarkerMoves() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(messages.newestIdOf(conversation.id())).thenReturn(50L);
		when(conversations.markRead(conversation.id(), BUYER, 40)).thenReturn(Optional.of(state(40, 3)));

		Participant result = service().markRead(BUYER, conversation.id(), 40);

		assertThat(result.lastReadMessageId()).isEqualTo(40);
		ArgumentCaptor<ReadRecorded> event = ArgumentCaptor.forClass(ReadRecorded.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().readerId()).isEqualTo(BUYER);
		assertThat(event.getValue().lastReadMessageId()).isEqualTo(40);
	}

	@Test
	@DisplayName("FR-CH-06: a marker beyond the newest message counts as the newest")
	void aMarkerBeyondTheNewestIsClamped() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(messages.newestIdOf(conversation.id())).thenReturn(50L);
		when(conversations.markRead(conversation.id(), BUYER, 50)).thenReturn(Optional.of(state(50, 0)));

		assertThat(service().markRead(BUYER, conversation.id(), 9_999).lastReadMessageId()).isEqualTo(50);
	}

	@Test
	@DisplayName("FR-CH-06: a marker that goes backwards is ignored: no event, the stored state is returned")
	void aBackwardsMarkerIsIgnored() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));
		when(messages.newestIdOf(conversation.id())).thenReturn(50L);
		when(conversations.markRead(conversation.id(), BUYER, 10)).thenReturn(Optional.empty());
		when(conversations.findParticipant(conversation.id(), BUYER)).thenReturn(Optional.of(state(40, 3)));

		Participant result = service().markRead(BUYER, conversation.id(), 10);

		assertThat(result.lastReadMessageId()).isEqualTo(40);
		verify(events, never()).publishEvent(any());
	}

	@Test
	void aStrangerGetsNotFound() {
		when(conversations.findById(conversation.id())).thenReturn(Optional.of(conversation));

		BusinessException error = catchThrowableOfType(BusinessException.class,
				() -> service().markRead(UUID.randomUUID(), conversation.id(), 1));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.CONVERSATION_NOT_FOUND);
	}

	@Test
	void aNegativeMarkerIsInvalid() {
		BusinessException error = catchThrowableOfType(BusinessException.class,
				() -> service().markRead(BUYER, conversation.id(), -1));

		assertThat(error.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
	}

}
