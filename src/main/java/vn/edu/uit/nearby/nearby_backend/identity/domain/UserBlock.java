package vn.edu.uit.nearby.nearby_backend.identity.domain;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;

/**
 * One user blocking another (FR-ID-14). The blocked user is never told.
 */
@Getter
@Entity
@Table(name = "user_blocks")
@IdClass(UserBlock.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

	@Id
	@Column(name = "blocker_id")
	private UUID blockerId;

	@Id
	@Column(name = "blocked_id")
	private UUID blockedId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	private UserBlock(UUID blockerId, UUID blockedId, Instant now) {
		this.blockerId = blockerId;
		this.blockedId = blockedId;
		this.createdAt = now.truncatedTo(ChronoUnit.MILLIS);
	}

	/**
	 * @throws InvalidRequestException {@code CANNOT_BLOCK_SELF}
	 */
	public static UserBlock of(UUID blockerId, UUID blockedId, Instant now) {
		if (blockerId.equals(blockedId)) {
			throw new InvalidRequestException(ErrorCodes.CANNOT_BLOCK_SELF, "You cannot block yourself.");
		}
		return new UserBlock(blockerId, blockedId, now);
	}

	public record Key(UUID blockerId, UUID blockedId) implements Serializable {
	}

}
