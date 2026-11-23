package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.ConversationOf;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummaries;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummary;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.StorageException;

/**
 * SRS ch4, "Tin nh\u1eafn" and "\u1ea2nh v\u00e0 v\u1ecb tr\u00ed trong chat": kept while
 * one account exists, deleted when both are deleted.
 */
@ExtendWith(MockitoExtension.class)
class ConversationRetentionTests {

	private final UUID deleted = UUID.randomUUID();

	private final UUID stillHere = UUID.randomUUID();

	private final UUID alsoDeleted = UUID.randomUUID();

	private final UUID withLiveUser = UUID.randomUUID();

	private final UUID withDeletedUser = UUID.randomUUID();

	@Mock
	ConversationRepository conversations;

	@Mock
	ChatImageRepository chatImages;

	@Mock
	UserSummaries users;

	@Mock
	ImageStorage storage;

	private ConversationRetention retention() {
		return new ConversationRetention(conversations, chatImages, users, storage);
	}

	private static UserSummary summary(UUID id, boolean deleted) {
		return new UserSummary(id, deleted ? "Ng\u01b0\u1eddi d\u00f9ng \u0111\u00e3 xo\u00e1" : "An", null, deleted);
	}

	@Test
	@DisplayName("ch4: a conversation whose other account still exists is kept, with the deleted author anonymous")
	void aConversationWithALiveAccountIsKept() {
		when(conversations.conversationsOf(deleted)).thenReturn(List.of(new ConversationOf(withLiveUser, stillHere)));
		when(users.summariesOf(Set.of(stillHere))).thenReturn(Map.of(stillHere, summary(stillHere, false)));

		assertThat(retention().deleteConversationsOfTwoDeletedAccounts(deleted)).isZero();

		verify(conversations, never()).deleteAll(any());
		verify(storage, never()).delete(any());
	}

	@Test
	@DisplayName("ch4: when both accounts are deleted the conversation, its messages and its files are deleted")
	void aConversationOfTwoDeletedAccountsIsDeleted() {
		when(conversations.conversationsOf(deleted)).thenReturn(
				List.of(new ConversationOf(withLiveUser, stillHere), new ConversationOf(withDeletedUser, alsoDeleted)));
		when(users.summariesOf(Set.of(stillHere, alsoDeleted)))
			.thenReturn(Map.of(stillHere, summary(stillHere, false), alsoDeleted, summary(alsoDeleted, true)));
		when(chatImages.objectKeysOf(List.of(withDeletedUser)))
			.thenReturn(List.of("chat-images/a_1080.webp", "chat-incoming/x.jpg"));

		assertThat(retention().deleteConversationsOfTwoDeletedAccounts(deleted)).isEqualTo(1);

		verify(storage).delete("chat-images/a_1080.webp");
		verify(storage).delete("chat-incoming/x.jpg");
		verify(conversations).deleteAll(List.of(withDeletedUser));
	}

	@Test
	void anAccountWithoutConversationsChangesNothing() {
		when(conversations.conversationsOf(deleted)).thenReturn(List.of());

		assertThat(retention().deleteConversationsOfTwoDeletedAccounts(deleted)).isZero();

		verify(users, never()).summariesOf(any());
	}

	@Test
	void anUnknownCounterpartIsTreatedAsStillExisting() {
		when(conversations.conversationsOf(deleted)).thenReturn(List.of(new ConversationOf(withLiveUser, stillHere)));
		when(users.summariesOf(Set.of(stillHere))).thenReturn(Map.of());

		assertThat(retention().deleteConversationsOfTwoDeletedAccounts(deleted)).isZero();
	}

	@Test
	@DisplayName("a file that cannot be deleted does not stop the deletion of the rows")
	void aStorageFailureIsLoggedNotThrown() {
		when(conversations.conversationsOf(deleted))
			.thenReturn(List.of(new ConversationOf(withDeletedUser, alsoDeleted)));
		when(users.summariesOf(Set.of(alsoDeleted))).thenReturn(Map.of(alsoDeleted, summary(alsoDeleted, true)));
		when(chatImages.objectKeysOf(List.of(withDeletedUser))).thenReturn(List.of("chat-images/a_1080.webp"));
		org.mockito.Mockito.doThrow(new StorageException("down", null)).when(storage).delete("chat-images/a_1080.webp");

		assertThat(retention().deleteConversationsOfTwoDeletedAccounts(deleted)).isEqualTo(1);

		verify(conversations).deleteAll(List.of(withDeletedUser));
	}

}
