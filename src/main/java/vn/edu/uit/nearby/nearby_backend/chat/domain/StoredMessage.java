package vn.edu.uit.nearby.nearby_backend.chat.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A message as saved ({@code messages} row). {@code id} only grows, so it is the sync
 * checkpoint (FR-CH-07).
 *
 * @param senderId null only for SYSTEM messages
 * @param payload a list for IMAGE, a map for LOCATION and SYSTEM, null for TEXT
 * @param clientMsgId null only for SYSTEM messages
 */
public record StoredMessage(long id, UUID conversationId, @Nullable UUID senderId, MessageType type,
		@Nullable String body, @Nullable Object payload, @Nullable UUID clientMsgId, Instant createdAt) {

}
