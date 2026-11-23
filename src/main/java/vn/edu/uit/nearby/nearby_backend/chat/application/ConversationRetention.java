package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ChatImageRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.ConversationOf;
import vn.edu.uit.nearby.nearby_backend.identity.AccountDeleted;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummaries;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummary;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.StorageException;

/**
 * Retention of messages (SRS ch4, "Tin nhắn" and "Ảnh và vị trí trong chat"): a
 * conversation is kept while either account exists, with a deleted author shown
 * anonymously, and is deleted with its messages, locations and image files once both
 * accounts are deleted. Runs when an account is anonymised ({@link AccountDeleted},
 * FR-ID-16, UC-SY-12), and is safe to run twice for the same account.
 */
@Component
class ConversationRetention {

	private static final Logger log = LoggerFactory.getLogger(ConversationRetention.class);

	private final ConversationRepository conversations;

	private final ChatImageRepository chatImages;

	private final UserSummaries users;

	private final ImageStorage storage;

	ConversationRetention(ConversationRepository conversations, ChatImageRepository chatImages, UserSummaries users,
			ImageStorage storage) {
		this.conversations = conversations;
		this.chatImages = chatImages;
		this.users = users;
		this.storage = storage;
	}

	@ApplicationModuleListener
	void onAccountDeleted(AccountDeleted deleted) {
		deleteConversationsOfTwoDeletedAccounts(deleted.userId());
	}

	/**
	 * @return how many conversations were deleted
	 */
	int deleteConversationsOfTwoDeletedAccounts(UUID deletedUserId) {
		List<ConversationOf> mine = conversations.conversationsOf(deletedUserId);
		if (mine.isEmpty()) {
			return 0;
		}
		Set<UUID> counterparts = mine.stream().map(ConversationOf::counterpartId).collect(Collectors.toSet());
		Map<UUID, UserSummary> summaries = users.summariesOf(counterparts);
		List<UUID> bothDeleted = mine.stream()
			.filter(conversation -> summaries.get(conversation.counterpartId()) != null
					&& summaries.get(conversation.counterpartId()).deleted())
			.map(ConversationOf::conversationId)
			.toList();
		if (bothDeleted.isEmpty()) {
			return 0;
		}
		deleteFiles(chatImages.objectKeysOf(bothDeleted));
		conversations.deleteAll(bothDeleted);
		return bothDeleted.size();
	}

	/**
	 * A file that cannot be deleted now is logged; the rows are gone, and the storage
	 * lifecycle removes what is left.
	 */
	private void deleteFiles(List<String> keys) {
		for (String key : keys) {
			try {
				storage.delete(key);
			}
			catch (StorageException ex) {
				log.warn("Chat file {} was not deleted: {}", key, ex.getMessage());
			}
		}
	}

}
