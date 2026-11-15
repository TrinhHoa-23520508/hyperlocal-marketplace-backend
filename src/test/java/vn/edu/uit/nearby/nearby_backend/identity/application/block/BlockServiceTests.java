package vn.edu.uit.nearby.nearby_backend.identity.application.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserBlock;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserBlockRepository;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;

@ExtendWith(MockitoExtension.class)
class BlockServiceTests {

	private static final UUID BLOCKER_ID = UUID.randomUUID();

	@Mock
	UserBlockRepository blocks;

	@Mock
	UserRepository users;

	@Mock
	ImageStorage storage;

	@Test
	void blockingAnotherUserStoresTheBlock() {
		User blocked = givenUser(UserFixtures.activeUser());
		when(blocks.existsByBlockerIdAndBlockedId(BLOCKER_ID, blocked.getId())).thenReturn(false);

		service().block(BLOCKER_ID, blocked.getId());

		ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
		verify(blocks).save(saved.capture());
		assertThat(saved.getValue().getBlockerId()).isEqualTo(BLOCKER_ID);
		assertThat(saved.getValue().getBlockedId()).isEqualTo(blocked.getId());
		assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void blockingTwiceKeepsTheFirstBlock() {
		User blocked = givenUser(UserFixtures.activeUser());
		when(blocks.existsByBlockerIdAndBlockedId(BLOCKER_ID, blocked.getId())).thenReturn(true);

		service().block(BLOCKER_ID, blocked.getId());

		verify(blocks, never()).save(any());
	}

	@Test
	void blockingYourselfIsRejectedBeforeAnyLookup() {
		assertThatThrownBy(() -> service().block(BLOCKER_ID, BLOCKER_ID)).isInstanceOf(InvalidRequestException.class)
			.extracting(ex -> ((BusinessException) ex).getCode())
			.isEqualTo("CANNOT_BLOCK_SELF");
		verifyNoInteractions(users, blocks);
	}

	@Test
	void unknownOrDeletedUserCannotBeBlocked() {
		UUID unknownId = UUID.randomUUID();
		when(users.findById(unknownId)).thenReturn(Optional.empty());
		User deleted = UserFixtures.activeUser();
		deleted.requestDeletion(NOW);
		deleted.anonymise("unusable", NOW);
		givenUser(deleted);

		assertThatThrownBy(() -> service().block(BLOCKER_ID, unknownId)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service().block(BLOCKER_ID, deleted.getId()))
			.isInstanceOf(ResourceNotFoundException.class);
		verify(blocks, never()).save(any());
	}

	@Test
	void unblockingRemovesTheBlockIfThereIsOne() {
		UUID blockedId = UUID.randomUUID();

		service().unblock(BLOCKER_ID, blockedId);

		verify(blocks).deleteBlock(BLOCKER_ID, blockedId);
	}

	@Test
	void blockedUsersAreListedNewestFirstWithTheirPublicDetails() {
		User first = UserFixtures.activeUser("0911111111");
		User second = UserFixtures.activeUser("0922222222");
		second.replaceAvatar("avatars/b.jpg", NOW);
		when(blocks.findNewestOf(BLOCKER_ID, Limit.of(21))).thenReturn(
				List.of(UserBlock.of(BLOCKER_ID, second.getId(), NOW), UserBlock.of(BLOCKER_ID, first.getId(), NOW)));
		when(users.findAllById(List.of(second.getId(), first.getId()))).thenReturn(List.of(first, second));
		when(storage.presignDownload("avatars/b.jpg"))
			.thenReturn(new ImageStorage.PresignedUrl("https://storage/b.jpg", "GET", NOW));

		CursorPage<BlockedUser> page = service().listBlocked(BLOCKER_ID, null, null);

		assertThat(page.items()).containsExactly(
				new BlockedUser(second.getId(), UserFixtures.DISPLAY_NAME, "https://storage/b.jpg", NOW),
				new BlockedUser(first.getId(), UserFixtures.DISPLAY_NAME, null, NOW));
		assertThat(page.hasMore()).isFalse();
	}

	@Test
	void nextPageStartsAfterTheCursor() {
		UUID lastSeenId = UUID.randomUUID();
		String cursor = CursorPage.of(List.of("a", "b"), 1, item -> new CursorPage.Cursor(NOW, lastSeenId))
			.nextCursor();
		when(blocks.findOlderOf(BLOCKER_ID, NOW, lastSeenId, Limit.of(2))).thenReturn(List.of());
		when(users.findAllById(List.of())).thenReturn(List.of());

		assertThat(service().listBlocked(BLOCKER_ID, cursor, 1).items()).isEmpty();
	}

	@Test
	void blocksCountInBothDirections() {
		UUID otherId = UUID.randomUUID();
		when(blocks.existsEitherWay(BLOCKER_ID, otherId)).thenReturn(true);
		when(blocks.findCounterpartsOf(BLOCKER_ID)).thenReturn(Set.of(otherId));

		assertThat(service().isBlockedEitherWay(BLOCKER_ID, otherId)).isTrue();
		assertThat(service().blockedEitherWay(BLOCKER_ID)).containsExactly(otherId);
	}

	private User givenUser(User user) {
		when(users.findById(user.getId())).thenReturn(Optional.of(user));
		return user;
	}

	private BlockService service() {
		return new BlockService(blocks, users, storage, Clock.fixed(NOW, ZoneOffset.UTC));
	}

}
