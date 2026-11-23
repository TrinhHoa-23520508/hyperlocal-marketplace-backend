package vn.edu.uit.nearby.nearby_backend.identity.application.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.identity.UserSummary;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage.PresignedUrl;

@ExtendWith(MockitoExtension.class)
class UserSummaryServiceTests {

	@Mock
	UserRepository users;

	@Mock
	ImageStorage storage;

	@Test
	@DisplayName("A17: one query summarises live and deleted users; unknown ids are absent")
	void summarisesLiveAndDeletedUsers() {
		User live = UserFixtures.activeUser();
		User withAvatar = UserFixtures.activeUser("+84912345678");
		withAvatar.replaceAvatar("avatars/a.webp", NOW);
		User deleted = UserFixtures.activeUser("+84912345679");
		deleted.requestDeletion(NOW);
		deleted.anonymise("{noop}x", NOW);
		UUID unknown = UUID.randomUUID();
		List<UUID> ids = List.of(live.getId(), withAvatar.getId(), deleted.getId(), unknown);
		when(users.findAllById(ids)).thenReturn(List.of(live, withAvatar, deleted));
		when(storage.presignDownload("avatars/a.webp"))
			.thenReturn(new PresignedUrl("https://files/a", "GET", Instant.parse("2026-10-18T03:10:00Z")));

		Map<UUID, UserSummary> summaries = new UserSummaryService(users, storage).summariesOf(ids);

		assertThat(summaries).containsOnlyKeys(live.getId(), withAvatar.getId(), deleted.getId());
		assertThat(summaries.get(live.getId()))
			.isEqualTo(new UserSummary(live.getId(), UserFixtures.DISPLAY_NAME, null, false));
		assertThat(summaries.get(withAvatar.getId()).avatarUrl()).isEqualTo("https://files/a");
		assertThat(summaries.get(deleted.getId()))
			.isEqualTo(new UserSummary(deleted.getId(), User.DELETED_DISPLAY_NAME, null, true));
	}

	@Test
	void asksNothingForNoIds() {
		assertThat(new UserSummaryService(users, storage).summariesOf(List.of())).isEmpty();

		verifyNoInteractions(users, storage);
	}

}
