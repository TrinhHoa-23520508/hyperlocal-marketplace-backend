package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * UC-CH-02 step 5: the sender's confirmation of a message sent over STOMP, on
 * {@code /user/queue/acks}, with the ids the server assigned. A repeated
 * {@code clientMsgId} is acknowledged with the id of the message stored the first time.
 */
public record MessageAck(@Nullable UUID clientMsgId, long id, Instant createdAt) {

}
