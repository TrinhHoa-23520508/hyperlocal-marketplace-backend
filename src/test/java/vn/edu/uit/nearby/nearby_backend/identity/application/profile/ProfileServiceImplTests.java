package vn.edu.uit.nearby.nearby_backend.identity.application.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures.NOW;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import vn.edu.uit.nearby.nearby_backend.identity.ProfileArea;
import vn.edu.uit.nearby.nearby_backend.identity.ProfileAreas;
import vn.edu.uit.nearby.nearby_backend.identity.application.IdentityParametersFixture;
import vn.edu.uit.nearby.nearby_backend.identity.domain.User;
import vn.edu.uit.nearby.nearby_backend.identity.domain.UserFixtures;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.UserRepository;
import vn.edu.uit.nearby.nearby_backend.shared.error.BusinessException;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.UnprocessableRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage.PresignedUrl;
import vn.edu.uit.nearby.nearby_backend.shared.storage.ImageStorage.StoredObject;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTests {

	private static final long FIVE_MEGABYTES = 5L * 1024 * 1024;

	@Mock
	UserRepository users;

	@Mock
	ImageStorage storage;

	@Mock
	ApplicationEventPublisher events;

	private final User user = UserFixtures.activeUser();

	private ProfileServiceImpl profileService;

	@BeforeEach
	void createService() {
		profileService = serviceWith(List.of());
	}

	@Test
	void profileWithoutAvatarOrAreaHasNeither() {
		givenUserExists();

		UserProfile profile = profileService.getProfile(user.getId());

		assertThat(profile.phone()).isEqualTo(UserFixtures.PHONE);
		assertThat(profile.phoneVerified()).isTrue();
		assertThat(profile.email()).isNull();
		assertThat(profile.avatarUrl()).isNull();
		assertThat(profile.areas()).isEmpty();
		assertThat(profile.emailVerified()).isFalse();
		verifyNoInteractions(storage);
	}

	@Test
	void avatarIsReturnedAsAPresignedDownloadUrl() {
		givenUserExists();
		user.replaceAvatar("avatars/a.jpg", NOW);
		when(storage.presignDownload("avatars/a.jpg")).thenReturn(new PresignedUrl("https://cdn/a.jpg", "GET", NOW));

		assertThat(profileService.getProfile(user.getId()).avatarUrl()).isEqualTo("https://cdn/a.jpg");
	}

	@Test
	void updateTrimsTheName() {
		givenUserExists();

		UserProfile profile = profileService.updateProfile(user.getId(), new UpdateProfileCommand("  Binh Tran "));

		assertThat(profile.displayName()).isEqualTo("Binh Tran");
	}

	@Test
	void ownProfileListsTheAreasWithStatusAndExpiry() {
		givenUserExists();
		ProfileArea home = new ProfileArea(UUID.randomUUID(), "Nhà", null, "EXPIRING", true, NOW.plusSeconds(60));
		profileService = serviceWith(List.of(new FixedProfileAreas(List.of(home), Optional.empty())));

		assertThat(profileService.getProfile(user.getId()).areas()).containsExactly(home);
	}

	@Test
	void secondRenameWithinThirtyDaysIsRejectedAndTheProfileSaysWhenItIsAllowed() {
		givenUserExists();

		UserProfile renamed = profileService.updateProfile(user.getId(), new UpdateProfileCommand("Binh"));

		assertThat(renamed.displayNameChangeAllowedAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
		assertThatThrownBy(() -> profileService.updateProfile(user.getId(), new UpdateProfileCommand("Chi")))
			.isInstanceOf(UnprocessableRequestException.class)
			.extracting(this::codeOf)
			.isEqualTo("NAME_CHANGE_TOO_SOON");
	}

	@Test
	void profileOfANeverRenamedUserCanBeRenamedNow() {
		givenUserExists();

		assertThat(profileService.getProfile(user.getId()).displayNameChangeAllowedAt()).isNull();
	}

	@Test
	void publicProfileShowsNameAvatarBadgeAndJoinDateOnly() {
		givenUserExists();
		user.replaceAvatar("avatars/a.jpg", NOW);
		when(storage.presignDownload("avatars/a.jpg"))
			.thenReturn(new PresignedUrl("https://storage/a.jpg", "GET", NOW.plusSeconds(600)));

		PublicProfile profile = profileService.getPublicProfile(user.getId());

		assertThat(profile).isEqualTo(new PublicProfile(user.getId(), UserFixtures.DISPLAY_NAME,
				"https://storage/a.jpg", true, user.getCreatedAt(), false, null));
	}

	@Test
	void publicProfileShowsTheAreaLabelButNeverACoordinate() {
		givenUserExists();
		profileService = serviceWith(List.of(new FixedProfileAreas(List.of(), Optional.of("Linh Trung"))));

		assertThat(profileService.getPublicProfile(user.getId()).localityName()).isEqualTo("Linh Trung");
	}

	@Test
	void publicProfileOfADeletedAccountIsAnonymous() {
		user.requestDeletion(NOW);
		user.anonymise("unusable", NOW);
		givenUserExists();
		profileService = serviceWith(List.of(new FixedProfileAreas(List.of(), Optional.of("Linh Trung"))));

		PublicProfile profile = profileService.getPublicProfile(user.getId());

		assertThat(profile.displayName()).isEqualTo(User.DELETED_DISPLAY_NAME);
		assertThat(profile.avatarUrl()).isNull();
		assertThat(profile.phoneVerified()).isFalse();
		assertThat(profile.deleted()).isTrue();
		assertThat(profile.localityName()).isNull();
	}

	@Test
	void uploadUrlIsIssuedForAnObjectKeyInTheUsersAvatarFolder() {
		when(storage.presignUpload(anyString(), any()))
			.thenAnswer(call -> new PresignedUrl("https://upload/" + call.getArgument(0), "PUT", NOW));

		AvatarUpload upload = profileService.createAvatarUpload(user.getId(),
				new AvatarUploadCommand("image/webp", 1024));

		assertThat(upload.objectKey()).matches("avatars/" + user.getId() + "/[0-9a-f-]{36}\\.webp");
		assertThat(upload.uploadUrl()).isEqualTo("https://upload/" + upload.objectKey());
		assertThat(upload.method()).isEqualTo("PUT");
		assertThat(upload.requiredHeaders()).isEqualTo(Map.of("Content-Type", "image/webp"));
	}

	@Test
	void jpgUploadIsSignedWithTheStandardJpegType() {
		when(storage.presignUpload(anyString(), any()))
			.thenAnswer(call -> new PresignedUrl("https://upload/" + call.getArgument(0), "PUT", NOW));

		AvatarUpload upload = profileService.createAvatarUpload(user.getId(),
				new AvatarUploadCommand("image/jpg", 1024));

		assertThat(upload.objectKey()).endsWith(".jpg");
		verify(storage).presignUpload(upload.objectKey(), "image/jpeg");
		assertThat(upload.requiredHeaders()).isEqualTo(Map.of("Content-Type", "image/jpeg"));
	}

	@Test
	void uploadUrlIsRefusedForAnInvalidImage() {
		assertThatThrownBy(
				() -> profileService.createAvatarUpload(user.getId(), new AvatarUploadCommand("image/gif", 1024)))
			.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(storage);
	}

	@ParameterizedTest
	@ValueSource(strings = { "avatars/someone-else/0b6c7a3e-8a64-4c4c-9a55-0e1f0c3b9e11.jpg",
			"listings/x/0b6c7a3e-8a64-4c4c-9a55-0e1f0c3b9e11.jpg", "{own}../0b6c7a3e-8a64-4c4c-9a55-0e1f0c3b9e11.jpg",
			"{own}0b6c7a3e-8a64-4c4c-9a55-0e1f0c3b9e11.gif" })
	void keyNotIssuedForTheUsersAvatarIsRejected(String objectKey) {
		String key = objectKey.replace("{own}", "avatars/" + user.getId() + "/");

		assertThatThrownBy(() -> profileService.confirmAvatar(user.getId(), key)).extracting(this::codeOf)
			.isEqualTo("INVALID_AVATAR_KEY");
		verifyNoInteractions(storage);
	}

	@Test
	void confirmingBeforeUploadingIsRejected() {
		String key = ownAvatarKey();
		when(storage.find(key)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> profileService.confirmAvatar(user.getId(), key)).extracting(this::codeOf)
			.isEqualTo("AVATAR_NOT_UPLOADED");
	}

	@Test
	void uploadedImageOverTheLimitIsDeletedAndRejected() {
		String key = ownAvatarKey();
		when(storage.find(key)).thenReturn(Optional.of(new StoredObject(FIVE_MEGABYTES + 1, "image/jpeg")));

		assertThatThrownBy(() -> profileService.confirmAvatar(user.getId(), key)).extracting(this::codeOf)
			.isEqualTo("INVALID_IMAGE");
		verify(storage).delete(key);
		verify(users, never()).getRequired(any());
	}

	@Test
	void confirmedAvatarReplacesThePreviousOneWhichIsDiscardedAfterCommit() {
		givenUserExists();
		user.replaceAvatar("avatars/old.jpg", NOW);
		String key = ownAvatarKey();
		when(storage.find(key)).thenReturn(Optional.of(new StoredObject(FIVE_MEGABYTES, "image/jpeg")));
		when(storage.presignDownload(key)).thenReturn(new PresignedUrl("https://cdn/new.jpg", "GET", NOW));

		UserProfile profile = profileService.confirmAvatar(user.getId(), key);

		assertThat(profile.avatarUrl()).isEqualTo("https://cdn/new.jpg");
		verify(events).publishEvent(new AvatarDiscarded("avatars/old.jpg"));
		verify(storage, never()).delete(anyString());
	}

	@Test
	void removingTheAvatarDiscardsIt() {
		givenUserExists();
		user.replaceAvatar("avatars/a.jpg", NOW);

		profileService.removeAvatar(user.getId());

		assertThat(user.getAvatarKey()).isNull();
		verify(events).publishEvent(new AvatarDiscarded("avatars/a.jpg"));
	}

	@Test
	void removingAMissingAvatarPublishesNothing() {
		givenUserExists();

		profileService.removeAvatar(user.getId());

		verifyNoInteractions(events);
	}

	@Test
	void discardedAvatarIsDeletedFromStorage() {
		new AvatarCleanupListener(storage).deleteDiscardedAvatar(new AvatarDiscarded("avatars/a.jpg"));

		verify(storage).delete("avatars/a.jpg");
	}

	private ProfileServiceImpl serviceWith(List<ProfileAreas> profileAreas) {
		return new ProfileServiceImpl(users, storage, IdentityParametersFixture.seeded(), events,
				Clock.fixed(NOW, ZoneOffset.UTC), profileAreas);
	}

	private record FixedProfileAreas(List<ProfileArea> areas, Optional<String> locality) implements ProfileAreas {

		@Override
		public List<ProfileArea> areasOf(UUID userId) {
			return areas;
		}

		@Override
		public Optional<String> publicLocalityOf(UUID userId) {
			return locality;
		}

	}

	private void givenUserExists() {
		when(users.getRequired(user.getId())).thenReturn(user);
	}

	private String ownAvatarKey() {
		return "avatars/" + user.getId() + "/" + UUID.randomUUID() + ".jpg";
	}

	private String codeOf(Throwable ex) {
		return ((BusinessException) ex).getCode();
	}

}
