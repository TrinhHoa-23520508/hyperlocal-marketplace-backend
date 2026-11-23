package vn.edu.uit.nearby.nearby_backend.chat.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * The one conversation of a (listing, buyer) pair (FR-CH-01). The seller is the listing's
 * owner when the conversation was created; a later change of owner does not move it.
 */
public record Conversation(UUID id, UUID listingId, UUID buyerId, UUID sellerId) {

	public Conversation {
		if (buyerId.equals(sellerId)) {
			throw new IllegalArgumentException("A conversation needs two different users.");
		}
	}

	public boolean isMember(UUID userId) {
		return roleOf(userId).isPresent();
	}

	public Optional<ParticipantRole> roleOf(UUID userId) {
		if (buyerId.equals(userId)) {
			return Optional.of(ParticipantRole.BUYER);
		}
		return sellerId.equals(userId) ? Optional.of(ParticipantRole.SELLER) : Optional.empty();
	}

	/**
	 * @throws IllegalArgumentException when {@code userId} is not a member
	 */
	public UUID counterpartOf(UUID userId) {
		return switch (roleOf(userId).orElseThrow(() -> new IllegalArgumentException("Not a member: " + userId))) {
			case BUYER -> sellerId;
			case SELLER -> buyerId;
		};
	}

}
