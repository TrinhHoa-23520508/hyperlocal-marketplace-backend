package vn.edu.uit.nearby.nearby_backend.identity.application.block;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserBlock;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserBlockRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;

/**
 * Blocking and unblocking other users (FR-ID-14). Both are idempotent so the client can
 * retry without checking first.
 */
@Service
public class BlockService implements UserBlocks {

	private final UserBlockRepository blocks;

	private final UserRepository users;

	private final ImageStorage storage;

	private final Clock clock;

	public BlockService(UserBlockRepository blocks, UserRepository users, ImageStorage storage, Clock clock) {
		this.blocks = blocks;
		this.users = users;
		this.storage = storage;
		this.clock = clock;
	}

	/**
	 * @throws InvalidRequestException {@code CANNOT_BLOCK_SELF}
	 * @throws ResourceNotFoundException {@code USER_NOT_FOUND} for an unknown or deleted
	 * user
	 */
	@Transactional
	public void block(UUID blockerId, UUID blockedId) {
		UserBlock block = UserBlock.of(blockerId, blockedId, clock.instant());
		users.findById(blockedId)
			.filter(user -> !user.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCodes.USER_NOT_FOUND, "User not found."));
		if (!blocks.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
			blocks.save(block);
		}
	}

	@Transactional
	public void unblock(UUID blockerId, UUID blockedId) {
		blocks.deleteBlock(blockerId, blockedId);
	}

	/**
	 * Most recently blocked first.
	 */
	@Transactional(readOnly = true)
	public CursorPage<BlockedUser> listBlocked(UUID blockerId, @Nullable String cursorToken,
			@Nullable Integer requestedLimit) {
		int limit = CursorPage.clampLimit(requestedLimit);
		Limit fetchLimit = Limit.of(limit + 1);
		List<UserBlock> fetched = Cursor.decode(cursorToken)
			.map(cursor -> blocks.findOlderOf(blockerId, cursor.createdAt(), cursor.id(), fetchLimit))
			.orElseGet(() -> blocks.findNewestOf(blockerId, fetchLimit));
		Map<UUID, User> blockedUsers = users.findAllById(fetched.stream().map(UserBlock::getBlockedId).toList())
			.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));
		List<BlockedUser> items = fetched.stream()
			.map(block -> toBlockedUser(block, blockedUsers.get(block.getBlockedId())))
			.toList();
		return CursorPage.of(items, limit, item -> new Cursor(item.blockedAt(), item.userId()));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isBlockedEitherWay(UUID userId, UUID otherUserId) {
		return blocks.existsEitherWay(userId, otherUserId);
	}

	@Override
	@Transactional(readOnly = true)
	public Set<UUID> blockedEitherWay(UUID userId) {
		return Set.copyOf(blocks.findCounterpartsOf(userId));
	}

	private BlockedUser toBlockedUser(UserBlock block, User user) {
		String avatarUrl = user.getAvatarKey() == null ? null : storage.presignDownload(user.getAvatarKey()).url();
		return new BlockedUser(user.getId(), user.getDisplayName(), avatarUrl, block.getCreatedAt());
	}

}
